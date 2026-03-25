package com.vdigital.volumestream.feature.home.di

import com.vdigital.volumestream.ui.viewmodel.DownloadViewModel
import com.vdigital.volumestream.ui.viewmodel.HomaPageViewModel
import com.vdigital.volumestream.ui.viewmodel.SearchViewModel
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val homeModule = module {
    viewModelOf(::HomaPageViewModel)
    viewModelOf(::DownloadViewModel)
    viewModelOf(::SearchViewModel)
}
