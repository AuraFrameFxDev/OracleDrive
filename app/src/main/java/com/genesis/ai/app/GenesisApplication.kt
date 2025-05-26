package com.genesis.ai.app

import android.app.Application
import com.genesis.ai.app.service.GenesisAIService
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance

class GenesisApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase with existing catalog
        FirebaseApp.initializeApp(
            this, FirebaseOptions.Builder()
                .setDatabaseUrl("https://your-project.firebaseio.com")
                .setStorageBucket("your-project.appspot.com")
                .build()
        )

        // Initialize Crashlytics
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true

        // Initialize Performance Monitoring
        FirebasePerformance.getInstance().isPerformanceCollectionEnabled = true

        // Initialize Firebase Auth
        FirebaseAuth.getInstance().initialize(this)

        // Initialize Firebase Storage
        FirebaseStorage.getInstance().initialize(this)

        // Initialize Firebase Analytics
        FirebaseAnalytics.getInstance(this).setUserProperty("app_version", BuildConfig.VERSION_NAME)

        // Start the AI service when the application starts
        GenesisAIService.startService(this)
    }
}
