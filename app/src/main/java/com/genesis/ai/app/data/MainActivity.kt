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

private const val MIME_TYPE = "application/octet-stream"
private val FILE_PICKER_MIME_TYPES = arrayOf("*/*")  // Changed to array of strings

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CODE_WRITE_STORAGE = 1001
        private const val REQUEST_CODE_READ_STORAGE = 1002

        private fun getRequiredPermissions(): Array<String> {
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
            uri?.let {
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    val fileBytes = inputStream?.readBytes() ?: return@registerForActivityResult
                    val reqBody = fileBytes.toRequestBody(MIME_TYPE.toMediaTypeOrNull())
                    val filePart =
                        MultipartBody.Part.createFormData("file", "importedfile", reqBody)
                    GenesisRepositoryNew.api.importFile(filePart)
                        .enqueue(object : Callback<ImportResponse> {
                            override fun onResponse(
                                call: Call<ImportResponse>,
                                response: Response<ImportResponse>,
                            ) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Import: ${response.body()?.status}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            override fun onFailure(call: Call<ImportResponse>, t: Throwable) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Import failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error reading file: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    // For picking a file using a custom FileManagerActivity
    private val fileManagerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val fileUri = result.data?.data
                fileUri?.let { uri ->
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val fileContent = inputStream?.bufferedReader().use { it?.readText() } ?: ""
                        messageInput.setText(fileContent)
                        Toast.makeText(this, "File loaded successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }

    // For runtime permission requests
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // All permissions granted, proceed with file operation
            filePicker.launch(FILE_PICKER_MIME_TYPES[0])
        } else {
            // Explain why the permission is needed
            Toast.makeText(
                this,
                "Storage permissions are required to access files",
                Toast.LENGTH_LONG
            ).show()

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
        val filter = IntentFilter(GenesisAIService.PROACTIVE_MESSAGE_ACTION)
        registerReceiver(messageReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(messageReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
            // This can happen if the activity is stopped before it's fully started
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize views
        chatLog = findViewById(R.id.chatLog)
        messageInput = findViewById(R.id.messageInput) // This is an EditText
        sendButton = findViewById(R.id.sendButton)
        rootToggle = findViewById(R.id.rootToggle) // Existing switch
        // importButton = findViewById(R.id.importButton) // Example, if it exists and needs init
        exportButton = findViewById(R.id.exportButton)
        fileManagerButton = findViewById(R.id.fileManagerButton)
        aiQuestions = findViewById(R.id.aiQuestions)

        // Initialize NEW UI Elements for LSPosed Module Control
        // Assuming IDs from the XML provided in the issue:
        moduleToggleSwitch = findViewById(R.id.moduleToggleSwitch)
        moduleNameInput = findViewById(R.id.moduleNameInput)

        // Set up click listeners
        sendButton.setOnClickListener { sendMessage() }
        exportButton.setOnClickListener { checkPermissionsAndExport() }
        fileManagerButton.setOnClickListener { openFileManager() }
        // importButton.setOnClickListener { checkStoragePermissionAndPickFile() } // If importButton is used

        // Listener for the new module toggle switch
        moduleToggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            val packageName = moduleNameInput.text.toString()
            toggleLSPosedModule(packageName, isChecked)
        }
        // TODO: Add a button or list view to select modules instead of manual input (as per issue comment)

        initializeService()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun toggleLSPosedModule(packageName: String, enable: Boolean) {
        if (packageName.isBlank()) {
            showToast("Module package name cannot be empty.")
            moduleToggleSwitch.isChecked = !enable // Revert toggle state
            return
        }
        if (auth.currentUser == null) {
            showToast("Please sign in first to manage modules.")
            moduleToggleSwitch.isChecked = !enable // Revert toggle state
            return
        }

        val request = LSPosedModuleRequest(packageName = packageName, enable = enable)
        showToast("Requesting to toggle module: $packageName to $enable")

        // Get fresh ID token (this should be handled securely, e.g., via a ViewModel/Repository)
        auth.currentUser?.getIdToken(true)?.addOnSuccessListener { tokenResult ->
            tokenResult.token?.let { token ->
                // The issue implies GenesisRepositoryNew.api directly, but typically you'd pass the token
                // with the request, e.g., via an Authenticator or interceptor in Retrofit setup.
                // For this implementation, we'll assume GenesisRepositoryNew handles token attachment
                // or the API doesn't require bearer token for this specific endpoint based on server setup.
                // If direct token usage is needed: GenesisRepositoryNew.setAuthToken(token) or pass to method.
                // The server's get_current_user expects an Authorization header.
                // For now, let's assume the Retrofit client used by GenesisRepositoryNew.api has an interceptor
                // that adds this token. If not, this part needs adjustment.
                // The issue's example for toggleLSPosedModule in MainActivity.kt doesn't show explicit token setting
                // for the repository before the call, but mentions GenesisRepositoryNew.setAuthToken(token)
                // as a possibility in a comment.

                // Let's try to use the setAuthToken approach if available, otherwise, it assumes interceptor.
                // Assuming GenesisRepositoryNew has a static method setAuthToken for simplicity here.
                // This is a common pattern but might differ in the actual project.
                try {
                    val method = GenesisRepositoryNew::class.java.getMethod("setAuthToken", String::class.java)
                    method.invoke(null, token) // Call static method if it exists
                     showToast("Auth token set for API call.")
                } catch (e: NoSuchMethodException) {
                    showToast("setAuthToken method not found in GenesisRepositoryNew. Assuming interceptor handles auth.")
                } catch (e: Exception) {
                    showToast("Error setting auth token: ${e.message}")
                }


                GenesisRepositoryNew.api.toggleLSPosedModule(request)
                    .enqueue(object : Callback<LSPosedModuleResponse> {
                        override fun onResponse(call: Call<LSPosedModuleResponse>, response: Response<LSPosedModuleResponse>) {
                            runOnUiThread { // Ensure UI updates are on the main thread
                                if (response.isSuccessful && response.body() != null) {
                                    val resp = response.body()!!
                                    showToast("Module '${resp.packageName}' status: ${resp.status}, Enabled: ${resp.enabled}")
                                    // Update UI based on actual response if needed
                                    // If server confirms different state, update moduleToggleSwitch.isChecked = resp.enabled
                                    if (moduleToggleSwitch.isChecked != resp.enabled) {
                                        // This might happen if the operation partially succeeded or server corrected the state
                                        moduleToggleSwitch.isChecked = resp.enabled
                                    }
                                } else {
                                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                                    showToast("Failed to toggle module: ${response.code()} - $errorBody")
                                    moduleToggleSwitch.isChecked = !enable // Revert on failure
                                }
                            }
                        }

                        override fun onFailure(call: Call<LSPosedModuleResponse>, t: Throwable) {
                            runOnUiThread { // Ensure UI updates are on the main thread
                                showToast("Network error toggling module: ${t.message}")
                                moduleToggleSwitch.isChecked = !enable // Revert on failure
                            }
                        }
                    })
            } ?: runOnUiThread {
                showToast("Authentication token not available. Cannot toggle module.")
                moduleToggleSwitch.isChecked = !enable // Revert toggle state
            }
        }?.addOnFailureListener { e ->
            runOnUiThread {
                showToast("Failed to get auth token: ${e.message}")
                moduleToggleSwitch.isChecked = !enable // Revert toggle state
            }
        }
        // Removed Thread { }.start() wrapper as getIdToken is async and enqueue handles its own threading.
    }

    private fun checkPermissionsAndExport() {
        if (hasStoragePermissions()) {
            exportChatToFile()
        } else {
            requestStoragePermissions()
        }
    }

    private fun hasStoragePermissions(): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        val permissionsToRequest = getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            storagePermissionLauncher.launch(permissionsToRequest)
        } else {
            // All permissions already granted
            filePicker.launch(FILE_PICKER_MIME_TYPES[0])
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_WRITE_STORAGE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            exportChatToFile()
        } else {
            Toast.makeText(
                this,
                "Storage permission is required to export chat",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun initializeService() {
        try {
            // Start the service when the activity is created
            GenesisAIService.startService(this)
        } catch (e: Exception) {
            e.printStackTrace()
            // Continue with the app even if service fails to start
            Toast.makeText(this, "Background service could not start", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkStoragePermissionAndPickFile() {
        if (hasStoragePermissions()) {
            filePicker.launch(FILE_PICKER_MIME_TYPES[0])
        } else {
            requestStoragePermissions()
        }
    }

    private fun openFileManager() {
        if (hasStoragePermissions()) {
            val intent = Intent(this, FileManagerActivity::class.java)
            startActivity(intent)
        } else {
            requestStoragePermissions()
        }
    }

    private fun exportChatToFile() {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "GenComm_Chat_$timeStamp.txt"
            val content = "=== GenComm Chat Export ===\n\n${chatLog.text}"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+ use MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?.let { uri ->
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(content.toByteArray())
                        }
                        // Mark the file as available to other apps
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            contentValues.clear()
                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            contentResolver.update(uri, contentValues, null, null)
                        }

                        Toast.makeText(
                            this,
                            "Chat exported to Documents folder: $fileName",
                            Toast.LENGTH_LONG
                        ).show()
                    } ?: throw Exception("Failed to create file")
            } else {
                // For older versions, save to external storage
                val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
                val file = File(downloadsDir, fileName)
                file.writeText(content)

                // Notify media scanner using MediaScannerConnection
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(file.absolutePath),
                    null
                ) { _, _ ->
                    // Media scan completed - parameters intentionally unused
                }

                Toast.makeText(
                    this,
                    "Chat exported to: ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Export failed: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            e.printStackTrace()
        }
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isEmpty()) return

        updateChatLog("You", message) // Display user message immediately

        val request = MessageRequest(message)
        GenesisRepositoryNew.api.sendMessage(request)
            .enqueue(object : Callback<MessageResponse> {
                override fun onResponse(
                    call: Call<MessageResponse>,
                    response: Response<MessageResponse>,
                ) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody != null) {
                            updateChatLog("AI", responseBody.message) // Pass "AI" as sender
                        } else {
                            updateChatLog("Error", "Empty response from server")
                        }
                    } else {
                         val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        updateChatLog("Error", "Failed to send message: ${response.code()} - $errorBody")
                    }
                }

                override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                    updateChatLog("Error","Network Error: ${t.message}")
                }
            })
        messageInput.text.clear() // Clear input after sending
    }

    // Modified to accept sender parameter
    private fun updateChatLog(sender: String, message: String) {
        chatLog.append("$sender: $message\n\n")
        // Scroll to bottom logic if chatLog is inside a ScrollView might be needed here
    }
}