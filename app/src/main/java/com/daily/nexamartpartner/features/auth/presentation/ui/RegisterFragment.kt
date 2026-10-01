package com.daily.nexamartpartner.features.auth.presentation.ui

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.databinding.FragmentRegisterBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.RegisterViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.RegisterViewModelFactory
import kotlinx.coroutines.launch

class RegisterFragment : Fragment(R.layout.fragment_register) {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val vm: RegisterViewModel by viewModels { RegisterViewModelFactory(requireContext().appContainer.registerUseCase, requireContext().appContainer.authStateStore) }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view,savedInstanceState); _binding=FragmentRegisterBinding.bind(view)
        binding.nameInput.doAfterTextChanged{vm.onNameChanged(it?.toString().orEmpty())}
        binding.emailInput.doAfterTextChanged{vm.onEmailChanged(it?.toString().orEmpty())}
        binding.phoneInput.doAfterTextChanged{vm.onPhoneChanged(it?.toString().orEmpty())}
        binding.passwordInput.doAfterTextChanged{vm.onPasswordChanged(it?.toString().orEmpty())}
        binding.confirmPasswordInput.doAfterTextChanged{vm.onConfirmPasswordChanged(it?.toString().orEmpty())}
        binding.createAccountButton.setOnClickListener{vm.submit()}
        binding.backToLoginButton.setOnClickListener{findNavController().popBackStack()}
        viewLifecycleOwner.lifecycleScope.launch{viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){vm.uiState.collect{state->
            binding.nameLayout.error=state.nameError; binding.emailLayout.error=state.emailError; binding.phoneLayout.error=state.phoneError; binding.passwordLayout.error=state.passwordError; binding.confirmPasswordLayout.error=state.confirmPasswordError
            binding.createAccountButton.isEnabled=!state.isSubmitting
            binding.createAccountButton.text=if(state.isSubmitting) "Creating account…" else "Create account"
            binding.errorText.text=state.formError.orEmpty(); binding.errorText.visibility=if(state.formError.isNullOrBlank()) View.GONE else View.VISIBLE
        }}}
    }
    override fun onDestroyView(){_binding=null;super.onDestroyView()}
}
