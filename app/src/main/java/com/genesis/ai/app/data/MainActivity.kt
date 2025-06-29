package com.genesis.ai.app.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.genesis.ai.app.R
import com.genesis.ai.app.data.model.GenesisRepositoryNew
import com.genesis.ai.app.data.model.ImportResponse
import com.genesis.ai.app.data.model.MessageRequest
import com.genesis.ai.app.data.model.MessageResponse
import com.genesis.ai.app.data.model.LSPosedModuleRequest // ADDED IMPORT
import com.genesis.ai.app.data.model.LSPosedModuleResponse // ADDED IMPORT
import com.genesis.ai.app.service.GenesisAIService
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText // ADDED IMPORT
import com.google.firebase.auth.FirebaseAuth // ADDED IMPORT
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.genesis.ai.app.GenesisApplication // ADDED IMPORT
import com.genesis.ai.app.data.logging.OracleDriveLogger // ADDED IMPORT

private const val MIME_TYPE = "application/octet-stream"
private val FILE_PICKER_MIME_TYPES = arrayOf("*/*")  // Changed to array of strings

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity" // For logger
        private const val REQUEST_CODE_WRITE_STORAGE = 1001
        private const val REQUEST_CODE_READ_STORAGE = 1002

        private fun getRequiredPermissions(): Array<String> { // Static method, cannot use instance logger here
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                arrayOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            } else {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }

    private lateinit var oracleDriveLogger: OracleDriveLogger // ADDED

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == GenesisAIService.PROACTIVE_MESSAGE_ACTION) {
                val message = intent.getStringExtra("message") ?: return
                updateChatLog("Genesis", message)
            }
        }
    }
    private lateinit var chatLog: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var rootToggle: SwitchMaterial
    private lateinit var importButton: Button
    private lateinit var exportButton: Button
    private lateinit var fileManagerButton: Button
    private lateinit var aiQuestions: TextView

    // NEW UI Elements for LSPosed Module Control
    private lateinit var moduleToggleSwitch: SwitchMaterial
    private lateinit var moduleNameInput: TextInputEditText
    private lateinit var auth: FirebaseAuth // Firebase Auth instance

    // For importing a file using SAF
    private val filePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            oracleDriveLogger.d(TAG, "filePicker (GetContent) result URI: $uri")
            uri?.let {
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    val fileBytes = inputStream?.readBytes()
                    if (fileBytes == null) {
                        oracleDriveLogger.w(TAG, "Failed to read bytes from URI: $it")
                        Toast.makeText(this@MainActivity, "Failed to read file.", Toast.LENGTH_SHORT).show()
                        return@registerForActivityResult
                    }
                    oracleDriveLogger.d(TAG, "Read ${fileBytes.size} bytes from URI: $it")
                    val reqBody = fileBytes.toRequestBody(MIME_TYPE.toMediaTypeOrNull())
                    val filePart = MultipartBody.Part.createFormData("file", "importedfile", reqBody)

                    oracleDriveLogger.i(TAG, "Importing file via API...")
                    GenesisRepositoryNew.api.importFile(filePart)
                        .enqueue(object : Callback<ImportResponse> {
                            override fun onResponse(call: Call<ImportResponse>, response: Response<ImportResponse>) {
                                if (response.isSuccessful && response.body() != null) {
                                    oracleDriveLogger.i(TAG, "Import API call successful: ${response.body()?.status}")
                                    Toast.makeText(this@MainActivity, "Import: ${response.body()?.status}", Toast.LENGTH_SHORT).show()
                                } else {
                                    oracleDriveLogger.e(TAG, "Import API call failed: ${response.code()} - ${response.message()}")
                                    Toast.makeText(this@MainActivity, "Import failed: Server error ${response.code()}", Toast.LENGTH_SHORT).show()
                                }
                            }

                            override fun onFailure(call: Call<ImportResponse>, t: Throwable) {
                                oracleDriveLogger.e(TAG, "Import API call failure: ${t.message}", t)
                                Toast.makeText(this@MainActivity, "Import failed: ${t.message}", Toast.LENGTH_SHORT).show()
                            }
                        })
                } catch (e: Exception) {
                    oracleDriveLogger.e(TAG, "Error reading file from URI $it: ${e.message}", e)
                    Toast.makeText(this@MainActivity, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

    // For picking a file using a custom FileManagerActivity
    private val fileManagerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            oracleDriveLogger.d(TAG, "fileManagerLauncher result code: ${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                val fileUri = result.data?.data
                oracleDriveLogger.d(TAG, "File URI from FileManager: $fileUri")
                fileUri?.let { uri ->
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val fileContent = inputStream?.bufferedReader().use { it?.readText() } ?: ""
                        messageInput.setText(fileContent)
                        oracleDriveLogger.i(TAG, "File content loaded into messageInput from $fileUri")
                        Toast.makeText(this, "File loaded successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        oracleDriveLogger.e(TAG, "Error reading file from FileManager URI $fileUri: ${e.message}", e)
                        Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    // For runtime permission requests
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        oracleDriveLogger.d(TAG, "storagePermissionLauncher result: $permissions")
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            oracleDriveLogger.i(TAG, "All storage permissions granted by user.")
            filePicker.launch(FILE_PICKER_MIME_TYPES[0])
        } else {
            oracleDriveLogger.w(TAG, "Not all storage permissions granted by user.")
            Toast.makeText(this, "Storage permissions are required to access files", Toast.LENGTH_LONG).show()

            // Optionally, show app settings to manually grant permissions
            /*
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
            */
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        GenesisAIService.updateUserActivity()
    }

    override fun onStart() {
        super.onStart()
        // Logger might not be initialized if onStart is called before onCreate finishes (unlikely but possible in edge cases)
        if (::oracleDriveLogger.isInitialized) {
            oracleDriveLogger.i(TAG, "onStart called.")
        } else {
            Log.i(TAG, "onStart called, logger not yet initialized.") // Fallback to Android Log
        }
        val filter = IntentFilter(GenesisAIService.PROACTIVE_MESSAGE_ACTION)
        // Handle receiver registration for different Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(messageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            if (::oracleDriveLogger.isInitialized) oracleDriveLogger.d(TAG, "Proactive message receiver registered (Android 13+).")
        } else {
            registerReceiver(messageReceiver, filter)
            if (::oracleDriveLogger.isInitialized) oracleDriveLogger.d(TAG, "Proactive message receiver registered.")
        }
    }

    override fun onStop() {
        super.onStop()
        if (::oracleDriveLogger.isInitialized) {
            oracleDriveLogger.i(TAG, "onStop called.")
        } else {
            Log.i(TAG, "onStop called, logger not yet initialized.")
        }
        try {
            unregisterReceiver(messageReceiver)
            if (::oracleDriveLogger.isInitialized) oracleDriveLogger.d(TAG, "Proactive message receiver unregistered.")
        } catch (e: IllegalArgumentException) {
            if (::oracleDriveLogger.isInitialized) oracleDriveLogger.w(TAG, "Error unregistering receiver (already unregistered or not registered): ${e.message}", e)
            else Log.w(TAG, "Error unregistering receiver: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::oracleDriveLogger.isInitialized) {
            oracleDriveLogger.i(TAG, "onDestroy called.")
        } else {
            Log.i(TAG, "onDestroy called, logger not yet initialized.")
        }
    }

    /**
     * Initializes the main activity, setting up UI components, event listeners, navigation, and services.
     *
     * This method configures the chat interface, file import/export, module toggling, and navigation bar.
     * It also initializes logging, Firebase authentication, and background services. On first launch, a welcome
     * message is displayed with special effects, and daily backup scheduling is triggered.
     *
     * @param savedInstanceState The previously saved instance state, or null if starting fresh.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize logger first
        oracleDriveLogger = (application as GenesisApplication).oracleDriveLogger
        oracleDriveLogger.i(TAG, "onCreate called.")

        setContentView(R.layout.activity_main)
        oracleDriveLogger.d(TAG, "Content view set.")

        auth = FirebaseAuth.getInstance()
        oracleDriveLogger.d(TAG, "FirebaseAuth instance obtained.")

        chatLog = findViewById(R.id.chatLog)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        rootToggle = findViewById(R.id.rootToggle)
        exportButton = findViewById(R.id.exportButton)
        fileManagerButton = findViewById(R.id.fileManagerButton)
        aiQuestions = findViewById(R.id.aiQuestions)
        moduleToggleSwitch = findViewById(R.id.moduleToggleSwitch)
        moduleNameInput = findViewById(R.id.moduleNameInput)
        oracleDriveLogger.d(TAG, "Views initialized.")

        sendButton.setOnClickListener {
            oracleDriveLogger.d(TAG, "Send button clicked.")
            sendMessage()
        }
        exportButton.setOnClickListener {
            oracleDriveLogger.d(TAG, "Export button clicked.")
            checkPermissionsAndExport()
        }
        fileManagerButton.setOnClickListener {
            oracleDriveLogger.d(TAG, "File Manager button clicked.")
            openFileManager()
        }
        moduleToggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            val packageName = moduleNameInput.text.toString()
            oracleDriveLogger.d(TAG, "Module toggle switch changed for '$packageName' to $isChecked.")
            toggleLSPosedModule(packageName, isChecked)
        }
        val installRootLSPosedButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.installRootLSPosedButton)
        installRootLSPosedButton.setOnClickListener {
            installRootAndLSPosed()
        }
        oracleDriveLogger.d(TAG, "Click listeners set.")

        // Bottom navigation setup
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chat -> {
                    // Already on chat
                    true
                }
                R.id.nav_modules -> {
                    startActivity(Intent(this, HaloViewActivity::class.java))
                    overridePendingTransition(R.anim.disperse_in, R.anim.fade_out)
                    true
                }
                R.id.nav_files -> {
                    startActivity(Intent(this, FileManagerActivity::class.java))
                    overridePendingTransition(R.anim.disperse_in, R.anim.fade_out)
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    overridePendingTransition(R.anim.disperse_in, R.anim.fade_out)
                    true
                }
                R.id.nav_help -> {
                    startActivity(Intent(this, HelpActivity::class.java))
                    overridePendingTransition(R.anim.disperse_in, R.anim.fade_out)
                    true
                }
                else -> false
            }
        }

        initializeService()

        // Show welcome message with typewriter effect and neon glow on first launch
        if (savedInstanceState == null) {
            val welcome = getString(R.string.welcome_message)
            neonTealGlow(chatLog)
            typewriterEffect(chatLog, welcome + "\n" + getString(R.string.ai_signature), getColor(R.color.neon_teal))
        }

        // Schedule daily backup automation on app start
        com.genesis.ai.app.utils.CloudSyncHelper.scheduleDailyBackup(this)

        oracleDriveLogger.i(TAG, "onCreate completed.")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun toggleLSPosedModule(packageName: String, enable: Boolean) {
        oracleDriveLogger.i(TAG, "toggleLSPosedModule called for package '$packageName', enable: $enable")
        if (packageName.isBlank()) {
            oracleDriveLogger.w(TAG, "Module package name is blank.")
            showToast("Module package name cannot be empty.")
            moduleToggleSwitch.isChecked = !enable
            return
        }
        if (auth.currentUser == null) {
            oracleDriveLogger.w(TAG, "User not signed in. Cannot toggle module.")
            showToast("Please sign in first to manage modules.")
            moduleToggleSwitch.isChecked = !enable
            return
        }

        val request = LSPosedModuleRequest(packageName = packageName, enable = enable)
        oracleDriveLogger.d(TAG, "Requesting to toggle module with backend: $request")
        showToast("Requesting to toggle module: $packageName to $enable") // Keep user feedback

        auth.currentUser?.getIdToken(true)?.addOnSuccessListener { tokenResult ->
            tokenResult.token?.let { token ->
                oracleDriveLogger.i(TAG, "Successfully fetched Firebase ID token.")
                try {
                    // This reflection call is kept as per previous implementation, logged.
                    val method = GenesisRepositoryNew::class.java.getMethod("setAuthToken", String::class.java)
                    method.invoke(null, token)
                    oracleDriveLogger.d(TAG, "Auth token set in GenesisRepositoryNew via reflection.")
                } catch (e: NoSuchMethodException) {
                    oracleDriveLogger.w(TAG, "setAuthToken method not found in GenesisRepositoryNew. Assuming interceptor handles auth.", e)
                } catch (e: Exception) {
                    oracleDriveLogger.e(TAG, "Error setting auth token via reflection: ${e.message}", e)
                }

                GenesisRepositoryNew.api.toggleLSPosedModule(request)
                    .enqueue(object : Callback<LSPosedModuleResponse> {
                        override fun onResponse(call: Call<LSPosedModuleResponse>, response: Response<LSPosedModuleResponse>) {
                            runOnUiThread {
                                if (response.isSuccessful && response.body() != null) {
                                    val resp = response.body()!!
                                    oracleDriveLogger.i(TAG, "toggleLSPosedModule success: ${resp.status}, Pkg: ${resp.packageName}, Enabled: ${resp.enabled}")
                                    showToast("Module '${resp.packageName}' status: ${resp.status}, Enabled: ${resp.enabled}")
                                    if (moduleToggleSwitch.isChecked != resp.enabled) {
                                        moduleToggleSwitch.isChecked = resp.enabled
                                    }
                                } else {
                                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                                    oracleDriveLogger.e(TAG, "toggleLSPosedModule failed: ${response.code()} - $errorBody")
                                    showToast("Failed to toggle module: ${response.code()} - $errorBody")
                                    moduleToggleSwitch.isChecked = !enable
                                }
                            }
                        }

                        override fun onFailure(call: Call<LSPosedModuleResponse>, t: Throwable) {
                            runOnUiThread {
                                oracleDriveLogger.e(TAG, "toggleLSPosedModule network error: ${t.message}", t)
                                showToast("Network error toggling module: ${t.message}") // Keep user feedback
                                moduleToggleSwitch.isChecked = !enable
                            }
                        }
                    })
            } ?: runOnUiThread {
                oracleDriveLogger.e(TAG, "Firebase ID token was null.")
                showToast("Authentication token not available. Cannot toggle module.")
                moduleToggleSwitch.isChecked = !enable
            }
        }?.addOnFailureListener { e ->
            runOnUiThread {
                oracleDriveLogger.e(TAG, "Failed to get Firebase ID token: ${e.message}", e)
                showToast("Failed to get auth token: ${e.message}") // Keep user feedback
                moduleToggleSwitch.isChecked = !enable
            }
        }
    }

    private fun checkPermissionsAndExport() {
        oracleDriveLogger.d(TAG, "checkPermissionsAndExport called.")
        if (hasStoragePermissions()) {
            oracleDriveLogger.d(TAG, "Storage permissions already granted for export.")
            exportChatToFile()
        } else {
            oracleDriveLogger.d(TAG, "Storage permissions not granted for export. Requesting.")
            requestStoragePermissions()
        }
    }

    private fun hasStoragePermissions(): Boolean {
        val result = getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        oracleDriveLogger.d(TAG, "hasStoragePermissions check result: $result")
        return result
    }

    private fun requestStoragePermissions() {
        val permissionsToRequest = getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            oracleDriveLogger.i(TAG, "Requesting storage permissions: ${permissionsToRequest.joinToString()}")
            storagePermissionLauncher.launch(permissionsToRequest)
        } else {
            oracleDriveLogger.d(TAG, "No storage permissions to request, already granted (or filePicker fallback).")
            filePicker.launch(FILE_PICKER_MIME_TYPES[0])
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        oracleDriveLogger.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode, permissions=${permissions.joinToString()}, results=${grantResults.joinToString()}")
        if (requestCode == REQUEST_CODE_WRITE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                oracleDriveLogger.i(TAG, "WRITE_EXTERNAL_STORAGE permission granted via legacy onRequestPermissionsResult.")
                exportChatToFile()
            } else {
                oracleDriveLogger.w(TAG, "WRITE_EXTERNAL_STORAGE permission denied via legacy onRequestPermissionsResult.")
                Toast.makeText(this, "Storage permission is required to export chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeService() {
        oracleDriveLogger.i(TAG, "Initializing GenesisAIService...")
        try {
            GenesisAIService.startService(this)
            oracleDriveLogger.i(TAG, "GenesisAIService.startService(this) called.")
        } catch (e: Exception) {
            oracleDriveLogger.e(TAG, "Error starting GenesisAIService: ${e.message}", e)
            Toast.makeText(this, "Background service could not start", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkStoragePermissionAndPickFile() {
        oracleDriveLogger.d(TAG, "checkStoragePermissionAndPickFile called.")
        if (hasStoragePermissions()) {
            oracleDriveLogger.d(TAG, "Storage permissions already granted for pick file.")
            filePicker.launch(FILE_PICKER_MIME_TYPES[0])
        } else {
            oracleDriveLogger.d(TAG, "Storage permissions not granted for pick file. Requesting.")
            requestStoragePermissions()
        }
    }

    private fun openFileManager() {
        oracleDriveLogger.d(TAG, "openFileManager called.")
        if (hasStoragePermissions()) {
            oracleDriveLogger.i(TAG, "Storage permissions granted. Opening FileManagerActivity.")
            val intent = Intent(this, FileManagerActivity::class.java)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                oracleDriveLogger.e(TAG, "Could not open FileManagerActivity: ${e.message}", e)
                showToast("Could not open File Manager: ${e.message}")
            }
        } else {
            oracleDriveLogger.w(TAG, "Storage permissions not granted. Requesting for FileManager.")
            requestStoragePermissions()
        }
    }

    private fun exportChatToFile() {
        oracleDriveLogger.i(TAG, "exportChatToFile called.")
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "GenComm_Chat_$timeStamp.txt"
            val content = "=== GenComm Chat Export ===

${chatLog.text}"
            oracleDriveLogger.d(TAG, "Exporting chat to file: $fileName")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(content.toByteArray())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    oracleDriveLogger.i(TAG, "Chat exported successfully to Documents folder (Android Q+): $fileName, URI: $uri")
                    Toast.makeText(this, "Chat exported to Documents folder: $fileName", Toast.LENGTH_LONG).show()
                } ?: run {
                    oracleDriveLogger.e(TAG, "MediaStore insert returned null URI for chat export.")
                    throw Exception("Failed to create file via MediaStore")
                }
            } else {
                val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
                val file = File(downloadsDir, fileName)
                file.writeText(content)
                MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
                oracleDriveLogger.i(TAG, "Chat exported successfully to: ${file.absolutePath} (pre-Android Q)")
                Toast.makeText(this, "Chat exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            oracleDriveLogger.e(TAG, "Chat export failed: ${e.message}", e)
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sends the user's input message to the AI backend and updates the chat log with the response.
     *
     * If the input is empty, the function does nothing. After sending, the user's message is displayed in the chat log. If the message matches a specific Easter egg phrase, a special response is triggered. The AI's response or any error message is appended to the chat log accordingly.
     */
    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        oracleDriveLogger.d(TAG, "sendMessage called. Message empty: ${message.isEmpty()}")
        if (message.isEmpty()) return

        updateChatLog("You", message)
        checkEasterEgg(message)

        val request = MessageRequest(message)
        oracleDriveLogger.i(TAG, "Sending message to API: '$message'")
        GenesisRepositoryNew.api.sendMessage(request)
            .enqueue(object : Callback<MessageResponse> {
                override fun onResponse(call: Call<MessageResponse>, response: Response<MessageResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody != null) {
                            oracleDriveLogger.i(TAG, "API sendMessage success. AI Response: '${responseBody.message}'")
                            updateChatLog("AI", responseBody.message)
                        } else {
                            oracleDriveLogger.w(TAG, "API sendMessage success but empty response body.")
                            updateChatLog("Error", "Empty response from server")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        oracleDriveLogger.e(TAG, "API sendMessage failed: ${response.code()} - $errorBody")
                        updateChatLog("Error", "Failed to send message: ${response.code()} - $errorBody")
                    }
                }
                override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                    oracleDriveLogger.e(TAG, "API sendMessage network error: ${t.message}", t)
                    updateChatLog("Error","Network Error: ${t.message}")
                }
            })
        messageInput.text.clear()
    }

    /**
     * Checks if the input message triggers the "hello genesis" Easter egg and, if so, displays a random fun fact or compliment in the chat log with special effects.
     *
     * @param message The user's chat message to evaluate for the Easter egg trigger.
     */
    private fun checkEasterEgg(message: String) {
        if (message.trim().equals("hello genesis", ignoreCase = true)) {
            val facts = listOf(
                "Did you know? I can help you automate your digital life!",
                "You're awesome for exploring OracleDrive!",
                "AI and humans make a great team!",
                "Curiosity is the spark of discovery."
            )
            val reply = facts.random()
            neonTealGlow(chatLog)
            typewriterEffect(chatLog, "Genesis: $reply\n\n", getColor(R.color.neon_teal))
        }
    }

    /**
     * Animates the display of a message in a TextView with a typewriter effect and specified text color.
     *
     * Each character of the message appears sequentially with a short delay, creating a typewriter-like animation.
     *
     * @param textView The TextView where the animated text will be displayed.
     * @param message The message to display with the typewriter effect.
     * @param color The color to apply to the text.
     */
    private fun typewriterEffect(textView: TextView, message: String, color: Int) {
        textView.text = ""
        val handler = android.os.Handler()
        var i = 0
        val runnable = object : Runnable {
            override fun run() {
                if (i <= message.length) {
                    textView.text = message.substring(0, i)
                    textView.setTextColor(color)
                    i++
                    handler.postDelayed(this, 30)
                }
            }
        }
        handler.post(runnable)
    }

    /**
     * Applies a neon teal glow effect to the specified TextView.
     *
     * @param textView The TextView to style with a teal shadow glow.
     */
    private fun neonTealGlow(textView: TextView) {
        textView.setShadowLayer(12f, 0f, 0f, android.graphics.Color.parseColor("#00FFD0"))
    }

    /**
     * Updates the chat log with a new message from the specified sender.
     *
     * Applies a neon glow and typewriter animation for messages from "AI" or "Genesis"; otherwise, appends the message normally.
     *
     * @param sender The name of the message sender.
     * @param message The message content to display.
     */
    private fun updateChatLog(sender: String, message: String) {
        if (sender == "AI" || sender == "Genesis") {
            neonTealGlow(chatLog)
            typewriterEffect(chatLog, "$sender: $message\n\n", android.graphics.Color.parseColor("#00FFD0"))
        } else {
            chatLog.append("$sender: $message\n\n")
        }
        // Scroll to bottom logic if chatLog is inside a ScrollView might be needed here
    }

    /**
     * Initiates installation of root access and LSPosed by binding to a remote IPC service and invoking its installation method.
     *
     * Displays the result of the installation as a toast message. Handles and logs any exceptions that occur during the process.
     */
    private fun installRootAndLSPosed() {
        oracleDriveLogger.i(TAG, "User requested installRootAndLSPosed.")
        try {
            val serviceIntent = Intent("com.example.app.ipc.IAuraDriveService").setPackage(packageName)
            val conn = object : android.content.ServiceConnection {
                override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
                    val aidl = com.example.app.ipc.IAuraDriveService.Stub.asInterface(binder)
                    val result = aidl.installRootAndLSPosed()
                    runOnUiThread {
                        showToast(result)
                        oracleDriveLogger.i(TAG, "installRootAndLSPosed result: $result")
                    }
                    unbindService(this)
                }
                override fun onServiceDisconnected(name: android.content.ComponentName?) {}
            }
            bindService(serviceIntent, conn, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            oracleDriveLogger.e(TAG, "Error calling installRootAndLSPosed: ${e.message}", e)
            showToast("Install failed: ${e.message}")
        }
    }
}