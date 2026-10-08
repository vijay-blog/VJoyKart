package com.daily.nexamartpartner.features.delivery.presentation.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.net.toUri
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.databinding.FragmentDeliveryOrderDetailsBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrderAction
import com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrderDetails
import com.daily.nexamartpartner.features.delivery.domain.model.deliveryStatusLabel
import com.daily.nexamartpartner.features.delivery.presentation.location.DeliveryNavigationHelper
import com.daily.nexamartpartner.features.delivery.presentation.viewmodel.DeliveryOrderDetailsViewModel
import com.daily.nexamartpartner.features.delivery.presentation.viewmodel.DeliveryOrderDetailsViewModelFactory
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class DeliveryOrderDetailsScreen : Fragment(R.layout.fragment_delivery_order_details) {
    private var _binding: FragmentDeliveryOrderDetailsBinding? = null
    private val b get() = requireNotNull(_binding)
    private val id: String get() = requireArguments().getString("orderId").orEmpty()
    private val vm: DeliveryOrderDetailsViewModel by viewModels {
        DeliveryOrderDetailsViewModelFactory(
            id,
            requireContext().appContainer.provideGetDeliveryOrderDetailsUseCase(),
            requireContext().appContainer.providePerformDeliveryOrderActionUseCase()
        )
    }
    private val auth: AuthCoordinatorViewModel by activityViewModels {
        AuthCoordinatorViewModelFactory(
            requireContext().appContainer.restoreSessionUseCase,
            requireContext().appContainer.logoutUseCase,
            requireContext().appContainer.authStateStore
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeliveryOrderDetailsBinding.bind(view)
        b.retryButton.setOnClickListener { vm.retry() }
        b.swipeRefresh.setOnRefreshListener { vm.refresh() }
        b.shareButton.setOnClickListener { shareOrder() }
        collect()
    }

    private fun collect() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect(::render) }
                launch {
                    vm.events.collect {
                        when (it) {
                            DeliveryOrderDetailsViewModel.Event.SessionExpired -> auth.onSessionExpired()
                            is DeliveryOrderDetailsViewModel.Event.ActionSuccess -> Toast.makeText(requireContext(), "${it.action.label} successful", Toast.LENGTH_SHORT).show()
                            is DeliveryOrderDetailsViewModel.Event.Message -> Toast.makeText(requireContext(), it.text, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: DeliveryOrderDetailsViewModel.State) {
        b.progress.isVisible = state.loading
        b.swipeRefresh.isRefreshing = state.refreshing
        b.stateCard.isVisible = !state.loading && state.order == null
        b.content.isVisible = state.order != null
        b.shareButton.isVisible = state.order != null
        if (state.order == null) {
            b.stateTitle.text = if (state.unavailable) "Order unavailable" else "Unable to load order"
            b.stateMessage.text = state.error ?: "Please try again."
            return
        }
        renderOrder(state.order!!, state.busy)
    }

    private fun renderOrder(order: DeliveryOrderDetails, busy: Boolean) {
        b.orderId.text = order.orderId
        b.status.text = "Status: ${order.statusLabel ?: order.status}\nPickup: ${order.pickupName}" + (order.pickupAddress?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: "")
        b.status.setOnClickListener { order.pickupMapsUrl?.takeIf { it.isNotBlank() }?.let(::openUrl) }
        b.customer.text = order.customerName
        b.phone.text = order.customerPhone?.takeIf { it.isNotBlank() }?.let { "📞 $it" } ?: "Phone unavailable"
        b.phone.isEnabled = !order.customerPhone.isNullOrBlank()
        b.phone.setOnClickListener { order.customerPhone?.let(::dial) }
        b.address.text = order.address ?: "Address unavailable"
        b.address.isEnabled = !order.address.isNullOrBlank()
        b.address.setOnClickListener { order.address?.let(::openMaps) }
        b.mapButton.isEnabled = !order.address.isNullOrBlank() && !busy
        b.mapButton.setOnClickListener { order.address?.let(::openMaps) }
        b.navigateButton.isEnabled = !order.address.isNullOrBlank() && !busy
        b.navigateButton.setOnClickListener { order.address?.let(::navigateToAddress) }
        b.copyAddressButton.isEnabled = !order.address.isNullOrBlank()
        b.copyAddressButton.setOnClickListener { order.address?.let(::copyAddress) }
        renderProof(order)
        b.amount.text = "Total: ${order.totalAmount?.let { if (order.currencyCode.isNullOrBlank()) it.toPlainString() else "${order.currencyCode} ${it.toPlainString()}" } ?: "Unavailable"}"
        b.payment.text = "Payment: ${order.paymentMethod?.let { if (it == "COD") "Cash on Delivery • " else "Online • " } ?: ""}${order.paymentStatus ?: "Unavailable"}"

        b.items.removeAllViews()
        if (order.items.isEmpty()) addLine(b.items, "No item details available")
        else order.items.forEach { item ->
            val price = item.lineTotal?.toPlainString()?.let { "  •  ${item.currencySafePrefix(order.currencyCode)}$it" } ?: ""
            addLine(b.items, "${item.productName} × ${item.quantity}$price")
        }

        b.timeline.removeAllViews()
        if (order.timeline.isEmpty()) addLine(b.timeline, "No timeline events available")
        else order.timeline.forEach { event -> addLine(b.timeline, "${deliveryStatusLabel(event.status)}  ${event.timestamp ?: "Time unavailable"}") }

        // All four steps are always shown; only the backend-provided next step is enabled.
        b.actions.removeAllViews()
        when (order.status.uppercase()) {
            "DELIVERED" -> addLine(b.actions, "Delivered — no further action needed.")
            "CANCELLED" -> addLine(b.actions, "This order was cancelled.")
        }
        DeliveryOrderAction.entries.forEach { action ->
            val allowed = action in order.allowedActions
            val button = MaterialButton(requireContext()).apply {
                text = action.label
                isEnabled = allowed && !busy
                alpha = if (allowed) 1f else 0.45f
                setOnClickListener { if (action == DeliveryOrderAction.DELIVERED) confirmCompletion(action, order) else confirm(action) }
            }
            b.actions.addView(button)
        }
    }

    private fun addLine(parent: android.widget.LinearLayout, text: String) {
        parent.addView(TextView(requireContext()).apply {
            this.text = text
            textSize = 15f
            setPadding(0, 6, 0, 6)
        })
    }


    private fun renderProof(order: DeliveryOrderDetails) {
        val hasProofData = order.proofOfDeliveryRequired || !order.proofOfDeliveryStatus.isNullOrBlank() || !order.proofOfDeliveryUrl.isNullOrBlank()
        b.proofCard.isVisible = hasProofData
        if (!hasProofData) return
        val requirement = if (order.proofOfDeliveryRequired) "Required by backend" else "Optional / not specified"
        val status = order.proofOfDeliveryStatus?.takeIf { it.isNotBlank() } ?: "Not submitted"
        b.proofStatus.text = "Requirement: $requirement\nStatus: $status"
        b.viewProofButton.isVisible = !order.proofOfDeliveryUrl.isNullOrBlank()
        b.viewProofButton.setOnClickListener {
            val url = order.proofOfDeliveryUrl ?: return@setOnClickListener
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                .onFailure { Toast.makeText(requireContext(), "Unable to open proof", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun confirmCompletion(action: DeliveryOrderAction, order: DeliveryOrderDetails) {
        val proofLine = when {
            order.proofOfDeliveryRequired && order.proofOfDeliveryStatus.isNullOrBlank() -> "\n\nProof of delivery is marked required by the backend. Complete only after following the configured proof process."
            !order.proofOfDeliveryStatus.isNullOrBlank() -> "\n\nProof status: ${order.proofOfDeliveryStatus}"
            else -> ""
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm delivery")
            .setMessage("You are about to mark order ${order.orderId} as delivered.$proofLine\n\nOnly continue after handing the order to the customer.")
            .setNegativeButton("Not yet", null)
            .setPositiveButton("Confirm delivered") { _, _ -> vm.perform(action) }
            .show()
    }

    private fun confirm(action: DeliveryOrderAction) {
        AlertDialog.Builder(requireContext())
            .setTitle(action.label)
            .setMessage("Confirm ${action.label.lowercase()} for order $id?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ -> vm.perform(action) }
            .show()
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onFailure { Toast.makeText(requireContext(), "No maps app available", Toast.LENGTH_SHORT).show() }
    }

    private fun dial(phone: String) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}")))
    }

    private fun openMaps(address: String) {
        if (!DeliveryNavigationHelper.openMapsSearch(requireContext(), address)) {
            Toast.makeText(requireContext(), "No maps app available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToAddress(address: String) {
        if (!DeliveryNavigationHelper.openDestination(requireContext(), address)) {
            Toast.makeText(requireContext(), "No navigation app available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyAddress(address: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Delivery address", address))
        Toast.makeText(requireContext(), "Address copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareOrder() {
        val text = "VJoyKart delivery order ${id}. Please use the order ID to view delivery details."
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share order"))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private fun com.daily.nexamartpartner.features.delivery.domain.model.DeliveryOrderItem.currencySafePrefix(currency: String?): String =
    if (currency.isNullOrBlank()) "₹" else "$currency "
