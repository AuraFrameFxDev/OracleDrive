package com.genesis.ai.app

import android.app.Application
import com.genesis.ai.app.data.logging.OracleDriveLogger // ADDED IMPORT
import com.genesis.ai.app.service.GenesisAIService
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.FirebaseOptions // Assuming this was used by original, from read_files context
// BuildConfig should be available from the module (e.g., com.genesis.ai.app.BuildConfig)

class GenesisApplication : Application() {

    lateinit var oracleDriveLogger: OracleDriveLogger // ADDED

    override fun onCreate() {
        super.onCreate()

        // Initialize the logger early in the application lifecycle
        oracleDriveLogger = OracleDriveLogger(applicationContext)
        oracleDriveLogger.i("Application", "OracleDrive Application Instance Created. Logger Initialized.")

        try {
            oracleDriveLogger.i("Application", "Attempting Firebase initialization block...")

            // --- Start of block attempting to mirror read_files for Firebase ---
            if (FirebaseApp.getApps(this).isEmpty()) {
                 FirebaseApp.initializeApp(
                    this, FirebaseOptions.Builder()
                        .setDatabaseUrl("https://your-project.firebaseio.com") // From original read_files
                        .setStorageBucket("your-project.appspot.com") // From original read_files
                        // .setApplicationId(packageName) // Example: usually needed for manual init
                        .build()
                )
                oracleDriveLogger.i("Application", "FirebaseApp.initializeApp with options called.")
            } else {
                oracleDriveLogger.i("Application", "FirebaseApp already initialized or default init used.")
            }

            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
            oracleDriveLogger.i("Application", "Firebase Crashlytics collection enabled.")

            FirebasePerformance.getInstance().isPerformanceCollectionEnabled = true
            oracleDriveLogger.i("Application", "Firebase Performance collection enabled.")

            // Original read_files showed: FirebaseAuth.getInstance().initialize(this)
            // This is not a standard Firebase method. Logging standard access instead.
            FirebaseAuth.getInstance()
            oracleDriveLogger.i("Application", "FirebaseAuth instance accessed.")

            // Original read_files showed: FirebaseStorage.getInstance().initialize(this)
            // This is not a standard Firebase method. Logging standard access instead.
            FirebaseStorage.getInstance()
            oracleDriveLogger.i("Application", "FirebaseStorage instance accessed.")

            FirebaseAnalytics.getInstance(this).setUserProperty("app_version", BuildConfig.VERSION_NAME)
            oracleDriveLogger.i("Application", "Firebase Analytics user property 'app_version' set to ${BuildConfig.VERSION_NAME}.")
            // --- End of Firebase block ---
            oracleDriveLogger.i("Application", "Firebase components initialization block finished.")

        } catch (e: Exception) {
            oracleDriveLogger.e("Application", "Error during Firebase initialization: ${e.message}", e)
        }

        try {
            oracleDriveLogger.i("Application", "Attempting to start GenesisAIService...")
            GenesisAIService.startService(this)
            oracleDriveLogger.i("Application", "GenesisAIService.startService(this) called.")
        } catch (e: Exception) {
            oracleDriveLogger.e("Application", "Error starting GenesisAIService: ${e.message}", e)
        }
        oracleDriveLogger.i("Application", "GenesisApplication.onCreate completed.")
    }

    override fun onTerminate() {
        super.onTerminate()
        oracleDriveLogger.i("Application", "OracleDrive Application Terminating.")
        oracleDriveLogger.shutdown()
    }
}
