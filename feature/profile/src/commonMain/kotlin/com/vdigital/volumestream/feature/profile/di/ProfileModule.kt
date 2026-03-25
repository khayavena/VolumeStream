package com.vdigital.volumestream.feature.profile.di

import com.vdigital.volumestream.ui.viewmodel.ProfileViewModel
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val profileModule = module {
    viewModelOf(::ProfileViewModel)
}

