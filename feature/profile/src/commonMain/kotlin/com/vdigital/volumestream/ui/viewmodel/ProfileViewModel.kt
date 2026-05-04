package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vditital.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {

    /** The email of the currently signed-in user, or null while loading / unavailable. */
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail = _userEmail.asStateFlow()

    private val _signedOut = MutableStateFlow(false)
    /** Emits `true` once when the user has signed out; the UI observes this to navigate away. */
    val signedOut = _signedOut.asStateFlow()

    init {
        // Read from EncryptedSharedPreferences off the Main thread — first-access
        // key derivation involves AES crypto that must not block the UI thread.
        viewModelScope.launch {
            _userEmail.value = withContext(Dispatchers.IO) {
                authRepository.getCurrentUserEmail()
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            // clearAll() writes to EncryptedSharedPreferences — keep off Main.
            withContext(Dispatchers.IO) { authRepository.logout() }
            _signedOut.value = true
        }
    }
}
