package com.vdigital.volumestream.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.vdigital.volumestream.ui.viewmodel.DownloadViewModel
import com.vdigital.volumestream.ui.viewmodel.HomaPageViewModel
import com.vdigital.volumestream.ui.widget.MediaItemCategoryListView
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.repository.state.ResultState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
fun HomeScreen(
    homaPageViewModel: HomaPageViewModel = koinViewModel(),
    downloadViewModel: DownloadViewModel = koinViewModel(),
    navController: NavHostController
) {
    // Trigger fetch on first composition
    LaunchedEffect(Unit) { homaPageViewModel.fetchData() }

    val state = homaPageViewModel.homeDataUIState.collectAsState()
    when (val s = state.value) {
        is ResultState.Error -> ErrorScreen(
            title = "Could not load content",
            message = s.exception.message ?: "Please try again.",
            onRetry = { homaPageViewModel.fetchData() }
        )
        ResultState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        is ResultState.Success -> {
            val data = s.data
            if (data.isEmpty()) {
                ErrorScreen(
                    title = "No content available",
                    message = "Check back later.",
                    onRetry = { homaPageViewModel.fetchData() }
                )
            } else {
                MediaItemCategoryListView(
                    mediaItemCategories = data,
                    navController = navController,
                    downloadViewModel = downloadViewModel
                )
            }
        }
    }
}
