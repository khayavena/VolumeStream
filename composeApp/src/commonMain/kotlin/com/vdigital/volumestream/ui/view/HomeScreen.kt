package com.vdigital.volumestream.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vdigital.volumestream.model.PlaybackMediaItem
import com.vdigital.volumestream.repository.state.ResultState
import com.vdigital.volumestream.ui.viewmodel.HomaPageViewModel
import com.vdigital.volumestream.ui.widget.MediaItemCategoryListWidget
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
@Composable
fun HomeScreen(homaPageViewModel: HomaPageViewModel = koinViewModel()) {
    val state = homaPageViewModel.homeDataUIState.collectAsState()
    when (state.value) {
        is ResultState.Error -> Column {
            Text("Error Screen", modifier = Modifier.padding(16.dp))
        }

        ResultState.Loading -> CircularProgressIndicator()
        is ResultState.Success -> {
            val data =
                (state.value as ResultState.Success<Map<String, MutableList<PlaybackMediaItem>>>).data
            MediaItemCategoryListWidget(data)
        }
    }
    LaunchedEffect(Unit) {
        homaPageViewModel.fetchData()
    }
}