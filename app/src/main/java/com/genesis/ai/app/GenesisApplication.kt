package com.genesis.ai.app

import android.app.Application
import com.genesis.ai.app.service.GenesisAIService

class GenesisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start the AI service when the application starts
        GenesisAIService.startService(this)
    }
}
