package com.nutriscan

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.google.firebase.FirebaseApp

@HiltAndroidApp
class NutriScanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
