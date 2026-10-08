package com.nexamart.customer.presentation.checkout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentUpiQrBinding
import com.nexamart.customer.presentation.common.Nav
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.presentation.common.showMessage
import com.nexamart.customer.util.Formats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Razorpay-generated, fixed-amount UPI QR. Payment status is accepted only from the backend. */
class UpiQrFragment : Fragment() {
    private var _binding: FragmentUpiQrBinding? = null
    private val binding get() = _binding!!
    private var polling = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentUpiQrBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val orderId = requireArguments().getInt(Nav.ARG_ORDER_ID)
        val amount = requireArguments().getDouble(Nav.ARG_AMOUNT)
        val orders = requireContext().appContainer.orders
        binding.amount.text = "Pay exactly ${Formats.rupeesExact(amount)}"
        binding.toolbar.setNavigationOnClickListener { cancelAndBack(orderId) }
        binding.cancel.setOnClickListener { cancelAndBack(orderId) }

        launchOnStarted {
            polling = true
            try {
                var session = orders.createUpiQr(orderId)
                while (polling && session.isPending) {
                    render(session)
                    delay(POLL_INTERVAL_MS)
                    session = orders.upiQrStatus(orderId)
                }
                render(session)
                if (session.isPaid) {
                    polling = false
                    findNavController().navigate(
                        R.id.orderSuccessFragment,
                        Bundle().apply { putString(Nav.ARG_ORDER_ID, orderId.toString()) },
                    )
                } else if (session.status != com.nexamart.customer.model.UpiQrSession.STATUS_CANCELLED) {
                    showMessage(session.message ?: "This QR payment is no longer active. You can retry safely.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(e.message ?: "Unable to load the UPI QR. Please try again.")
                findNavController().navigateUp()
            }
        }
    }

    private fun render(session: com.nexamart.customer.model.UpiQrSession) {
        binding.progress.visibility = if (session.isPending) View.VISIBLE else View.GONE
        binding.status.text = when (session.status) {
            com.nexamart.customer.model.UpiQrSession.STATUS_PENDING -> "Waiting for payment…"
            com.nexamart.customer.model.UpiQrSession.STATUS_PAID -> "Payment verified"
            com.nexamart.customer.model.UpiQrSession.STATUS_EXPIRED -> "QR expired"
            com.nexamart.customer.model.UpiQrSession.STATUS_CANCELLED -> "Payment cancelled"
            else -> "Payment failed"
        }
        session.imageUrl?.let {
            binding.qrCode.visibility = View.VISIBLE
            binding.qrCode.load(it)
        }
    }

    private fun cancelAndBack(orderId: Int) {
        if (!polling) {
            findNavController().navigateUp()
            return
        }
        polling = false
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { requireContext().appContainer.orders.cancelUpiQr(orderId) }
            if (isAdded) findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        polling = false
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 4000L
    }
}
