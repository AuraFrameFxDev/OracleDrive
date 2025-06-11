// OracleDrive - Genesisystem/app/src/main/aidl/com/genesis/ai/app/ipc/IAuraDriveService.aidl
package com.example.app.ipc; // IMPORTANT: This package declaration is for the interface itself.

// In OracleDrive project, you might also need to import its specific models if you pass them directly.
// For simplicity, we'll use basic types or define similar Parcelables.
// import com.genesis.ai.app.data.model.LSPosedModuleResponse; // Example for Parcelable

interface IAuraDriveService {
    /**
     * Example: Get basic status from OracleDrive.
     */
    String getOracleDriveStatus();

    /**
     * Example: Request to toggle an LSPosed module (OracleDrive handles actual execution).
     * Returns an immediate status message. The actual operation is asynchronous.
     */
    String toggleLSPosedModule(String packageName, boolean enable);

    // NEW: Get detailed internal status as a JSON string or key-value map
    String getDetailedInternalStatus();

    // NEW: Get OracleDrive's internal log for the current day
    String getInternalDiagnosticsLog();

    /**
     * Installs OracleDrive's self-contained root and LSPosed environment.
     * Returns a status message.
     */
    String installRootAndLSPosed;

    /**
     * Removes an LSPosed module by name.
     * Returns a status message.
     */
    String removeLSPosedModule(String moduleName);

    /**
     * Exports the current LSPosed module configuration as a JSON string.
     */
    String exportModuleConfig();

    /**
     * Restores LSPosed module configuration from a JSON string.
     * Returns a status message.
     */
    String restoreModuleConfig(String json);

    // TODO: Add more methods as needed, e.g.,
    // String runScriptInContainer(String scriptContent);
    // Parcelable getModuleList(); // If you want to retrieve a list of modules
}
