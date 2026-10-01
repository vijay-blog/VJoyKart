package com.daily.nexamartpartner

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.daily.nexamartpartner.databinding.ActivityMainBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import com.daily.nexamartpartner.routing.AuthDestinationResolver
import com.daily.nexamartpartner.routing.NavigationGuard
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var authCoordinatorViewModel: AuthCoordinatorViewModel
    private var isGuardRedirecting = false
    private var pendingDeliveryOrderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent screenshots/screen capture of customer, order, address and earnings data.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authCoordinatorViewModel = ViewModelProvider(
            this,
            AuthCoordinatorViewModelFactory(
                restoreSessionUseCase = applicationContext.appContainer.restoreSessionUseCase,
                logoutUseCase = applicationContext.appContainer.logoutUseCase,
                authStateStore = applicationContext.appContainer.authStateStore
            )
        )[AuthCoordinatorViewModel::class.java]

        observeAuthState()
        observeConnectivity()
        pendingDeliveryOrderId = intent?.getStringExtra(EXTRA_DELIVERY_ORDER_ID)
        authCoordinatorViewModel.initialize()
    }

    private fun observeConnectivity() {
        val monitor = applicationContext.appContainer.networkConnectivityMonitor
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                monitor.state.collect { state ->
                    binding.offlineBanner.isVisible =
                        state == com.daily.nexamartpartner.core.network.NetworkConnectivityMonitor.State.OFFLINE
                }
            }
        }
    }

    private fun observeAuthState() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController
        installNavigationGuard(navController)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authCoordinatorViewModel.authState.collect { authState ->
                    val targetRootId = AuthDestinationResolver.resolve(authState)
                    if (!isAtOrWithinDestination(navController, targetRootId)) {
                        // Use the resource-ID overload explicitly. With Navigation 2.9.x,
                        // navigate(Int) can resolve to the typed-route overload and interpret
                        // the destination ID as a route of type Int, causing:
                        // "Destination with route Int cannot be found in navigation graph".
                        navController.navigate(
                            targetRootId,
                            null,
                            navOptions {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        )
                    }
                    openPendingDeliveryOrder(navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_DELIVERY_ORDER_ID)?.let { orderId ->
            pendingDeliveryOrderId = orderId
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
            openPendingDeliveryOrder(navHostFragment.navController)
        }
    }

    /** Opens the order from a 'New delivery assigned' notification once the partner is authorised. */
    private fun openPendingDeliveryOrder(navController: NavController) {
        val orderId = pendingDeliveryOrderId ?: return
        val target = R.id.deliveryOrderDetailsFragment
        val allowed = NavigationGuard.resolveAuthorizedDestination(
            authState = authCoordinatorViewModel.authState.value,
            requestedDestinationId = target
        )
        if (allowed != target) return
        pendingDeliveryOrderId = null
        navController.navigate(target, android.os.Bundle().apply { putString("orderId", orderId) })
    }

    private fun installNavigationGuard(navController: NavController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (isGuardRedirecting) {
                isGuardRedirecting = false
                return@addOnDestinationChangedListener
            }
            val authState = authCoordinatorViewModel.authState.value
            val allowedDestination = NavigationGuard.resolveAuthorizedDestination(
                authState = authState,
                requestedDestinationId = destination.id
            )
            if (!isAtOrWithinDestination(navController, allowedDestination)) {
                isGuardRedirecting = true
                navController.navigate(
                    allowedDestination,
                    null,
                    navOptions {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                )
            }
        }
    }

    private fun isAtOrWithinDestination(navController: NavController, destinationId: Int): Boolean {
        val current = navController.currentDestination ?: return false
        return current.id == destinationId || current.hierarchy.any { it.id == destinationId }
    }

    companion object {
        const val EXTRA_DELIVERY_ORDER_ID = "com.daily.nexamartpartner.extra.DELIVERY_ORDER_ID"
    }
}
