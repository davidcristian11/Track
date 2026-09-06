package com.example.track

import android.app.Application
import androidx.room.Room

class TrackApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, TrackDatabase::class.java, "track.db").build()
    }
    val repository by lazy { TrackRepository(database) }
}
