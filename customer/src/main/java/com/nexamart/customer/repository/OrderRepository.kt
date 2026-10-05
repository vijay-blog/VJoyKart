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
)

/** Port of lib/providers/order_provider.dart. The backend is the source of truth for orders. */
class OrderRepository(
    private val prefs: CustomerPrefs,
    private val api: ApiClient,
    private val session: SessionRepository,
) {
    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()

    fun orderById(id: String): CustomerOrder? = _state.value.orders.firstOrNull { it.id == id }

    suspend fun refresh() {
        if (!session.isAuthenticated()) {
            _state.value = OrdersState(emptyList(), initialized = true)
            return
        }
        _state.update { it.copy(refreshing = true) }
        try {
            val data = api.execute { api.customerApi.orders() }
            val content = if (data is Map<*, *>) data["content"] else data
            if (content is List<*>) {
                val orders = withContext(Dispatchers.Default) {
                    try {
                        content.mapNotNull { it.asJsonMap() }.map { CustomerOrder.fromJson(it) }
                    } catch (_: Exception) {
                        throw ApiException(ApiException.FORMAT_MESSAGE, 0)
                    }
                }
                _state.value = OrdersState(orders, initialized = true)
                persist(orders)
            }
        } catch (e: ApiException) {
            // Keep persisted orders while the backend is temporarily unavailable.
            if (_state.value.orders.isEmpty()) _state.update { it.copy(orders = loadCached()) }
        } finally {
            _state.update { it.copy(initialized = true, refreshing = false) }
        }
    }

    private fun loadCached(): List<CustomerOrder> = prefs.getStringList(CustomerPrefs.ORDERS).orEmpty().mapNotNull { raw ->
        try {
            Json.decode(raw).asJsonMap()?.let { CustomerOrder.fromJson(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun persist(orders: List<CustomerOrder>) =
        prefs.putStringList(CustomerPrefs.ORDERS, orders.map { Json.encode(it.toJson()) })

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

    /** Clears the in-memory list and the device cache (used on logout). */
    fun clear() {
        _state.value = OrdersState(emptyList(), initialized = true)
        prefs.remove(CustomerPrefs.ORDERS)
    }

    private fun parseOrder(json: Map<String, Any?>): CustomerOrder = try {
        CustomerOrder.fromJson(json)
    } catch (_: Exception) {
        throw ApiException(ApiException.FORMAT_MESSAGE, 0)
    }
}
