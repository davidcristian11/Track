package com.example.track

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.track.ui.theme.TrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val trackApplication = application as TrackApplication
            val trackViewModel: TrackViewModel = viewModel(
                factory = TrackViewModel.Factory(
                    trackApplication.repository, trackApplication.settingsRepository, trackApplication.foodLookupRepository,
                ),
            )
            TrackTheme {
                TrackApp(trackViewModel)
            }
        }
    }
}
