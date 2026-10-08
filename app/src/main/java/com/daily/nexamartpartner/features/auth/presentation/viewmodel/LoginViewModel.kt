package com.daily.nexamartpartner.features.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.nexamartpartner.core.result.AppResult
import com.daily.nexamartpartner.core.result.FailureType
import com.daily.nexamartpartner.features.auth.domain.model.LoginCredentials
import com.daily.nexamartpartner.features.auth.domain.usecase.LoginUseCase
import com.daily.nexamartpartner.features.auth.presentation.state.AuthStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val identifier: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val identifierError: String? = null,
    val passwordError: String? = null,
    val formError: String? = null
)

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val authStateStore: AuthStateStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onIdentifierChanged(value: String) {
        _uiState.update {
            it.copy(
                identifier = value,
                identifierError = null,
                formError = null
            )
        }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = null,
                formError = null
            )
        }
    }

    fun submitLogin() {
        val current = _uiState.value
        if (current.isSubmitting) return

        var identifierError: String? = null
        var passwordError: String? = null
        if (current.identifier.isBlank()) {
            identifierError = "Please enter your phone or email."
        }
        if (current.password.isBlank()) {
            passwordError = "Please enter your password."
        }
        if (identifierError != null || passwordError != null) {
            _uiState.update {
                it.copy(
                    identifierError = identifierError,
                    passwordError = passwordError
                )
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            when (
                val result = loginUseCase(
                    LoginCredentials(
                        identifier = current.identifier.trim(),
                        password = current.password
                    )
                )
            ) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, formError = null) }
                    authStateStore.setAuthenticated(result.data)
                }

                is AppResult.Failure -> {
                    _uiState.update { it.copy(isSubmitting = false, formError = result.error.message) }
                    if (result.error.type == FailureType.UNSUPPORTED_ROLE) {
                        authStateStore.setUnsupportedRole(result.error.message)
                    }
                }
            }
        }
    }
}
