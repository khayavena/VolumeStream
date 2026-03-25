package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vditital.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {

    /** The email of the currently signed-in user, or null if unavailable. */
    val userEmail: String? = authRepository.getCurrentUserEmail()

    private val _signedOut = MutableStateFlow(false)
    /** Emits `true` once when the user has signed out; the UI observes this to navigate away. */
    val signedOut = _signedOut.asStateFlow()

    fun signOut() {
        authRepository.logout()
        _signedOut.value = true
    }
}

