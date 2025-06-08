// In OracleDrive - Genesisystem/app/src/main/java/com/genesis/ai/app/ipc/AuraDriveServiceImpl.kt
package com.genesis.ai.app.ipc // IMPORTANT: Use OracleDrive's package name here

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.app.ipc.IAuraDriveService // IMPORT THE GENERATED AIDL INTERFACE (from common package)
import com.genesis.ai.app.data.model.LSPosedModuleRequest
// Assuming LSPosedModuleResponse might be used later or for more detailed return, though current AIDL returns String
// import com.genesis.ai.app.data.model.LSPosedModuleResponse
import com.genesis.ai.app.data.model.GenesisRepositoryNew // To make API calls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel // To cancel the scope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking // To make the AIDL method call effectively synchronous for the client
                                  // while handling async work internally. Consider implications.
import retrofit2.awaitResponse // For async API calls

class AuraDriveServiceImpl : Service() {
    private val TAG = "AuraDriveService"
    // Create a service-specific coroutine scope. SupervisorJob allows children to fail independently.
    // Dispatchers.IO is suitable for network or disk operations.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // This is the implementation of the AIDL interface
    private val binder = object : IAuraDriveService.Stub() {
        override fun getOracleDriveStatus(): String {
            Log.d(TAG, "AuraFrameFX requested OracleDrive status.")
            // You can get current status from your internal state, e.g., if container is running
            // This is a synchronous call.
            return "OracleDrive is running in container mode. LSPosed modules are active. (Simulated status)"
        }

        override fun toggleLSPosedModule(packageName: String, enable: Boolean): String {
            Log.d(TAG, "AuraFrameFX requested to toggle LSPosed module: $packageName, enable: $enable. Processing...")

            // The AIDL call itself is synchronous from the client's perspective.
            // We use runBlocking here to wait for the suspend function's result.
            // This means the client (AuraFrameFX) will block until this method returns.
            // This is often desired in AIDL if the client expects an immediate result from the method call.
            // The actual network operation within toggleModuleOnBackend still runs on Dispatchers.IO via serviceScope.
            var resultMessage: String

            // Using runBlocking to bridge the suspend world with the synchronous AIDL method.
            // This is a common pattern for AIDL calls that need to perform async work but return a direct result.
            // Ensure that any long-running operations within toggleModuleOnBackend are cancellable
            // and handle exceptions gracefully.
            runBlocking { // This will block the binder thread until the inner coroutine completes.
                resultMessage = toggleModuleOnBackend(packageName, enable)
            }

            Log.d(TAG, "Toggle request for $packageName processed. Result: $resultMessage")
            return resultMessage
        }
    }

    // Suspend function to handle the actual backend call
    private suspend fun toggleModuleOnBackend(packageName: String, enable: Boolean): String {
        // This function is called within runBlocking from the AIDL method,
        // but its internal operations (the API call) are still async-friendly.
        return try {
            // In a real scenario, you'd get the auth token from OracleDrive's own secure storage
            // or ensure GenesisRepositoryNew is configured with necessary auth interceptors.
            // The issue description for MainActivity's toggleLSPosedModule involved getting a token.
            // For a service, this might mean the service needs a way to access a valid token
            // or the API calls made by GenesisRepositoryNew are already authenticated.
            // For this implementation, we'll assume GenesisRepositoryNew.api handles auth.

            Log.i(TAG, "Calling backend to toggle module $packageName to $enable")
            val request = LSPosedModuleRequest(packageName, enable)

            // Using awaitResponse() to make the Retrofit call suspendable
            val response = GenesisRepositoryNew.api.toggleLSPosedModule(request).awaitResponse()

            if (response.isSuccessful && response.body() != null) {
                val respBody = response.body()!!
                val msg = "Module '${respBody.packageName}' toggled: ${respBody.enabled}. Status: ${respBody.status} (via OracleDrive Service)"
                Log.d(TAG, msg)
                msg
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                val msg = "Failed to toggle module $packageName via backend: ${response.code()} - $errorBody"
                Log.e(TAG, msg)
                msg
            }
        } catch (e: Exception) {
            val msg = "Exception calling backend for module $packageName toggle: ${e.message}"
            Log.e(TAG, msg, e)
            msg
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "AuraDriveService onBind received. Returning binder.")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AuraDriveService onCreate.")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all coroutines started by this service scope when the service is destroyed.
        serviceScope.cancel()
        Log.d(TAG, "AuraDriveService onDestroy. Coroutine scope cancelled.")
    }
}
