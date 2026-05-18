package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vditital.data.repository.AuthRepository
import com.vditital.data.repository.ProfileRepository
import com.vditital.data.repository.state.ResultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    /** The email of the currently signed-in user, or null while loading / unavailable. */
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail = _userEmail.asStateFlow()

    private val _signedOut = MutableStateFlow(false)
    /** Emits `true` once when the user has signed out; the UI observes this to navigate away. */
    val signedOut = _signedOut.asStateFlow()

    private val _fullName = MutableStateFlow("")
    val fullName = _fullName.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone = _phone.asStateFlow()

    private val _address = MutableStateFlow("")
    val address = _address.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    init {
        viewModelScope.launch {
            _userEmail.value = withContext(Dispatchers.Default) {
                authRepository.getCurrentUserEmail()
            }
            refreshProfile()
        }
    }

    fun onFullNameChanged(value: String) {
        _fullName.value = value
    }

    fun onPhoneChanged(value: String) {
        _phone.value = value
    }

    fun onAddressChanged(value: String) {
        _address.value = value
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val result = withContext(Dispatchers.Default) { profileRepository.loadMyProfile() }) {
                is ResultState.Success -> {
                    _fullName.value = result.data.fullName
                    _phone.value = result.data.phone
                    _address.value = result.data.address
                }
                is ResultState.Error -> {
                    if (result.exception !is NoSuchElementException) {
                        _error.value = result.exception.message ?: "Failed to load profile"
                    }
                }
                ResultState.Loading -> Unit
            }
            _isLoading.value = false
        }
    }

    fun saveProfile() {
        if (_fullName.value.isBlank()) {
            _error.value = "Full name is required"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            _status.value = null

            when (
                val result = withContext(Dispatchers.Default) {
                    profileRepository.saveMyProfile(
                        fullName = _fullName.value.trim(),
                        phone = _phone.value.trim(),
                        address = _address.value.trim(),
                    )
                }
            ) {
                is ResultState.Success -> {
                    _fullName.value = result.data.fullName
                    _phone.value = result.data.phone
                    _address.value = result.data.address
                    _status.value = "Profile saved"
                }
                is ResultState.Error -> {
                    _error.value = result.exception.message ?: "Failed to save profile"
                }
                ResultState.Loading -> Unit
            }
            _isSaving.value = false
        }
    }

    fun upgradeToAdmin() {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            _status.value = null

            when (val result = withContext(Dispatchers.Default) { profileRepository.upgradeCurrentUserToAdmin() }) {
                is ResultState.Success -> _status.value = result.data
                is ResultState.Error -> _error.value = result.exception.message ?: "Upgrade failed"
                ResultState.Loading -> Unit
            }
            _isSaving.value = false
        }
    }

    fun testTokenRefresh() {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            _status.value = null

            val jwt = withContext(Dispatchers.Default) { authRepository.forceRefreshJwt() }
            if (jwt.isNullOrBlank()) {
                _error.value = "Token refresh failed. Please sign in again."
            } else {
                _status.value = "Token refresh succeeded"
            }

            _isSaving.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { authRepository.logout() }
            _signedOut.value = true
        }
    }
}
