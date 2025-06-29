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

        /**
         * Enables or disables an LSPosed module locally based on the provided package name.
         *
         * Validates the module name format before attempting to change its enabled state. Returns a status message indicating success or failure, including validation errors or file system issues.
         *
         * @param packageName The name of the LSPosed module to enable or disable.
         * @param enable If true, enables the module; if false, disables it.
         * @return A message describing the result of the operation.
         */
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

        /**
         * Removes an LSPosed module by its name after validating the module name format.
         *
         * @param moduleName The name of the LSPosed module to remove.
         * @return A status message indicating success, invalid name, or failure due to permissions or file system issues.
         */
        fun removeLSPosedModule(moduleName: String): String {
            return if (!isValidModuleName(moduleName)) {
                "Invalid module name: '$moduleName'. Allowed: a-z, A-Z, 0-9, ., _, -"
            } else if (removeModule(moduleName)) {
                "Module '$moduleName' removed successfully."
            } else {
                "Failed to remove module '$moduleName'. Check root permissions or file system access."
            }
        }

        /**
         * Returns a JSON string containing detailed internal status information.
         *
         * The status includes timestamp, app version, root status, OS and SDK versions, backend connection availability, LSPosed running state, count of enabled LSPosed modules, and the enabled/disabled state of all LSPosed modules.
         *
         * @return A JSON string representing the current internal status and environment details.
         */
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

        /**
         * Retrieves the current day's internal diagnostics log as a string.
         *
         * @return The diagnostics log contents, or an error message if retrieval fails.
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
         * Installs root binaries, the LSPosed framework, and a sample LSPosed module on the device.
         *
         * Attempts to extract and install the `su` binary, extract the LSPosed framework, and deploy a sample LSPosed module. Returns a status message indicating the outcome of each installation step or any encountered exception.
         *
         * @return A string describing the result of the installation process.
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

        /**
         * Determines whether LSPosed is currently running on the device.
         *
         * Checks for the presence of the LSPosed process, its Unix socket, or its working directory to infer runtime status.
         *
         * @return `true` if LSPosed is detected as running; `false` otherwise.
         */
        private fun isLSPosedRunning(): Boolean {
            // Check for LSPosed process
            val processCheck = com.genesis.ai.app.utils.ShellUtils.runCommand("ps | grep lspd")
            val socketExists = File("/dev/socket/lsposed").exists()
            val lsposedDir = File("/data/adb/lspd")
            return processCheck.contains("lspd") || socketExists || lsposedDir.exists()
        }

        /**
         * Retrieves the enabled or disabled state of all installed LSPosed modules.
         *
         * @return A map where each key is a module name and the value is true if the module is enabled, false otherwise.
         */
        private fun getLSPosedModuleStates(): Map<String, Boolean> {
            val modulesDir = File("/data/adb/modules")
            val modules = modulesDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            return modules.associate { it.name to File(it, "enable").exists() }
        }

        /**
         * Checks if the given module name is valid by ensuring it contains only letters, digits, dots, underscores, or hyphens.
         *
         * Prevents invalid or potentially unsafe module names that could lead to path traversal or injection vulnerabilities.
         *
         * @param name The module name to validate.
         * @return `true` if the module name is valid; `false` otherwise.
         */
        private fun isValidModuleName(name: String): Boolean {
            return name.matches(Regex("^[a-zA-Z0-9._-]+$"))
        }

        /**
         * Deletes the specified LSPosed module directory and its contents.
         *
         * @param moduleName The name of the module to remove.
         * @return `true` if the module directory was successfully deleted, `false` otherwise.
         */
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
         * Returns the current enabled/disabled state of all LSPosed modules as a JSON string.
         *
         * The JSON maps module names to boolean values indicating whether each module is enabled.
         * Only modules present in the `/data/adb/modules` directory are included.
         *
         * @return A JSON string representing the LSPosed module enable states.
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
         * Restores LSPosed module enable states from a JSON configuration string.
         *
         * The JSON should represent a map of module names to their enabled (Boolean) states.
         * Returns a status message indicating success or failure.
         *
         * @param json A JSON string mapping module names to their enabled states.
         * @return A message indicating whether the configuration was restored successfully or an error occurred.
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

        /**
         * Handles custom AIDL transactions for LSPosed module management and configuration.
         *
         * Processes transaction codes for removing a module, exporting module configuration, and restoring module configuration by reading input from the parcel, invoking the corresponding method, and writing the result to the reply parcel. Falls back to the superclass implementation for unrecognized codes.
         *
         * @return `true` if the transaction was handled; otherwise, the result of the superclass implementation.
         */
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
