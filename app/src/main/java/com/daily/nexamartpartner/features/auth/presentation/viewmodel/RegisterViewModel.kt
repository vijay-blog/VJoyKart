package com.daily.nexamartpartner.features.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.features.auth.domain.model.RegistrationCredentials
import com.daily.nexamartpartner.features.auth.domain.usecase.RegisterUseCase
import com.daily.nexamartpartner.features.auth.presentation.state.AuthStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

 data class RegisterUiState(
    val name: String = "", val email: String = "", val phone: String = "", val password: String = "", val confirmPassword: String = "",
    val isSubmitting: Boolean = false, val nameError: String? = null, val emailError: String? = null, val phoneError: String? = null,
    val passwordError: String? = null, val confirmPasswordError: String? = null, val formError: String? = null
)

class RegisterViewModel(private val useCase: RegisterUseCase, private val authStateStore: AuthStateStore) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()
    fun onNameChanged(v: String) = _uiState.update { it.copy(name=v, nameError=null, formError=null) }
    fun onEmailChanged(v: String) = _uiState.update { it.copy(email=v, emailError=null, formError=null) }
    fun onPhoneChanged(v: String) = _uiState.update { it.copy(phone=v, phoneError=null, formError=null) }
    fun onPasswordChanged(v: String) = _uiState.update { it.copy(password=v, passwordError=null, confirmPasswordError=null, formError=null) }
    fun onConfirmPasswordChanged(v: String) = _uiState.update { it.copy(confirmPassword=v, confirmPasswordError=null, formError=null) }
    fun submit() {
        val c=_uiState.value
        if(c.isSubmitting) return
        val nameErr=if(c.name.isBlank()) "Please enter your name." else null
        val emailErr=if(!android.util.Patterns.EMAIL_ADDRESS.matcher(c.email.trim()).matches()) "Please enter a valid email address." else null
        val normalizedPhone=c.phone.filter{it.isDigit()||it=='+'}
        val phoneErr=if(!Regex("^\\+?[0-9]{10,13}$").matches(normalizedPhone)) "Please enter a valid mobile number." else null
        val passErr=if(c.password.length<8) "Password must contain at least 8 characters." else null
        val confirmErr=if(c.confirmPassword!=c.password) "Passwords do not match." else null
        if(listOf(nameErr,emailErr,phoneErr,passErr,confirmErr).any{it!=null}) { _uiState.update{it.copy(nameError=nameErr,emailError=emailErr,phoneError=phoneErr,passwordError=passErr,confirmPasswordError=confirmErr)}; return }
        _uiState.update{it.copy(isSubmitting=true,formError=null)}
        viewModelScope.launch {
            when(val r=useCase(RegistrationCredentials(c.name.trim(),c.email.trim(),c.password,normalizedPhone))) {
                is AppResult.Success -> { _uiState.update{it.copy(isSubmitting=false)}; authStateStore.setAuthenticated(r.data) }
                is AppResult.Failure -> _uiState.update{it.copy(isSubmitting=false,formError=r.error.message)}
            }
        }
    }
}

class RegisterViewModelFactory(private val useCase: RegisterUseCase, private val authStateStore: AuthStateStore): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T = if(modelClass.isAssignableFrom(RegisterViewModel::class.java)) RegisterViewModel(useCase,authStateStore) as T else throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
}
