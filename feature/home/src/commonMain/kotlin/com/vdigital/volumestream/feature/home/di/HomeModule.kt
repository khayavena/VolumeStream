package com.vdigital.volumestream.feature.home.di

import com.vdigital.volumestream.ui.viewmodel.HomaPageViewModel
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val homeModule = module {
    viewModelOf(::HomaPageViewModel)
}
