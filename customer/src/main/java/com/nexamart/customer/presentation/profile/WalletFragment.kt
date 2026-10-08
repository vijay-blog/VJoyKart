package com.nexamart.customer.presentation.profile

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.nexamart.customer.R
import com.nexamart.customer.databinding.FragmentWalletBinding

/** Port of WalletScreen (informational; no money movement from the app). */
class WalletFragment : Fragment(R.layout.fragment_wallet) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentWalletBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }
}
