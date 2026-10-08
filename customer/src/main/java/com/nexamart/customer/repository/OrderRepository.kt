package com.nexamart.customer.repository

import com.nexamart.customer.core.ApiException
import com.nexamart.customer.data.local.CustomerPrefs
import com.nexamart.customer.data.network.ApiClient
import com.nexamart.customer.model.Address
import com.nexamart.customer.model.CartItem
import com.nexamart.customer.model.CustomerOrder
import com.nexamart.customer.model.PaymentOrder
import com.nexamart.customer.util.Json
import com.nexamart.customer.util.asJsonMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class OrdersState(
    val orders: List<CustomerOrder> = emptyList(),
    val initialized: Boolean = false,
    val refreshing: Boolean = false,
    /** Set when the latest refresh failed; the UI shows it only when there is nothing cached to display. */
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

/** Port of lib/providers/order_provider.dart. The backend is the source of truth for orders. */
class OrderRepository(
    private val prefs: CustomerPrefs,
    private val api: ApiClient,
    private val session: SessionRepository,
) {
    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()
    private var loadedFor: Int? = prefs.getInt(CustomerPrefs.CUSTOMER_ID)

    fun orderById(id: String): CustomerOrder? = _state.value.orders.firstOrNull { it.id == id }

    /** Reloads the first page of the signed-in customer's order history. */
    suspend fun refresh() {
        if (!session.isAuthenticated()) {
            _state.value = OrdersState(emptyList(), initialized = true)
            loadedFor = null
            return
        }
        val customerId = prefs.getInt(CustomerPrefs.CUSTOMER_ID)
        if (loadedFor != customerId) {
            // Another account signed in: never show the previous account's orders, even briefly.
            _state.value = OrdersState()
            loadedFor = customerId
        }
        _state.update { it.copy(refreshing = true) }
        try {
            val page = fetchPage(0)
            _state.update {
                it.copy(orders = page.orders, page = 0, hasMore = page.hasMore, error = null)
            }
            persist(page.orders)
        } catch (e: ApiException) {
            if (e.statusCode == 401) {
                // Expired/revoked session: require a fresh OTP login instead of showing stale data.
                session.expire()
                clear()
                return
            }
            // Keep persisted orders while the backend is temporarily unavailable.
            if (_state.value.orders.isEmpty()) _state.update { it.copy(orders = loadCached()) }
            _state.update { it.copy(error = e.message) }
        } finally {
            _state.update { it.copy(initialized = true, refreshing = false) }
        }
    }

    /** Appends the next history page (the backend pages `/customer/orders` 20 at a time). */
    suspend fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.loadingMore || current.refreshing || !session.isAuthenticated()) return
        _state.update { it.copy(loadingMore = true) }
        try {
            val next = fetchPage(current.page + 1)
            val known = _state.value.orders.map { it.id }.toSet()
            val merged = _state.value.orders + next.orders.filterNot { it.id in known }
            _state.update { it.copy(orders = merged, page = current.page + 1, hasMore = next.hasMore) }
            persist(merged)
        } catch (e: ApiException) {
            if (e.statusCode == 401) {
                session.expire()
                clear()
            }
        } finally {
            _state.update { it.copy(loadingMore = false) }
        }
    }

    private class Page(val orders: List<CustomerOrder>, val hasMore: Boolean)

    private suspend fun fetchPage(page: Int): Page {
        val data = api.execute { api.customerApi.orders(page, PAGE_SIZE) }
        val content = if (data is Map<*, *>) data["content"] else data
        if (content !is List<*>) throw ApiException(ApiException.FORMAT_MESSAGE, 0)
        val orders = withContext(Dispatchers.Default) {
            try {
                content.mapNotNull { it.asJsonMap() }.map { CustomerOrder.fromJson(it) }
            } catch (_: Exception) {
                throw ApiException(ApiException.FORMAT_MESSAGE, 0)
            }
        }
        val hasMore = (data as? Map<*, *>)?.get("hasNextPage") == true
        return Page(orders, hasMore)
    }

    private fun loadCached(): List<CustomerOrder> {
        val owner = prefs.getInt(CustomerPrefs.ORDERS_OWNER)
        if (owner == null || owner != prefs.getInt(CustomerPrefs.CUSTOMER_ID)) return emptyList()
        return prefs.getStringList(CustomerPrefs.ORDERS).orEmpty().mapNotNull { raw ->
            try {
                Json.decode(raw).asJsonMap()?.let { CustomerOrder.fromJson(it) }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun persist(orders: List<CustomerOrder>) {
        prefs.putStringList(CustomerPrefs.ORDERS, orders.map { Json.encode(it.toJson()) })
        prefs.getInt(CustomerPrefs.CUSTOMER_ID)?.let { prefs.putInt(CustomerPrefs.ORDERS_OWNER, it) }
    }

    private fun upsertFirst(order: CustomerOrder) {
        val orders = listOf(order) + _state.value.orders.filterNot { it.id == order.id }
        _state.update { it.copy(orders = orders) }
        persist(orders)
    }

    suspend fun create(items: List<CartItem>, address: Address, paymentMethod: String = "COD"): CustomerOrder {
        session.ensureCustomerId()
        val body = mapOf(
            "address" to address.toJson(),
            "paymentMethod" to paymentMethod,
            "items" to items.map { mapOf("productId" to it.product.id, "quantity" to it.quantity) },
        )
        val response = api.execute { api.customerApi.createOrder(ApiClient.jsonBody(body)) }.asJsonMap()
            ?: throw ApiException(ApiException.FORMAT_MESSAGE, 0)
        val order = parseOrder(response)
        upsertFirst(order)
        return order
    }

    /** Polls `/customer/orders/{id}/tracking` and updates the cached order. */
    suspend fun refreshTracking(order: CustomerOrder): CustomerOrder {
        val response = api.execute { api.customerApi.tracking(order.id) }.asJsonMap() ?: return order
        val updated = order.withTracking(response)
        val orders = _state.value.orders.map { if (it.id == order.id) it.withTracking(response) else it }
        _state.update { it.copy(orders = orders) }
        persist(orders)
        return updated
    }

    suspend fun createPaymentOrder(order: CustomerOrder): PaymentOrder {
        val orderId = order.id.toIntOrNull() ?: throw ApiException(ApiException.FORMAT_MESSAGE, 0)
        val response = api.execute {
            api.customerApi.createPaymentOrder(ApiClient.jsonBody(mapOf("orderId" to orderId)))
        }.asJsonMap() ?: throw ApiException(ApiException.FORMAT_MESSAGE, 0)
        return try {
            PaymentOrder.fromJson(response)
        } catch (_: Exception) {
            throw ApiException(ApiException.FORMAT_MESSAGE, 0)
        }
    }

    suspend fun verifyPayment(
        orderId: Int,
        gatewayOrderId: String,
        gatewayPaymentId: String,
        gatewaySignature: String,
    ): CustomerOrder {
        val body = mapOf(
            "orderId" to orderId,
            "gatewayOrderId" to gatewayOrderId,
            "gatewayPaymentId" to gatewayPaymentId,
            "gatewaySignature" to gatewaySignature,
        )
        val response = api.execute { api.customerApi.verifyPayment(ApiClient.jsonBody(body)) }.asJsonMap()
            ?: throw ApiException(ApiException.FORMAT_MESSAGE, 0)
        val order = parseOrder(response)
        upsertFirst(order)
        return order
    }

    /** Fetches one order from the backend (e.g. to confirm a payment the server already recorded). */
    suspend fun fetch(orderId: String): CustomerOrder {
        val response = api.execute { api.customerApi.order(orderId) }.asJsonMap()
            ?: throw ApiException(ApiException.FORMAT_MESSAGE, 0)
        val order = parseOrder(response)
        upsertFirst(order)
        return order
    }

    /** Clears the in-memory list and the device cache (used on logout). */
    fun clear() {
        _state.value = OrdersState(emptyList(), initialized = true)
        loadedFor = null
        prefs.remove(CustomerPrefs.ORDERS, CustomerPrefs.ORDERS_OWNER)
    }

    private companion object {
        const val PAGE_SIZE = 20
    }

    private fun parseOrder(json: Map<String, Any?>): CustomerOrder = try {
        CustomerOrder.fromJson(json)
    } catch (_: Exception) {
        throw ApiException(ApiException.FORMAT_MESSAGE, 0)
    }
}
