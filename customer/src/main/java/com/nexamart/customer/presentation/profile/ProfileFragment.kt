package com.nexamart.customer.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nexamart.customer.BuildConfig
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentProfileBinding
import com.nexamart.customer.databinding.ItemProfileTileBinding
import com.nexamart.customer.presentation.common.Nav.openSavedAddresses
import com.nexamart.customer.presentation.common.Nav.openWallet
import com.nexamart.customer.presentation.common.dial
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.presentation.common.openExternal
import com.nexamart.customer.presentation.common.showMessage
import com.nexamart.customer.presentation.common.visibleIf

/** Port of ProfileScreen, plus an Account section (verified mobile number + logout). */
class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = requireContext().appContainer
        tile(binding.walletTile, R.drawable.ic_account_balance_wallet_round, "Wallet & Rewards",
            "View wallet credits, rewards and eligible refunds") { openWallet() }
        tile(binding.addressesTile, R.drawable.ic_location_on_outline, "Saved Addresses",
            "Manage your delivery locations") { openSavedAddresses() }
        tile(binding.aboutTile, R.drawable.ic_info_outline, "About VJoyKart",
            "How VJoyKart helps customers and local shops") { showAbout() }
        tile(binding.termsTile, R.drawable.ic_gavel_outline, "Terms", "Simple rules for using VJoyKart") { showTerms() }
        tile(binding.versionTile, R.drawable.ic_verified_outline, "App Version", "Current customer app version", null)
        binding.versionTile.trailingText.visibleIf(true)
        binding.versionTile.trailingText.text = BuildConfig.VERSION_NAME
        tile(binding.logoutTile, R.drawable.ic_logout, "Logout", "Sign out of this device") { confirmLogout() }

        binding.callUs.setOnClickListener { requireContext().dial(SUPPORT_NUMBER) }
        binding.whatsapp.setOnClickListener {
            val text = android.net.Uri.encode("Hi VJoyKart Support, I need help with my order.")
            requireContext().openExternal("https://wa.me/91$SUPPORT_NUMBER?text=$text", "WhatsApp is not available on this device.")
        }

        launchOnStarted {
            container.session.authenticated.collect { authenticated ->
                binding.accountSection.visibleIf(authenticated)
                val phone = container.session.customerPhone.orEmpty()
                tile(binding.phoneTile, R.drawable.ic_phone_android_round, "Mobile number",
                    if (phone.isEmpty()) "Verified" else "+91 $phone", null)
            }
        }
    }

    private fun tile(
        tile: ItemProfileTileBinding,
        icon: Int,
        title: String,
        subtitle: String,
        onClick: (() -> Unit)?,
    ) {
        tile.icon.setImageResource(icon)
        tile.title.text = title
        tile.subtitle.text = subtitle
        tile.chevron.visibleIf(onClick != null)
        tile.root.isClickable = onClick != null
        tile.root.setOnClickListener(onClick?.let { action -> View.OnClickListener { action() } })
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("You will need to verify your mobile number with an OTP again before placing an order.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Logout") { _, _ ->
                requireContext().appContainer.logout()
                showMessage("You have been logged out.")
            }
            .show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_favorite_round)
            .setTitle("About VJoyKart")
            .setMessage(
                "VJoyKart is built to make everyday shopping easier while helping neighbourhood kirana stores and small local shops reach more customers.\n\n" +
                    "Customers get convenient product discovery, ordering and delivery, while local shops get another digital channel to serve nearby customers.\n\n" +
                    "The goal is simple: make local shopping more convenient without losing the personal connection of neighbourhood stores.",
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showTerms() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("VJoyKart Terms")
            .setMessage(
                "By using VJoyKart, you agree to use the app for genuine shopping and delivery requests.\n\n" +
                    "• Product availability and prices may change.\n" +
                    "• Orders are subject to store availability and service coverage.\n" +
                    "• Customers should provide accurate contact and delivery information.\n" +
                    "• Cash on Delivery orders should be accepted only when the customer is ready to receive them.\n" +
                    "• Online payments are processed through the configured payment gateway.\n" +
                    "• Delivery times are estimates and may vary due to store preparation, traffic or other operational conditions.\n" +
                    "• Refunds, cancellations and payment issues are handled according to the applicable order and payment process.\n\n" +
                    "VJoyKart may update these terms as the service evolves.",
            )
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val SUPPORT_NUMBER = "9959095202"
    }
}
