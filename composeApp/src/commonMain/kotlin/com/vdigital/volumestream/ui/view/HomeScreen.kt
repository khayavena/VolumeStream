package com.vdigital.volumestream.ui.view

import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import com.vdigital.volumestream.model.PlaybackMediaItem
import com.vdigital.volumestream.repository.state.ResultState
import com.vdigital.volumestream.ui.viewmodel.HomaPageViewModel
import com.vdigital.volumestream.ui.widget.MediaItemCategoryListWidget
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
fun HomeScreen(
    homaPageViewModel: HomaPageViewModel = koinViewModel(),
    navController: NavHostController
) {
    val state = homaPageViewModel.homeDataUIState.collectAsState()
    when (state.value) {
        is ResultState.Error -> {
            ErrorScreen(onRetry = {
                homaPageViewModel.fetchData()
            })
        }
        ResultState.Loading -> {
            CircularProgressIndicator()
        }
        is ResultState.Success -> {
            val data =
                (state.value as ResultState.Success<Map<String, MutableList<PlaybackMediaItem>>>).data
            MediaItemCategoryListWidget(mediaItemCategories = data, navController = navController)
        }
    }
    LaunchedEffect(Unit) {
        homaPageViewModel.fetchData()
    }
}