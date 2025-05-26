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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.genesis.ai.app.R
import com.genesis.ai.app.data.model.GenesisRepositoryNew
import com.genesis.ai.app.data.model.ImportResponse
import com.genesis.ai.app.data.model.MessageRequest
import com.genesis.ai.app.data.model.MessageResponse
import com.genesis.ai.app.service.GenesisAIService
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
private const val FILE_PICKER_MIME_TYPE = "*/*"

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CODE_WRITE_STORAGE = 1001
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
    private lateinit var rootToggle: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var importButton: Button
    private lateinit var exportButton: Button
    private lateinit var fileManagerButton: Button
    private lateinit var aiQuestions: TextView

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
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            filePicker.launch(FILE_PICKER_MIME_TYPE)
        } else {
            Toast.makeText(this, "Storage permission required to import file", Toast.LENGTH_SHORT)
                .show()
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

        // Initialize views
        chatLog = findViewById(R.id.chatLog)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        rootToggle = findViewById(R.id.rootToggle)
        exportButton = findViewById(R.id.exportButton)
        aiQuestions = findViewById(R.id.aiQuestions)

        // Set up click listeners
        sendButton.setOnClickListener { sendMessage() }
        exportButton.setOnClickListener { checkPermissionsAndExport() }
        
        // Initialize the service after UI is ready
        initializeService()
    }
    
    private fun checkPermissionsAndExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || 
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            exportChatToFile()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_WRITE_STORAGE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_WRITE_STORAGE && 
            grantResults.isNotEmpty() && 
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            exportChatToFile()
        } else {
            Toast.makeText(this, "Storage permission is required to export chat", Toast.LENGTH_SHORT).show()
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // On Android 12 and below, check READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                filePicker.launch(FILE_PICKER_MIME_TYPE)
            }
        } else {
            // On Android 13+, permission is not required for file picker
            filePicker.launch(FILE_PICKER_MIME_TYPE)
        }
    }

    private fun openFileManager() {
        val intent = Intent(this, FileManagerActivity::class.java)
        fileManagerLauncher.launch(intent)
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
                            updateChatLog(
                                message,
                                responseBody.message
                            )
                        } else {
                            updateChatLog(message, "Error: Empty response from server")
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to send message",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<MessageResponse>, t: Throwable) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun updateChatLog(userMessage: String, aiResponse: String) {
        chatLog.append("You: $userMessage\n")
        chatLog.append("AI: $aiResponse\n\n")
        messageInput.text.clear()
    }
}