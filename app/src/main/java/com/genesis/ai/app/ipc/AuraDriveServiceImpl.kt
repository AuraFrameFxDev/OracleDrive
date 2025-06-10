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
            runBlocking {
                if (!isValidModuleName(packageName)) {
                    resultMessage = "Invalid module name: '$packageName'. Allowed: a-z, A-Z, 0-9, ., _, -"
                } else {
                    // Try to enable/disable locally first
                    val localResult = setModuleEnabled(packageName, enable)
                    if (localResult) {
                        resultMessage = "Module '$packageName' ${if (enable) "enabled" else "disabled"} locally."
                    } else {
                        resultMessage = "Failed to ${if (enable) "enable" else "disable"} module '$packageName' locally. Check root permissions or file system access."
                    }
                }
            }
            oracleDriveLogger.d(TAG, "AIDL call: toggleLSPosedModule for '$packageName' processed. Result: $resultMessage")
            return resultMessage
        }

        // New method to remove a module
        fun removeLSPosedModule(moduleName: String): String {
            return if (!isValidModuleName(moduleName)) {
                "Invalid module name: '$moduleName'. Allowed: a-z, A-Z, 0-9, ., _, -"
            } else if (removeModule(moduleName)) {
                "Module '$moduleName' removed successfully."
            } else {
                "Failed to remove module '$moduleName'. Check root permissions or file system access."
            }
        }

        override fun getDetailedInternalStatus(): String {
            oracleDriveLogger.d(TAG, "AIDL call: getDetailedInternalStatus requested.")
            val appVersionName = try {
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName
            } catch (e: Exception) {
                oracleDriveLogger.w(TAG, "Could not get app version name: ${e.message}", e)
                "Unknown"
            }

            val lsposedRunning = isLSPosedRunning()
            val moduleStates = getLSPosedModuleStates()
            val enabledModules = moduleStates.filterValues { it }.keys

            val jsonObject = JsonObject().apply {
                addProperty("timestamp", System.currentTimeMillis())
                addProperty("oracleDriveAppVersion", appVersionName)
                addProperty("isRooted", checkRootStatus())
                addProperty("containerOSVersion", Build.VERSION.RELEASE)
                addProperty("containerSDKVersion", Build.VERSION.SDK_INT)
                val backendStatus = try {
                    if (GenesisRepositoryNew.api != null) "Available" else "Unavailable"
                } catch (e: Exception) { "Error" }
                addProperty("backendConnection", backendStatus)
                addProperty("lsposedStatus", if (lsposedRunning) "Active" else "Not running")
                addProperty("activeLSPosedModulesCount", enabledModules.size)
                add("moduleStates", Gson().toJsonTree(moduleStates))
            }
            val jsonString = Gson().toJson(jsonObject)
            oracleDriveLogger.i(TAG, "Detailed internal status generated: $jsonString")
            return jsonString
        }

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

                // Extract and install LSPosed framework
                val lsposedZip = com.genesis.ai.app.utils.RootInstaller.extractLSPosedFramework(applicationContext)
                oracleDriveLogger.i(TAG, "LSPosed framework extracted to: ${lsposedZip.absolutePath}")

                // Deploy the sample LSPosed module
                val moduleResult = com.genesis.ai.app.utils.ModuleDeployer.deploySampleModule(applicationContext)
                oracleDriveLogger.i(TAG, "Sample LSPosed module deployment result: $moduleResult")

                if (suResult && moduleResult) {
                    "Root, LSPosed framework, and sample module installation completed. (LSPosed framework flash is simulated, see logs)"
                } else if (!suResult) {
                    "Failed to install su binary. See logs."
                } else if (!moduleResult) {
                    "LSPosed framework extracted, but failed to deploy sample module. See logs."
                } else {
                    "Unknown error during LSPosed installation. See logs."
                }
            } catch (e: Exception) {
                oracleDriveLogger.e(TAG, "Error in installRootAndLSPosed: ${e.message}", e)
                "Exception during install: ${e.message}"
            }
        }

        // Checks if LSPosed is running by looking for its process or files
        private fun isLSPosedRunning(): Boolean {
            // Check for LSPosed process
            val processCheck = com.genesis.ai.app.utils.ShellUtils.runCommand("ps | grep lspd")
            val socketExists = File("/dev/socket/lsposed").exists()
            val lsposedDir = File("/data/adb/lspd")
            return processCheck.contains("lspd") || socketExists || lsposedDir.exists()
        }

        // Returns a map of installed LSPosed modules and their enabled/disabled state
        private fun getLSPosedModuleStates(): Map<String, Boolean> {
            val modulesDir = File("/data/adb/modules")
            val modules = modulesDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            return modules.associate { it.name to File(it, "enable").exists() }
        }

        // Validate module name to prevent path traversal or injection
        private fun isValidModuleName(name: String): Boolean {
            return name.matches(Regex("^[a-zA-Z0-9._-]+$"))
        }

        // Removes a module by deleting its directory
        private fun removeModule(moduleName: String): Boolean {
            val moduleDir = File("/data/adb/modules/$moduleName")
            return try {
                moduleDir.deleteRecursively()
            } catch (e: Exception) {
                oracleDriveLogger.e(TAG, "Failed to remove module $moduleName: ${e.message}", e)
                false
            }
        }

        /**
         * Export the current LSPosed module configuration as a JSON string.
         */
        fun exportModuleConfig(): String {
            val modulesDir = java.io.File("/data/adb/modules")
            val modules = modulesDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            val config = modules.associate { dir ->
                val enabled = java.io.File(dir, "enable").exists()
                dir.name to enabled
            }
            return com.google.gson.Gson().toJson(config)
        }

        /**
         * Restore LSPosed module configuration from a JSON string.
         * This will enable/disable modules as specified in the config.
         */
        fun restoreModuleConfig(json: String): String {
            return try {
                val config = com.google.gson.Gson().fromJson(json, Map::class.java)
                config.forEach { (name, enabled) ->
                    if (name is String && enabled is Boolean) {
                        setModuleEnabled(name, enabled)
                    }
                }
                "Module configuration restored."
            } catch (e: Exception) {
                oracleDriveLogger.e(TAG, "Failed to restore module config: ${e.message}", e)
                "Failed to restore module configuration: ${e.message}"
            }
        }

        override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
            if (code == this.TRANSACTION_removeLSPosedModule) {
                val moduleName = data.readString() ?: ""
                val result = removeLSPosedModule(moduleName)
                reply?.writeString(result)
                return true
            }
            if (code == this.TRANSACTION_exportModuleConfig) {
                val result = exportModuleConfig()
                reply?.writeString(result)
                return true
            }
            if (code == this.TRANSACTION_restoreModuleConfig) {
                val json = data.readString() ?: ""
                val result = restoreModuleConfig(json)
                reply?.writeString(result)
                return true
            }
            return super.onTransact(code, data, reply, flags)
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
