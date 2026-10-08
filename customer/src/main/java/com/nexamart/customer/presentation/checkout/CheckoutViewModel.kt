package com.nexamart.customer.presentation.checkout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexamart.customer.AppContainer
import com.nexamart.customer.core.ApiException
import com.nexamart.customer.core.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToLong

data class CheckoutState(
    val checkingSession: Boolean = true,
    val authenticated: Boolean = false,
    val loading: Boolean = false,
    val paymentMethod: String = CheckoutViewModel.COD,
)

sealed interface CheckoutEvent {
    data class Message(val text: String) : CheckoutEvent
    data class OpenRazorpay(val keyId: String, val options: JSONObject) : CheckoutEvent
    data class OpenUpiQr(val orderId: Int, val amount: Double) : CheckoutEvent
    data class OrderPlaced(val orderId: String) : CheckoutEvent
}

/**
 * Port of _CheckoutScreenState. Payment success is only trusted after the backend
 * `/customer/payments/razorpay/verify` call succeeds; the pending ids survive process death.
 */
class CheckoutViewModel(
    private val container: AppContainer,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(CheckoutState(paymentMethod = handle[KEY_METHOD] ?: COD))
    val state: StateFlow<CheckoutState> = _state.asStateFlow()
    private val _events = Channel<CheckoutEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var pendingOnlineOrderId: Int?
        get() = handle[KEY_PENDING_ORDER]
        set(value) { handle[KEY_PENDING_ORDER] = value }
    private var pendingGatewayOrderId: String?
        get() = handle[KEY_PENDING_GATEWAY]
        set(value) { handle[KEY_PENDING_GATEWAY] = value }

    init {
        checkSession()
    }

    private fun checkSession() {
        val ok = container.session.isAuthenticated()
        _state.update { it.copy(authenticated = ok, checkingSession = false) }
        if (ok) viewModelScope.launch { container.orders.refresh() }
    }

    fun onAuthenticated() {
        _state.update { it.copy(authenticated = true) }
        viewModelScope.launch { container.orders.refresh() }
    }

    fun selectPayment(method: String) {
        handle[KEY_METHOD] = method
        _state.update { it.copy(paymentMethod = method) }
    }

    fun place() {
        if (_state.value.loading) return
        val selected = container.addresses.state.value.selected
        if (selected == null) {
            _events.trySend(CheckoutEvent.Message("Please add/select a delivery address."))
            return
        }
        val method = _state.value.paymentMethod
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val order = container.orders.create(container.cart.current, selected, paymentMethod = method)
                if (method == ONLINE) {
                    val paymentOrder = container.orders.createPaymentOrder(order)
                    pendingOnlineOrderId = paymentOrder.orderId
                    pendingGatewayOrderId = paymentOrder.gatewayOrderId
                    val options = JSONObject()
                        .put("key", paymentOrder.keyId)
                        .put("amount", (paymentOrder.amount * 100).roundToLong())
                        .put("currency", paymentOrder.currency)
                        .put("order_id", paymentOrder.gatewayOrderId)
                        .put("name", "VJoyKart")
                        .put("description", "Order ${order.orderNumber}")
                        .put("prefill", JSONObject().put("contact", selected.mobile).put("name", selected.name))
                        .put("theme", JSONObject().put("color", "#3454D1"))
                    container.paymentResults.consume()
                    _events.send(CheckoutEvent.OpenRazorpay(paymentOrder.keyId, options))
                } else if (method == UPI_QR) {
                    pendingOnlineOrderId = order.id.toIntOrNull()
                        ?: throw ApiException("Payment order id was invalid. Please try again.", 400)
                    _events.send(CheckoutEvent.OpenUpiQr(pendingOnlineOrderId!!, order.total))
                } else {
                    container.cart.clear()
                    _events.send(CheckoutEvent.OrderPlaced(order.id))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(CheckoutEvent.Message(e.userMessage(PLACE_FALLBACK)))
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun onPaymentResult(result: PaymentResult) {
        when (result) {
            is PaymentResult.Success -> onPaymentSuccess(result)
            is PaymentResult.Error -> {
                pendingOnlineOrderId = null
                pendingGatewayOrderId = null
                _events.trySend(CheckoutEvent.Message(paymentErrorMessage(result.description)))
            }
            is PaymentResult.ExternalWallet ->
                _events.trySend(CheckoutEvent.Message("External wallet selected: ${result.walletName}"))
        }
    }

    private fun onPaymentSuccess(result: PaymentResult.Success) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val orderId = pendingOnlineOrderId
                val gatewayOrderId = result.orderId ?: pendingGatewayOrderId
                val paymentId = result.paymentId
                val signature = result.signature
                if (orderId == null || gatewayOrderId.isNullOrEmpty() || paymentId.isNullOrEmpty() || signature.isNullOrEmpty()) {
                    throw ApiException(
                        "Payment completed but the verification details were incomplete. Please contact support before placing another order.",
                        400,
                    )
                }
                val order = container.orders.verifyPayment(
                    orderId = orderId,
                    gatewayOrderId = gatewayOrderId,
                    gatewayPaymentId = paymentId,
                    gatewaySignature = signature,
                )
                pendingOnlineOrderId = null
                pendingGatewayOrderId = null
                container.cart.clear()
                _events.send(CheckoutEvent.OrderPlaced(order.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(CheckoutEvent.Message(e.userMessage(PLACE_FALLBACK)))
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    companion object {
        const val COD = "COD"
        const val ONLINE = "ONLINE"
        const val UPI_QR = "UPI_QR"
        private const val KEY_METHOD = "paymentMethod"
        private const val KEY_PENDING_ORDER = "pendingOnlineOrderId"
        private const val KEY_PENDING_GATEWAY = "pendingGatewayOrderId"
        private const val PLACE_FALLBACK =
            "Unable to place your order right now. Please check your internet connection and try again."

        /** Razorpay returns a JSON error payload; show its description when present. */
        fun paymentErrorMessage(raw: String?): String {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return "Payment failed or cancelled"
            return try {
                val json = JSONObject(text)
                val error = json.optJSONObject("error")
                error?.optString("description")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: json.optString("description").takeIf { it.isNotBlank() && it != "null" }
                    ?: "Payment failed or cancelled"
            } catch (_: Exception) {
                text
            }
        }
    }
}
