package com.nexamart.customer.presentation.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.nexamart.customer.R
import com.nexamart.customer.appContainer
import com.nexamart.customer.databinding.FragmentLoginForOrdersBinding
import com.nexamart.customer.presentation.common.appViewModels
import com.nexamart.customer.presentation.auth.bindOtpLogin
import com.nexamart.customer.presentation.common.launchOnStarted
import com.nexamart.customer.presentation.common.showMessage
import com.nexamart.customer.presentation.main.MainTabsViewModel
import com.nexamart.customer.presentation.main.returnToMainTab
import kotlinx.coroutines.launch

class LoginForOrdersFragment : Fragment() {
    private var _binding: FragmentLoginForOrdersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OtpLoginViewModel by appViewModels { c, _ -> OtpLoginViewModel(c.session) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentLoginForOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.otpView.loginTitle.text = "Please log in to view your orders"
        binding.otpView.loginDescription.text = "Verify your mobile number to open your order history."
        bindOtpLogin(
            binding.otpView,
            viewModel,
            onMessage = ::showMessage,
            onAuthenticated = {
                requireContext().appContainer.appScope.launch { requireContext().appContainer.orders.refresh() }
                returnToMainTab(MainTabsViewModel.TAB_ORDERS)
            },
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
