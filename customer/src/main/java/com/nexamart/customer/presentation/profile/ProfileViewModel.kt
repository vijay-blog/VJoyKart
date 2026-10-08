package com.nexamart.customer.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexamart.customer.core.ApiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AccountDeletionState {
    data object Idle : AccountDeletionState
    data object Deleting : AccountDeletionState
    data object Deleted : AccountDeletionState
    data class Error(val message: String) : AccountDeletionState
}

class ProfileViewModel(
    private val deleteAccountRequest: suspend () -> Unit,
) : ViewModel() {
    private val _deletion = MutableStateFlow<AccountDeletionState>(AccountDeletionState.Idle)
    val deletion: StateFlow<AccountDeletionState> = _deletion.asStateFlow()
    private var deletionJob: Job? = null

    fun deleteAccount() {
        if (deletionJob?.isActive == true) return
        deletionJob = viewModelScope.launch {
            _deletion.value = AccountDeletionState.Deleting
            try {
                deleteAccountRequest()
                _deletion.value = AccountDeletionState.Deleted
            } catch (error: ApiException) {
                _deletion.value = AccountDeletionState.Error(error.message ?: "Unable to delete your account. Please try again.")
            } catch (error: Exception) {
                _deletion.value = AccountDeletionState.Error("Unable to delete your account. Please try again.")
            }
        }
    }

    fun clearError() {
        if (_deletion.value is AccountDeletionState.Error) _deletion.value = AccountDeletionState.Idle
    }
}
