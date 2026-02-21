package com.nutriscan

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.google.firebase.FirebaseApp

import com.nutriscan.ui.social.TrendingScoreWorker

@HiltAndroidApp
class NutriScanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        TrendingScoreWorker.schedulePeriodicUpdates(context = this)
    }
}
