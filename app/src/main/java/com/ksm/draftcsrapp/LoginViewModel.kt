package com.ksm.draftcsrapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    fun performLogin(username: String, pass: String) {
        _loginState.value = LoginUiState.Loading
        viewModelScope.launch {
            repository.login(LoginRequest(username, pass)).collect { result ->
                result.fold(
                    onSuccess = { _loginState.value = LoginUiState.Success },
                    onFailure = { error ->
                        _loginState.value = LoginUiState.Error(error.message ?: "Terjadi kesalahan")
                    }
                )
            }
        }
    }
}

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}