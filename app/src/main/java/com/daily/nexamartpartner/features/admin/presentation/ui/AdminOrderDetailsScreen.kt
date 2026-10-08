package com.daily.nexamartpartner.features.admin.presentation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.core.format.ValueFormatter
import com.daily.nexamartpartner.core.widgets.UiFeedback
import com.daily.nexamartpartner.databinding.FragmentAdminOrderDetailsBinding
import com.daily.nexamartpartner.databinding.ItemOrderTimelineBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.admin.domain.model.AdminOrderDetails
import com.daily.nexamartpartner.features.admin.domain.model.OrderStatus
import com.daily.nexamartpartner.features.admin.presentation.state.AdminOrderDetailsUiState
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.AdminOrderDetailsViewModel
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.AdminOrderDetailsViewModelFactory
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import java.math.BigDecimal
import kotlinx.coroutines.launch

class AdminOrderDetailsScreen : Fragment(R.layout.fragment_admin_order_details) {
    private var _binding: FragmentAdminOrderDetailsBinding? = null
    private val binding: FragmentAdminOrderDetailsBinding
        get() = requireNotNull(_binding)

    private val authCoordinatorViewModel: AuthCoordinatorViewModel by activityViewModels {
        AuthCoordinatorViewModelFactory(
            restoreSessionUseCase = requireContext().appContainer.restoreSessionUseCase,
            logoutUseCase = requireContext().appContainer.logoutUseCase,
            authStateStore = requireContext().appContainer.authStateStore
        )
    }

    private val viewModel: AdminOrderDetailsViewModel by viewModels {
        AdminOrderDetailsViewModelFactory(
            owner = this,
            defaultArgs = arguments,
            getAdminOrderDetailsUseCase = requireContext().appContainer.provideAdminOrderDetailsUseCase(),
            updateAdminOrderStatusUseCase = requireContext().appContainer.provideUpdateAdminOrderStatusUseCase(),
            cancelAdminOrderUseCase = requireContext().appContainer.provideCancelAdminOrderUseCase()
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminOrderDetailsBinding.bind(view)
        setupActions()
        collectUi()
        collectEvents()
    }

    private fun setupActions() {
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.detailsRetryButton.setOnClickListener { viewModel.retry() }
        binding.detailsRefreshButton.setOnClickListener { viewModel.refresh() }
        binding.orderDetailsSwipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.updateStatusButton.setOnClickListener { showUpdateStatusDialog() }
        binding.cancelOrderButton.setOnClickListener { showCancelDialog() }
    }

    private fun collectUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AdminOrderDetailsViewModel.Event.SessionExpired -> authCoordinatorViewModel.onSessionExpired()
                        is AdminOrderDetailsViewModel.Event.ShowMessage -> {
                            UiFeedback.showSnackbar(binding.root, event.message)
                        }
                    }
                }
            }
        }
    }

    private fun render(state: AdminOrderDetailsUiState) {
        binding.orderDetailsSwipeRefresh.isRefreshing = state.isRefreshing
        binding.updateStatusButton.isEnabled = !state.isStatusUpdating
        binding.cancelOrderButton.isEnabled = !state.isCancelling

        when (val content = state.content) {
            is AdminOrderDetailsUiState.ContentState.Loading -> {
                binding.detailsLoadingSection.isVisible = true
                binding.detailsStateSection.isVisible = false
                binding.detailsContentSection.isVisible = false
            }

            is AdminOrderDetailsUiState.ContentState.Success -> {
                binding.detailsLoadingSection.isVisible = false
                binding.detailsStateSection.isVisible = false
                binding.detailsContentSection.isVisible = true
                renderDetails(content.details)
            }

            is AdminOrderDetailsUiState.ContentState.Error -> {
                binding.detailsLoadingSection.isVisible = false
                binding.detailsStateSection.isVisible = true
                binding.detailsContentSection.isVisible = false
                binding.detailsStateTitleText.text = content.title
                binding.detailsStateDescriptionText.text = content.message
            }

            is AdminOrderDetailsUiState.ContentState.Unavailable -> {
                binding.detailsLoadingSection.isVisible = false
                binding.detailsStateSection.isVisible = true
                binding.detailsContentSection.isVisible = false
                binding.detailsStateTitleText.text = content.title
                binding.detailsStateDescriptionText.text = content.message
            }
        }
    }

    private fun renderDetails(details: AdminOrderDetails) {
        binding.orderIdText.text = details.orderId
        binding.orderStatusText.text = getString(
            R.string.admin_order_details_status_template,
            details.currentStatus.backendValue
        )
        binding.orderCreatedAtText.text = getString(
            R.string.admin_order_details_created_template,
            details.createdAt ?: getString(R.string.admin_dashboard_time_unavailable)
        )

        binding.customerNameText.text = getString(
            R.string.admin_order_details_customer_name_template,
            details.customer.name.ifBlank { getString(R.string.admin_dashboard_unknown_customer) }
        )
        binding.customerPhoneText.text = getString(
            R.string.admin_order_details_customer_phone_template,
            details.customer.phone ?: getString(R.string.admin_order_details_unavailable)
        )
        binding.customerAddressText.text = getString(
            R.string.admin_order_details_customer_address_template,
            details.customer.address ?: getString(R.string.admin_order_details_unavailable)
        )

        binding.itemsContainer.removeAllViews()
        details.items.forEach { item ->
            val text = TextView(requireContext()).apply {
                text = getString(
                    R.string.admin_order_details_item_template,
                    item.productName,
                    item.quantity,
                    item.lineTotal?.let { ValueFormatter.formatCurrency(it, item.currencyCode) }
                        ?: getString(R.string.admin_dashboard_amount_unavailable)
                )
            }
            binding.itemsContainer.addView(text)
        }

        val paymentMethod = details.payment?.method?.backendValue ?: getString(R.string.admin_order_details_unavailable)
        val paymentStatus = details.payment?.status?.backendValue ?: getString(R.string.admin_order_details_unavailable)
        val paymentRef = details.payment?.transactionReference ?: getString(R.string.admin_order_details_unavailable)
        binding.paymentMethodText.text = getString(R.string.admin_order_details_payment_method_template, paymentMethod)
        binding.paymentStatusText.text = getString(R.string.admin_order_details_payment_status_template, paymentStatus)
        binding.paymentReferenceText.text = getString(R.string.admin_order_details_payment_reference_template, paymentRef)

        binding.orderTotalsText.text = formatTotals(details)

        val unavailable = getString(R.string.admin_order_details_unavailable)
        val delivery = details.delivery
        val deliveryStatusLabel = listOfNotNull(
            delivery?.status?.takeIf { it.isNotBlank() },
            delivery?.assignmentStatus?.takeIf { it.isNotBlank() && !it.equals(delivery.status, true) }
        ).joinToString(" • ").ifBlank { unavailable }
        binding.deliveryStatusText.text = getString(
            R.string.admin_order_details_delivery_status_template,
            deliveryStatusLabel
        )
        val partnerLabel = delivery?.partnerName?.let { name ->
            delivery.partnerPhone?.let { "$name • $it" } ?: name
        } ?: unavailable
        binding.deliveryPartnerText.text = getString(
            R.string.admin_order_details_delivery_partner_template,
            partnerLabel
        )
        binding.deliveryPartnerText.isClickable = details.delivery?.partnerId != null
        binding.deliveryPartnerText.setOnClickListener(
            details.delivery?.partnerId?.let { partnerId ->
                View.OnClickListener {
                    val args = Bundle().apply { putString("partnerId", partnerId) }
                    findNavController().navigate(R.id.adminDeliveryPartnerDetailsFragment, args)
                }
            }
        )
        binding.deliveryAssignedAtText.text = getString(
            R.string.admin_order_details_delivery_assigned_at_template,
            details.delivery?.assignedAt ?: getString(R.string.admin_order_details_unavailable)
        )

        binding.timelineContainer.removeAllViews()
        details.timeline.forEach { entry ->
            val timelineBinding = ItemOrderTimelineBinding.inflate(
                LayoutInflater.from(requireContext()),
                binding.timelineContainer,
                false
            )
            timelineBinding.timelineStatusText.text = entry.status.backendValue
            timelineBinding.timelineTimestampText.text =
                entry.timestamp ?: getString(R.string.admin_order_details_timestamp_unavailable)
            binding.timelineContainer.addView(timelineBinding.root)
        }

        binding.updateStatusButton.isEnabled = details.allowedTransitions.isNotEmpty()
        binding.cancelOrderButton.isEnabled = details.canCancel
    }

    private fun formatTotals(details: AdminOrderDetails): String {
        val totals = details.totals ?: return getString(R.string.admin_order_details_unavailable)
        return getString(
            R.string.admin_order_details_totals_template,
            money(totals.subtotal, totals.currencyCode),
            money(totals.deliveryFee, totals.currencyCode),
            money(totals.discount, totals.currencyCode),
            money(totals.tax, totals.currencyCode),
            money(totals.grandTotal, totals.currencyCode)
        )
    }

    private fun money(value: BigDecimal?, currencyCode: String?): String {
        return value?.let { ValueFormatter.formatCurrency(it, currencyCode) }
            ?: getString(R.string.admin_order_details_unavailable)
    }

    private fun showUpdateStatusDialog() {
        val currentState = viewModel.uiState.value.content
        if (currentState !is AdminOrderDetailsUiState.ContentState.Success) return
        val transitions = currentState.details.allowedTransitions
        if (transitions.isEmpty()) {
            UiFeedback.showSnackbar(binding.root, getString(R.string.admin_order_details_no_transition))
            return
        }
        val transitionLabels = transitions.map { it.backendValue }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.admin_order_details_update_status))
            .setItems(transitionLabels) { _, index ->
                val target = transitions[index]
                UiFeedback.showConfirmationDialog(
                    anchor = binding.root,
                    title = getString(R.string.admin_order_details_update_status),
                    message = getString(
                        R.string.admin_order_details_update_confirmation,
                        currentState.details.orderId,
                        target.backendValue
                    ),
                    positiveActionText = getString(R.string.common_confirm),
                    negativeActionText = getString(R.string.common_cancel),
                    onConfirmed = { viewModel.updateStatus(target) }
                )
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showCancelDialog() {
        val currentState = viewModel.uiState.value.content
        if (currentState !is AdminOrderDetailsUiState.ContentState.Success) return
        if (!currentState.details.canCancel) {
            UiFeedback.showSnackbar(binding.root, getString(R.string.admin_order_details_cancel_not_supported))
            return
        }
        UiFeedback.showConfirmationDialog(
            anchor = binding.root,
            title = getString(R.string.admin_order_details_cancel_order),
            message = getString(R.string.admin_order_details_cancel_confirmation),
            positiveActionText = getString(R.string.common_confirm),
            negativeActionText = getString(R.string.common_cancel),
            onConfirmed = { viewModel.cancelOrder(reason = null) }
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
