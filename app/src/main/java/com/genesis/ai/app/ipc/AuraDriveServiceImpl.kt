package com.genesis.ai.app.ipc

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Build // ADDED IMPORT
import com.example.app.ipc.IAuraDriveService
import com.genesis.ai.app.data.model.LSPosedModuleRequest
import com.genesis.ai.app.data.model.GenesisRepositoryNew
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext // ADDED IMPORT
import retrofit2.awaitResponse
import com.genesis.ai.app.GenesisApplication
import com.genesis.ai.app.data.logging.OracleDriveLogger
import com.google.gson.Gson // ADDED IMPORT
import com.google.gson.JsonObject // ADDED IMPORT
import java.io.File // ADDED IMPORT for checkRootStatus

class AuraDriveServiceImpl : Service() {
    private val TAG = "AuraDriveService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var oracleDriveLogger: OracleDriveLogger

    override fun onCreate() {
        super.onCreate()
        oracleDriveLogger = (application as GenesisApplication).oracleDriveLogger
        oracleDriveLogger.i(TAG, "AuraDriveService onCreate.")
    }

    private val binder = object : IAuraDriveService.Stub() {
        override fun getOracleDriveStatus(): String {
            oracleDriveLogger.d(TAG, "AIDL call: getOracleDriveStatus (enhanced) requested.")
            val isRooted = checkRootStatus()
            val backendStatus = try {
                // A light check, not a full API call if not necessary
                if (GenesisRepositoryNew.api != null) "Available" else "Unavailable"
            } catch (e: Exception) {
                "Error checking backend"
            }
            return "OracleDrive Active. Container Root: ${if (isRooted) "YES" else "NO"}. Backend: $backendStatus."
        }

        override fun toggleLSPosedModule(packageName: String, enable: Boolean): String {
            oracleDriveLogger.d(TAG, "AIDL call: toggleLSPosedModule requested by AuraFrameFX for package '$packageName', enable: $enable.")
            var resultMessage: String
            runBlocking { // Blocks the binder thread
                resultMessage = toggleModuleOnBackend(packageName, enable)
            }
            oracleDriveLogger.d(TAG, "AIDL call: toggleLSPosedModule for '$packageName' processed. Result: $resultMessage")
            return resultMessage // This is the immediate response.
        }

        override fun getDetailedInternalStatus(): String {
            oracleDriveLogger.d(TAG, "AIDL call: getDetailedInternalStatus requested.")
            val appVersionName = try {
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName
            } catch (e: Exception) {
                oracleDriveLogger.w(TAG, "Could not get app version name: ${e.message}", e)
                "Unknown"
            }

            val jsonObject = JsonObject().apply {
                addProperty("timestamp", System.currentTimeMillis())
                addProperty("oracleDriveAppVersion", appVersionName)
                addProperty("isRooted", checkRootStatus()) // Internal helper
                addProperty("containerOSVersion", Build.VERSION.RELEASE)
                addProperty("containerSDKVersion", Build.VERSION.SDK_INT)
                val backendStatus = try {
                    if (GenesisRepositoryNew.api != null) "Available" else "Unavailable"
                } catch (e: Exception) { "Error" }
                addProperty("backendConnection", backendStatus)
                // TODO: Add actual LSPosed status (e.g., if LSPosed is running, number of active modules)
                // This would require executing root commands here or querying LSPosed's internal state.
                addProperty("lsposedStatus", "Active (simulated, root check needed for real status)")
                addProperty("activeLSPosedModulesCount", "0 (simulated, needs implementation)")
            }
            val jsonString = Gson().toJson(jsonObject)
            oracleDriveLogger.i(TAG, "Detailed internal status generated: $jsonString")
            return jsonString
        }

        /**
         * Retrieves the current day's internal diagnostics log as a string.
         *
         * Returns the log contents if available, or an error message if log retrieval fails.
         */
        override fun getInternalDiagnosticsLog(): String {
            oracleDriveLogger.d(TAG, "AIDL call: getInternalDiagnosticsLog requested.")
            var logs = "Error reading logs." // Default error message
            try {
                // readCurrentDayLogs is a suspend function, so call it within a coroutine context.
                // runBlocking is used here because AIDL calls are synchronous from client's perspective.
                runBlocking {
                    logs = oracleDriveLogger.readCurrentDayLogs()
                }
                oracleDriveLogger.i(TAG, "Internal diagnostics log retrieved. Length: ${logs.length}")
            } catch (e: Exception) {
                oracleDriveLogger.e(TAG, "Error in getInternalDiagnosticsLog while calling readCurrentDayLogs: ${e.message}", e)
                logs = "Exception reading logs: ${e.message}"
            }
            return logs
        }

        /**
         * Attempts to install a root `su` binary and extract the LSPosed framework on the device.
         *
         * Installs the `su` binary to `/system/xbin/su` with appropriate permissions using root commands,
         * and extracts the LSPosed framework ZIP file (actual LSPosed installation is not performed).
         * Returns a status message indicating success, failure, or any exception encountered.
         *
         * @return A message describing the result of the installation attempt.
         */
        override fun installRootAndLSPosed(): String {
            oracleDriveLogger.i(TAG, "AIDL call: installRootAndLSPosed requested.")
            return try {
                // Extract and install su binary
                val suFile = com.genesis.ai.app.utils.RootInstaller.extractSuBinary(applicationContext)
                val suInstallCmds = listOf(
                    "cp ${suFile.absolutePath} /system/xbin/su",
                    "chmod 0755 /system/xbin/su",
                    "chown 0:0 /system/xbin/su"
                )
                val suResult = com.genesis.ai.app.utils.ShellUtils.runAsRoot(suInstallCmds)
                oracleDriveLogger.i(TAG, "su binary install result: $suResult")

                // Extract and install LSPosed framework (placeholder logic)
                val lsposedZip = com.genesis.ai.app.utils.RootInstaller.extractLSPosedFramework(applicationContext)
                // In production, you would flash the ZIP or use LSPosed's installer logic
                // Here, just log extraction for demonstration
                oracleDriveLogger.i(TAG, "LSPosed framework extracted to: ${lsposedZip.absolutePath}")

                if (suResult) {
                    "Root and LSPosed installation initiated. (LSPosed install is placeholder, see logs)"
                } else {
                    "Failed to install su binary. See logs."
                }
            } catch (e: Exception) {
                oracleDriveLogger.e(TAG, "Error in installRootAndLSPosed: ${e.message}", e)
                "Exception during install: ${e.message}"
            }
        }
    }

    // Suspend function to handle the actual backend call for toggleLSPosedModule
    private suspend fun toggleModuleOnBackend(packageName: String, enable: Boolean): String {
        return try {
            oracleDriveLogger.i(TAG, "Calling backend (from toggleModuleOnBackend) to toggle module $packageName to $enable")
            val request = LSPosedModuleRequest(packageName, enable)
            val response = GenesisRepositoryNew.api.toggleLSPosedModule(request).awaitResponse()

            if (response.isSuccessful && response.body() != null) {
                val respBody = response.body()!!
                val msg = "Module '${respBody.packageName}' toggled: ${respBody.enabled}. Status: ${respBody.status} (via OracleDrive Service backend call)"
                oracleDriveLogger.i(TAG, msg)
                msg
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                val msg = "Failed to toggle module $packageName via backend: ${response.code()} - $errorBody"
                oracleDriveLogger.e(TAG, msg)
                msg
            }
        } catch (e: Exception) {
            val msg = "Exception calling backend for module $packageName toggle: ${e.message}"
            oracleDriveLogger.e(TAG, msg, e)
            msg
        }
    }

    // Helper to check for root status (simple check, could be more robust)
    private fun checkRootStatus(): Boolean {
        oracleDriveLogger.d(TAG, "Performing checkRootStatus().")
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su",
            "/su/bin/su" // Magisk
        )
        for (path in paths) {
            if (File(path).exists()) {
                oracleDriveLogger.i(TAG, "Root check: Found 'su' binary at $path.")
                return true
            }
        }
        oracleDriveLogger.i(TAG, "Root check: 'su' binary not found in common paths.")
        return false
    }

    override fun onBind(intent: Intent?): IBinder {
        oracleDriveLogger.i(TAG, "AuraDriveService onBind received. Returning binder. Intent: $intent")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        oracleDriveLogger.i(TAG, "AuraDriveService onDestroy. Coroutine scope cancelled.")
    }
}
