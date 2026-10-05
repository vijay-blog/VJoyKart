package com.nexamart.customer.presentation.checkout

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Razorpay delivers results to the Activity; they are relayed to the checkout screen through this bus. */
sealed interface PaymentResult {
    data class Success(val paymentId: String?, val orderId: String?, val signature: String?) : PaymentResult
    data class Error(val code: Int, val description: String?) : PaymentResult
    data class ExternalWallet(val walletName: String?) : PaymentResult
}

class PaymentResultBus {
    private val _results = MutableSharedFlow<PaymentResult>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val results: SharedFlow<PaymentResult> = _results.asSharedFlow()

    fun emit(result: PaymentResult) {
        _results.tryEmit(result)
    }

    /** Consumes the replayed result so it is not handled twice (e.g. after rotation). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consume() = _results.resetReplayCache()
}
