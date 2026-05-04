package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vditital.data.repository.AuthRepository
import com.vditital.data.repository.state.ResultState
import com.vditital.data.security.SessionRevokedBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AuthUiState {
    data object Idle           : AuthUiState()
    data object Loading        : AuthUiState()
    data object Success        : AuthUiState()
    /** Emitted when the server revokes the session (401 TOKEN_INVALID). */
    data object SessionRevoked : AuthUiState()
    data class  Error(val message: String) : AuthUiState()
}

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState = _uiState.asStateFlow()

    init {
        // Listen for server-side session revocation (e.g. 401 TOKEN_INVALID).
        // Clears the stored JWT via logout() and surfaces SessionRevoked so the
        // UI can navigate to login and stop retrying with the dead token.
        viewModelScope.launch {
            SessionRevokedBus.events.collect {
                withContext(Dispatchers.IO) { authRepository.logout() }
                _uiState.value = AuthUiState.SessionRevoked
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all fields.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = withContext(Dispatchers.IO) { authRepository.login(email, password) }
            _uiState.value = when (result) {
                is ResultState.Success -> AuthUiState.Success
                is ResultState.Error   -> AuthUiState.Error(
                    result.exception.message ?: "Login failed. Check your credentials."
                )
                ResultState.Loading    -> AuthUiState.Idle
            }
        }
    }

    fun register(email: String, password: String, confirmPassword: String) {
        when {
            email.isBlank() || password.isBlank() ->
                _uiState.value = AuthUiState.Error("Please fill in all fields.")
            password != confirmPassword ->
                _uiState.value = AuthUiState.Error("Passwords do not match.")
            password.length < 6 ->
                _uiState.value = AuthUiState.Error("Password must be at least 6 characters.")
            else -> viewModelScope.launch {
                _uiState.value = AuthUiState.Loading
                val result = withContext(Dispatchers.IO) { authRepository.register(email, password) }
                _uiState.value = when (result) {
                    is ResultState.Success -> AuthUiState.Success
                    is ResultState.Error   -> AuthUiState.Error(
                        result.exception.message ?: "Registration failed. Please try again."
                    )
                    ResultState.Loading    -> AuthUiState.Idle
                }
            }
        }
    }

    fun resetState() { _uiState.value = AuthUiState.Idle }
}
