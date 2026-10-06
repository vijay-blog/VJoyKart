package com.nexamart.customer

import android.app.Application
import android.content.Context
import com.nexamart.customer.data.local.CustomerPrefs
import com.nexamart.customer.data.local.FlutterPrefsMigrator
import com.nexamart.customer.data.network.ApiClient
import com.nexamart.customer.repository.AddressRepository
import com.nexamart.customer.repository.AccountRepository
import com.nexamart.customer.repository.CartRepository
import com.nexamart.customer.repository.CatalogRepository
import com.nexamart.customer.repository.OrderRepository
import com.nexamart.customer.repository.SessionRepository
import com.nexamart.customer.presentation.checkout.PaymentResultBus
import com.razorpay.Checkout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container (app-scoped singletons, like the Flutter root providers). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = CustomerPrefs(context).also { FlutterPrefsMigrator.migrate(context, it) }
    val apiClient = ApiClient(prefs)
    val session = SessionRepository(prefs, apiClient)
    val catalog = CatalogRepository(apiClient)
    val cart = CartRepository(prefs)
    val addresses = AddressRepository(prefs)
    val orders = OrderRepository(prefs, apiClient, session)
    val account = AccountRepository(apiClient)
    val paymentResults = PaymentResultBus()

    /** Logs out of the CUSTOMER session and clears per-account data from the device. */
    fun logout() {
        session.logout()
        orders.clear()
    }

    /** Removes every account-scoped cache only after the server confirms deletion. */
    fun clearDeletedAccount() {
        session.logout()
        cart.clear()
        addresses.clear()
        orders.clear()
    }
}

class VJoyKartApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Same as Flutter's razorpay_flutter plugin: warm up the checkout assets.
        Checkout.preload(applicationContext)
        container.appScope.launch { container.catalog.load() }
        container.appScope.launch { container.orders.refresh() }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as VJoyKartApp).container
