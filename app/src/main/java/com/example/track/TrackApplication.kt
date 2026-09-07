package com.example.track

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room

private val Context.trackSettings by preferencesDataStore(name = "track_settings")

class TrackApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, TrackDatabase::class.java, "track.db").build()
    }
    val foodLookupRepository by lazy { FoodLookupRepository() }
    val repository by lazy { TrackRepository(database) }
    val settingsRepository by lazy { TrackSettingsRepository(trackSettings) }
}
