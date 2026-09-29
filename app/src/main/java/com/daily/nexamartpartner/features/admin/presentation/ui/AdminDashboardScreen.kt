package com.daily.nexamartpartner.features.admin.presentation.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.core.format.ValueFormatter
import com.daily.nexamartpartner.databinding.FragmentAdminDashboardBinding
import com.daily.nexamartpartner.databinding.ViewDashboardKpiCardBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.admin.domain.model.DashboardKpis
import com.daily.nexamartpartner.features.admin.domain.model.AdminNotification
import com.daily.nexamartpartner.features.admin.presentation.state.AdminDashboardUiState
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.AdminDashboardViewModel
import com.daily.nexamartpartner.features.admin.presentation.viewmodel.AdminDashboardViewModelFactory
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import com.daily.nexamartpartner.routing.ProtectedNavigator
import kotlinx.coroutines.launch

class AdminDashboardScreen : Fragment(R.layout.fragment_admin_dashboard) {
    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding: FragmentAdminDashboardBinding
        get() = requireNotNull(_binding)

    private val authCoordinatorViewModel: AuthCoordinatorViewModel by activityViewModels {
        AuthCoordinatorViewModelFactory(
            restoreSessionUseCase = requireContext().appContainer.restoreSessionUseCase,
            logoutUseCase = requireContext().appContainer.logoutUseCase,
            authStateStore = requireContext().appContainer.authStateStore
        )
    }

    private val viewModel: AdminDashboardViewModel by viewModels {
        AdminDashboardViewModelFactory(
            getAdminDashboardUseCase = requireContext().appContainer.provideAdminDashboardUseCase(),
            sessionManager = requireContext().appContainer.sessionManager
        )
    }

    private val recentOrderAdapter = OrderSummaryAdapter()
    private var latestNotifications: List<AdminNotification> = emptyList()
    private var unreadNotificationCount: Long = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminDashboardBinding.bind(view)
        val navigator = ProtectedNavigator(
            navController = findNavController(),
            authStateStore = requireContext().appContainer.authStateStore
        )

        binding.recentOrdersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recentOrdersRecyclerView.adapter = recentOrderAdapter

        setupQuickActions(navigator)
        setupHeaderActions(navigator)
        setupCommonActions()
        setupLoadingCards()
        collectUiState()
        collectEvents()
    }

    private fun setupHeaderActions(navigator: ProtectedNavigator) {
        binding.notificationButton.isEnabled = true
        binding.notificationButton.setOnClickListener { showNotifications() }
        binding.settingsButton.setOnClickListener {
            navigator.navigate(R.id.adminSettingsFragment)
        }
        binding.profileButton.setOnClickListener {
            navigator.navigate(R.id.adminProfilePlaceholderFragment)
        }
    }

    private fun setupQuickActions(navigator: ProtectedNavigator) {
        binding.ordersActionButton.setOnClickListener {
            navigator.navigate(R.id.adminOrdersFragment)
        }
        binding.productsActionButton.setOnClickListener {
            navigator.navigate(R.id.adminProductsFragment)
        }
        binding.categoriesActionButton.setOnClickListener {
            navigator.navigate(R.id.adminCategoriesFragment)
        }
        binding.deliveryPartnersActionButton.setOnClickListener {
            navigator.navigate(R.id.adminDeliveryPartnersFragment)
        }
        binding.customersActionButton.setOnClickListener {
            navigator.navigate(R.id.adminCustomersFragment)
        }
        binding.viewAllOrdersButton.setOnClickListener {
            navigator.navigate(R.id.adminOrdersFragment)
        }
    }

    private fun setupCommonActions() {
        binding.logoutButton.setOnClickListener {
            authCoordinatorViewModel.logout()
        }
        binding.retryButton.setOnClickListener {
            viewModel.retry()
        }
        binding.dashboardSwipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderHeader(state)
                    renderState(state)
                }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    if (event is AdminDashboardViewModel.Event.SessionExpired) {
                        authCoordinatorViewModel.onSessionExpired()
                    }
                }
            }
        }
    }

    private fun renderHeader(state: AdminDashboardUiState) {
        binding.greetingTitleText.text = if (state.adminName != null) {
            getString(R.string.admin_dashboard_greeting_with_name, state.greeting, state.adminName)
        } else {
            state.greeting
        }
        binding.adminNameText.text = state.adminName ?: getString(R.string.admin_dashboard_admin_fallback)
        binding.dashboardSwipeRefresh.isRefreshing = state.isRefreshing
    }

    private fun renderState(state: AdminDashboardUiState) {
        when (val content = state.content) {
            is AdminDashboardUiState.ContentState.Loading -> {
                binding.dashboardLoadingSection.isVisible = true
                binding.dashboardContentSection.isVisible = false
                binding.dashboardStateCard.isVisible = false
                binding.retryButton.isVisible = false
                binding.notificationButton.isEnabled = false
                latestNotifications = emptyList()
                unreadNotificationCount = 0L
            }

            is AdminDashboardUiState.ContentState.Success -> {
                binding.dashboardLoadingSection.isVisible = false
                binding.dashboardContentSection.isVisible = true
                binding.dashboardStateCard.isVisible = false
                renderKpis(content.dashboard.kpis)
                renderRecentOrders(
                    recentOrdersAvailable = content.dashboard.recentOrdersAvailable,
                    hasOrders = content.dashboard.recentOrders.isNotEmpty()
                )
                recentOrderAdapter.submitList(content.dashboard.recentOrders)
                latestNotifications = content.dashboard.notifications
                unreadNotificationCount = content.dashboard.unreadNotifications
                binding.notificationButton.isEnabled = true
                binding.notificationButton.contentDescription =
                    if (unreadNotificationCount > 0) {
                        "Notifications ($unreadNotificationCount unread)"
                    } else {
                        "Notifications"
                    }
            }

            is AdminDashboardUiState.ContentState.Empty -> {
                binding.dashboardLoadingSection.isVisible = false
                binding.dashboardContentSection.isVisible = true
                binding.dashboardStateCard.isVisible = true
                binding.stateTitleText.text = content.title
                binding.stateDescriptionText.text = content.message
                binding.retryButton.isVisible = true
                renderKpis(null)
                renderRecentOrders(recentOrdersAvailable = true, hasOrders = false)
                recentOrderAdapter.submitList(emptyList())
                latestNotifications = emptyList()
                unreadNotificationCount = 0L
                binding.notificationButton.isEnabled = true
            }

            is AdminDashboardUiState.ContentState.Error -> {
                binding.dashboardLoadingSection.isVisible = false
                binding.dashboardContentSection.isVisible = true
                binding.dashboardStateCard.isVisible = true
                binding.stateTitleText.text = content.title
                binding.stateDescriptionText.text = content.message
                binding.retryButton.isVisible = true
                renderKpis(null)
                renderRecentOrders(recentOrdersAvailable = false, hasOrders = false)
                recentOrderAdapter.submitList(emptyList())
                latestNotifications = emptyList()
                unreadNotificationCount = 0L
                binding.notificationButton.isEnabled = true
            }

            is AdminDashboardUiState.ContentState.Unavailable -> {
                binding.dashboardLoadingSection.isVisible = false
                binding.dashboardContentSection.isVisible = true
                binding.dashboardStateCard.isVisible = true
                binding.stateTitleText.text = content.title
                binding.stateDescriptionText.text = content.message
                binding.retryButton.isVisible = true
                renderKpis(null)
                renderRecentOrders(recentOrdersAvailable = false, hasOrders = false)
                recentOrderAdapter.submitList(emptyList())
                latestNotifications = emptyList()
                unreadNotificationCount = 0L
                binding.notificationButton.isEnabled = true
            }
        }
    }

    private fun showNotifications() {
        if (latestNotifications.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("Notifications")
                .setMessage("No notifications yet.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val message = latestNotifications.joinToString("\n\n") { n ->
            val order = n.orderId?.let { "Order #$it" } ?: ""
            listOf(n.title, n.message, order, n.createdAt.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }

        AlertDialog.Builder(requireContext())
            .setTitle(
                if (unreadNotificationCount > 0) {
                    "Notifications ($unreadNotificationCount unread)"
                } else {
                    "Notifications"
                }
            )
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun renderKpis(kpis: DashboardKpis?) {
        bindKpiCard(
            card = binding.totalOrdersCard,
            icon = android.R.drawable.ic_menu_agenda,
            label = getString(R.string.admin_dashboard_total_orders),
            value = kpis?.totalOrders?.let(ValueFormatter::formatCount)
        )
        bindKpiCard(
            card = binding.todayOrdersCard,
            icon = android.R.drawable.ic_menu_today,
            label = getString(R.string.admin_dashboard_today_orders),
            value = kpis?.todayOrders?.let(ValueFormatter::formatCount)
        )
        bindKpiCard(
            card = binding.pendingOrdersCard,
            icon = android.R.drawable.ic_popup_sync,
            label = getString(R.string.admin_dashboard_pending_orders),
            value = kpis?.pendingOrders?.let(ValueFormatter::formatCount)
        )
        bindKpiCard(
            card = binding.outForDeliveryCard,
            icon = android.R.drawable.ic_menu_directions,
            label = getString(R.string.admin_dashboard_out_for_delivery),
            value = kpis?.outForDelivery?.let(ValueFormatter::formatCount)
        )
        bindKpiCard(
            card = binding.deliveredTodayCard,
            icon = android.R.drawable.checkbox_on_background,
            label = getString(R.string.admin_dashboard_delivered_today),
            value = kpis?.deliveredToday?.let(ValueFormatter::formatCount)
        )
        bindKpiCard(
            card = binding.todaySalesCard,
            icon = android.R.drawable.ic_menu_info_details,
            label = getString(R.string.admin_dashboard_today_sales),
            value = kpis?.let { ValueFormatter.formatCurrency(it.todaySales, it.currencyCode) }
        )
    }

    private fun setupLoadingCards() {
        bindKpiCard(
            card = binding.loadingCardOne,
            icon = android.R.drawable.ic_menu_agenda,
            label = getString(R.string.admin_dashboard_total_orders),
            value = null
        )
        bindKpiCard(
            card = binding.loadingCardTwo,
            icon = android.R.drawable.ic_menu_info_details,
            label = getString(R.string.admin_dashboard_today_sales),
            value = null
        )
    }

    private fun bindKpiCard(card: ViewDashboardKpiCardBinding, icon: Int, label: String, value: String?) {
        card.kpiIcon.setImageResource(icon)
        card.kpiLabel.text = label
        card.kpiValue.text = value ?: getString(R.string.admin_dashboard_value_unavailable)
    }

    private fun renderRecentOrders(recentOrdersAvailable: Boolean, hasOrders: Boolean) {
        binding.recentOrdersUnavailableText.isVisible = !recentOrdersAvailable
        binding.noRecentOrdersText.isVisible = recentOrdersAvailable && !hasOrders
        binding.recentOrdersRecyclerView.isVisible = recentOrdersAvailable && hasOrders
        binding.viewAllOrdersButton.isVisible = recentOrdersAvailable
    }

    override fun onDestroyView() {
        binding.recentOrdersRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
