package com.daily.nexamartpartner.features.delivery.presentation.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.databinding.FragmentDeliveryDashboardBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import com.daily.nexamartpartner.features.delivery.presentation.state.DeliveryDashboardUiState
import com.daily.nexamartpartner.features.delivery.presentation.viewmodel.DeliveryDashboardViewModel
import com.daily.nexamartpartner.features.delivery.presentation.viewmodel.DeliveryDashboardViewModelFactory
import com.daily.nexamartpartner.routing.ProtectedNavigator
import kotlinx.coroutines.launch
import java.math.BigDecimal

class DeliveryDashboardScreen : Fragment(R.layout.fragment_delivery_dashboard) {
    private var _binding: FragmentDeliveryDashboardBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val authViewModel: AuthCoordinatorViewModel by activityViewModels {
        AuthCoordinatorViewModelFactory(requireContext().appContainer.restoreSessionUseCase, requireContext().appContainer.logoutUseCase, requireContext().appContainer.authStateStore)
    }
    private val viewModel: DeliveryDashboardViewModel by viewModels {
        DeliveryDashboardViewModelFactory(requireContext().appContainer.provideGetDeliveryDashboardUseCase(), requireContext().appContainer.sessionManager)
    }

    private val presencePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requireContext().appContainer.deliveryPresenceReporter.start()
    }

    override fun onStart() {
        super.onStart()
        startPresenceReporting()
        viewModel.refresh()
    }

    /** Location is needed so the backend can auto-assign nearby orders from the VJoyKart Store. */
    private fun startPresenceReporting() {
        val reporter = requireContext().appContainer.deliveryPresenceReporter
        val missing = buildList {
            if (!reporter.hasLocationPermission()) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isEmpty() || permissionsRequested) reporter.start() else {
            permissionsRequested = true
            presencePermissions.launch(missing.toTypedArray())
        }
    }
    private var permissionsRequested = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeliveryDashboardBinding.bind(view)
        val navigator = ProtectedNavigator(findNavController(), requireContext().appContainer.authStateStore)
        binding.assignedOrdersButton.setOnClickListener { navigator.navigate(R.id.deliveryAssignedOrdersFragment) }
        binding.historyButton.setOnClickListener { navigator.navigate(R.id.deliveryHistoryFragment) }
        binding.earningsButton.setOnClickListener { navigator.navigate(R.id.deliveryEarningsPlaceholderFragment) }
        binding.profileButton.setOnClickListener { navigator.navigate(R.id.deliveryProfilePlaceholderFragment) }
        binding.availabilityButton.setOnClickListener { navigator.navigate(R.id.deliveryAvailabilityPlaceholderFragment) }
        binding.notificationsButton.setOnClickListener { navigator.navigate(R.id.deliveryNotificationsPlaceholderFragment) }
        binding.logoutButton.setOnClickListener { requireContext().appContainer.deliveryPresenceReporter.stop(); authViewModel.logout() }
        binding.retryButton.setOnClickListener { viewModel.retry() }
        binding.deliveryDashboardSwipeRefresh.setOnRefreshListener { viewModel.refresh() }
        collectState()
        collectEvents()
    }

    private fun collectState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.partnerNameText.text = state.partnerName ?: getString(R.string.delivery_partner_fallback_name)
                    binding.deliveryDashboardSwipeRefresh.isRefreshing = state.isRefreshing
                    when (val content = state.content) {
                        DeliveryDashboardUiState.ContentState.Loading -> {
                            binding.dashboardStateCard.isVisible = false
                            binding.dashboardMessageText.text = getString(R.string.delivery_dashboard_loading)
                            clearMetrics()
                        }
                        is DeliveryDashboardUiState.ContentState.Success -> {
                            binding.dashboardStateCard.isVisible = false
                            val d = content.dashboard
                            binding.availabilityText.text = getString(R.string.delivery_dashboard_availability, d.availability ?: getString(R.string.unavailable))
                            binding.dashboardMessageText.text = if (d.hasMetrics) getString(R.string.delivery_dashboard_live_data) else getString(R.string.delivery_dashboard_no_metrics)
                            binding.activeOrdersValue.text = number(d.activeOrders)
                            binding.assignedOrdersValue.text = number(d.assignedOrders)
                            binding.pickedUpOrdersValue.text = number(d.pickedUpOrders)
                            binding.outForDeliveryValue.text = number(d.outForDeliveryOrders)
                            binding.completedTodayValue.text = number(d.completedToday)
                            binding.todayEarningsValue.text = d.todayEarnings?.let { formatMoney(it, d.currencyCode) } ?: getString(R.string.unavailable)
                        }
                        is DeliveryDashboardUiState.ContentState.Empty -> showState(content.title, content.message, true)
                        is DeliveryDashboardUiState.ContentState.Error -> showState(content.title, content.message, true)
                        is DeliveryDashboardUiState.ContentState.Unavailable -> showState(content.title, content.message, true)
                    }
                }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event -> if (event is DeliveryDashboardViewModel.Event.SessionExpired) authViewModel.onSessionExpired() }
            }
        }
    }

    private fun showState(title: String, message: String, retry: Boolean) {
        binding.dashboardStateCard.isVisible = true
        binding.stateTitleText.text = title
        binding.stateDescriptionText.text = message
        binding.retryButton.isVisible = retry
        clearMetrics()
    }
    private fun clearMetrics() {
        val value = getString(R.string.unavailable)
        binding.activeOrdersValue.text = value
        binding.assignedOrdersValue.text = value
        binding.pickedUpOrdersValue.text = value
        binding.outForDeliveryValue.text = value
        binding.completedTodayValue.text = value
        binding.todayEarningsValue.text = value
    }
    private fun number(value: Long?): String = value?.toString() ?: getString(R.string.unavailable)
    private fun formatMoney(value: BigDecimal, currency: String?): String = if (currency.isNullOrBlank()) value.toPlainString() else "${currency} ${value.toPlainString()}"

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
