package com.example.track

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.track.ui.theme.TrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val trackingViewModel: TrackViewModel = viewModel(
                factory = TrackViewModel.Factory((application as TrackApplication).repository),
            )
            val tracking by trackingViewModel.tracking.collectAsStateWithLifecycle()
            TrackTheme {
                TrackApp(
                    sessionData = tracking,
                    onAddFood = trackingViewModel::addFood,
                    onAddWorkout = trackingViewModel::addWorkout,
                    onAddWater = trackingViewModel::addWater,
                    onCreatineToggle = trackingViewModel::toggleCreatine,
                )
            }
        }
    }
}
