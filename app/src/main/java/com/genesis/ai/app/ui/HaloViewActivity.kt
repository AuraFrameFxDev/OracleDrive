package com.genesis.ai.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.genesis.ai.app.R

class HaloViewActivity : AppCompatActivity() {
    /**
     * Initializes the HaloViewActivity, sets up the UI, and binds to services for module state visualization and file operations.
     *
     * Configures the activity's theme based on system night mode, displays a graphical view of module states by fetching data from the AuraDriveService, and sets up UI buttons for backup, restore, file management, help, cleaning unused files, and cloud sync placeholders. Handles service connections and user interactions for these features.
     *
     * @param savedInstanceState The previously saved instance state, if any.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_halo_view)

        // Enable dark mode if system is in night mode
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            setTheme(R.style.AppTheme_Dark)
        }

        val haloGraphView = findViewById<HaloGraphView>(R.id.haloGraphView)

        // Connect to AuraDriveService and fetch real module state data
        val serviceIntent = android.content.Intent("com.example.app.ipc.IAuraDriveService").setPackage(packageName)
        val conn = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
                val aidl = com.example.app.ipc.IAuraDriveService.Stub.asInterface(binder)
                val statusJson = aidl.getDetailedInternalStatus()
                val moduleStates = parseModuleStatesFromJson(statusJson)
                // Layout nodes in a circle for demo; real layout should be smarter
                val centerX = 500f
                val centerY = 700f
                val radius = 300f
                val nodes = moduleStates.keys.mapIndexed { i, name ->
                    val angle = 2 * Math.PI * i / moduleStates.size
                    ModuleNode(
                        id = name,
                        label = name,
                        x = (centerX + radius * Math.cos(angle)).toFloat(),
                        y = (centerY + radius * Math.sin(angle)).toFloat()
                    )
                }
                val edges = nodes.flatMap { from ->
                    nodes.filter { it != from }.map { to ->
                        ModuleEdge(from.id, to.id, blocked = !moduleStates[to.id]!!)
                    }
                }
                runOnUiThread {
                    haloGraphView.setGraphData(nodes, edges)
                }
                unbindService(this)
            }
            override fun onServiceDisconnected(name: android.content.ComponentName?) {}
        }
        bindService(serviceIntent, conn, android.content.Context.BIND_AUTO_CREATE)

        // Add backup and restore buttons
        val backupButton = findViewById<Button>(R.id.backupButton)
        val restoreButton = findViewById<Button>(R.id.restoreButton)
        val openFileManagerButton = findViewById<Button>(R.id.openFileManagerButton)
        val helpButton = findViewById<Button>(R.id.helpButton)

        backupButton.setOnClickListener {
            val serviceIntent = android.content.Intent("com.example.app.ipc.IAuraDriveService").setPackage(packageName)
            val conn = object : android.content.ServiceConnection {
                override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
                    val aidl = com.example.app.ipc.IAuraDriveService.Stub.asInterface(binder)
                    val configJson = aidl.exportModuleConfig()
                    // Save to file
                    val file = java.io.File(getExternalFilesDir(null), "lsposed_backup_${System.currentTimeMillis()}.json")
                    file.writeText(configJson)
                    android.widget.Toast.makeText(this@HaloViewActivity, "Backup saved to ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                    unbindService(this)
                }
                override fun onServiceDisconnected(name: android.content.ComponentName?) {}
            }
            bindService(serviceIntent, conn, android.content.Context.BIND_AUTO_CREATE)
        }
        restoreButton.setOnClickListener {
            // Use file picker for restore
            val intent = Intent(this, FilePickerActivity::class.java)
            startActivityForResult(intent, 2001)
        }
        openFileManagerButton.setOnClickListener {
            // Launch file manager activity for automation/cleaning/organization
            val intent = Intent(this, FileManagerActivity::class.java)
            startActivity(intent)
        }
        helpButton.setOnClickListener {
            val intent = Intent(this, HelpActivity::class.java)
            startActivity(intent)
        }

        // Add a button to clean unused module files
        val cleanButton = Button(this).apply { text = "Clean Unused Files" }
        layout.addView(cleanButton)
        cleanButton.setOnClickListener {
            val deleted = com.genesis.ai.app.utils.ModuleDeployer.cleanUnusedModuleFiles()
            android.widget.Toast.makeText(this, "Deleted $deleted unused files.", android.widget.Toast.LENGTH_SHORT).show()
        }

        // Cloud sync: backup to Google Drive
        val backupCloudButton = Button(this).apply { text = "Cloud Backup" }
        layout.addView(backupCloudButton)
        backupCloudButton.setOnClickListener {
            // TODO: Launch Google account picker, get Drive service, and upload backup file
            Toast.makeText(this, "Cloud backup coming soon!", Toast.LENGTH_SHORT).show()
        }
        // Cloud sync: restore from Google Drive
        val restoreCloudButton = Button(this).apply { text = "Cloud Restore" }
        layout.addView(restoreCloudButton)
        restoreCloudButton.setOnClickListener {
            // TODO: Launch Google account picker, get Drive service, list backups, and download/restore
            Toast.makeText(this, "Cloud restore coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Parses a JSON string to extract a map of module names to their boolean states.
     *
     * The JSON is expected to contain a "moduleStates" object mapping module names to boolean values.
     * Returns an empty map if parsing fails or the expected structure is not present.
     *
     * @param json The JSON string containing module state information.
     * @return A map where keys are module names and values indicate their boolean states.
     */
    fun parseModuleStatesFromJson(json: String): Map<String, Boolean> {
        return try {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val states = obj["moduleStates"].asJsonObject
            states.entrySet().associate { it.key to it.value.asBoolean }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Handles the result from activities started for a result, specifically processing module configuration restore requests.
     *
     * If the result is from the file picker activity (request code 2001) and is successful, retrieves the selected JSON configuration,
     * binds to the `IAuraDriveService` to restore the module configuration, and displays the result in a toast message.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == RESULT_OK) {
            val json = data?.getStringExtra("json") ?: return
            val serviceIntent = android.content.Intent("com.example.app.ipc.IAuraDriveService").setPackage(packageName)
            val conn = object : android.content.ServiceConnection {
                override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
                    val aidl = com.example.app.ipc.IAuraDriveService.Stub.asInterface(binder)
                    val result = aidl.restoreModuleConfig(json)
                    android.widget.Toast.makeText(this@HaloViewActivity, result, android.widget.Toast.LENGTH_LONG).show()
                    unbindService(this)
                }
                override fun onServiceDisconnected(name: android.content.ComponentName?) {}
            }
            bindService(serviceIntent, conn, android.content.Context.BIND_AUTO_CREATE)
        }
    }

    // Apply custom transition for all page switches
    overridePendingTransition(R.anim.disperse_in, R.anim.fade_out)
}
