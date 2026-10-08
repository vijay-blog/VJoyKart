package com.nexamart.customer

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.NavHostFragment
import com.nexamart.customer.databinding.ActivityMainBinding
import com.nexamart.customer.presentation.checkout.PaymentResult
import com.razorpay.ExternalWalletListener
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener

class MainActivity : AppCompatActivity(), PaymentResultWithDataListener, ExternalWalletListener {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_VJoyKart)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                ContextCompat.getColor(this, android.R.color.transparent),
                ContextCompat.getColor(this, android.R.color.transparent),
            ),
            navigationBarStyle = SystemBarStyle.light(
                ContextCompat.getColor(this, android.R.color.white),
                ContextCompat.getColor(this, android.R.color.white),
            ),
        )
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.statusBarSpacer.updateLayoutParams { height = bars.top }
            binding.navigationBarSpacer.updateLayoutParams { height = maxOf(bars.bottom, ime.bottom) }
            binding.root.setPadding(bars.left, 0, bars.right, 0)
            WindowInsetsCompat.CONSUMED
        }

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navHost.navController.addOnDestinationChangedListener { _, destination, _ ->
            val splash = destination.id == R.id.splashFragment
            val top = if (splash) R.color.vk_primary else R.color.vk_bg
            val bottom = if (splash) R.color.vk_primary else android.R.color.white
            binding.statusBarSpacer.setBackgroundColor(ContextCompat.getColor(this, top))
            binding.navigationBarSpacer.setBackgroundColor(ContextCompat.getColor(this, bottom))
            binding.root.setBackgroundColor(ContextCompat.getColor(this, if (splash) R.color.vk_primary else R.color.vk_bg))
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !splash
            controller.isAppearanceLightNavigationBars = !splash
        }
    }

    override fun onResume() {
        super.onResume()
        appContainer.session.refreshState()
    }

    // Razorpay delivers checkout results to the hosting Activity.
    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        appContainer.paymentResults.emit(
            PaymentResult.Success(
                paymentId = paymentData?.paymentId ?: razorpayPaymentId,
                orderId = paymentData?.orderId,
                signature = paymentData?.signature,
            ),
        )
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        appContainer.paymentResults.emit(PaymentResult.Error(code, response))
    }

    override fun onExternalWalletSelected(walletName: String?, paymentData: PaymentData?) {
        appContainer.paymentResults.emit(PaymentResult.ExternalWallet(walletName))
    }
}
