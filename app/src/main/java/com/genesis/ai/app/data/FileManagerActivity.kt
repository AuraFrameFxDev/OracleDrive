package com.genesis.ai.app.data

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.genesis.ai.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManagerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSelect: Button
    private lateinit var btnExport: Button
    private lateinit var btnCancel: Button
    private lateinit var currentPathText: TextView
    private lateinit var currentDirectory: File
    private var selectedFile: File? = null

    private val createFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            uri?.let { exportChatToFile(it) }
        }

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importFileFromUri(it) }
        }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadFiles(currentDirectory)
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        // Initialize views
        recyclerView = findViewById(R.id.rvFiles)
        btnSelect = findViewById(R.id.btnSelect)
        btnExport = findViewById(R.id.btnExportFile)
        btnCancel = findViewById(R.id.btnCancel)
        currentPathText = findViewById(R.id.tvCurrentPath)

        // Set click listeners
        btnSelect.setOnClickListener {
            selectedFile?.let { file ->
                val resultIntent = Intent().apply {
                    data = Uri.fromFile(file)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }

        btnExport.setOnClickListener {
            checkStoragePermission()
        }

        btnCancel.setOnClickListener {
            finish()
        }

        // Initialize UI
        setupRecyclerView()

        // Set initial directory
        currentDirectory = Environment.getExternalStorageDirectory()
        checkStoragePermission()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = FileAdapter(emptyList()) { file ->
            if (file.isDirectory) {
                val message = getString(R.string.file_exported, file.absolutePath)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                // Update UI to show selection
                (recyclerView.adapter as? FileAdapter)?.setSelectedFile(file)
            } else {
                selectedFile = file
                btnSelect.isEnabled = true
                // Update UI to show selection
                (recyclerView.adapter as? FileAdapter)?.setSelectedFile(file)
            }
        }
    }

    private fun loadFiles(directory: File) {
        if (!directory.exists() || !directory.isDirectory) {
            Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_SHORT).show()
            return
        }

        currentDirectory = directory

        val files = directory.listFiles()?.toList() ?: emptyList()
        (recyclerView.adapter as? FileAdapter)?.updateFiles(files)
    }

    private fun importFileFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = getFileName(uri) ?: "imported_file_${System.currentTimeMillis()}"
                val file = File(currentDirectory, fileName)

                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                Toast.makeText(this, "File imported successfully", Toast.LENGTH_SHORT).show()
                loadFiles(currentDirectory)
            }
        } catch (e: Exception) {
            val errorMsg = "Error reading file: ${e.message ?: "Unknown error"}"
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun prepareExport() {
        selectedFile?.let { file ->
            createFile.launch("*/*")
        }
    }

    private fun exportChatToFile(uri: Uri) {
        selectedFile?.let { file ->
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                Toast.makeText(this, "File exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val errorMsg = "Error writing file: ${e.message ?: "Unknown error"}"
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "No file selected for export", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkStoragePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        // Manage External Storage permission granted
                        loadFiles(currentDirectory)
                    } else {
                        // Request Manage External Storage permission
                        try {
                            val intent =
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.addCategory("android.intent.category.DEFAULT")
                            intent.data = Uri.parse(
                                String.format(
                                    "package:%s",
                                    applicationContext.packageName
                                )
                            )
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        }
                    }
                } else {
                    // For devices below Android 11, request WRITE_EXTERNAL_STORAGE permission
                    loadFiles(currentDirectory)
                }
            }

            shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                // Show an explanation to the user
                showPermissionRationaleDialog()
            }

            else -> {
                // Request the permission using the new API
                requestPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Storage permission is required to manage files")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("Storage permission is required to manage files. The app will now exit.")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex =
                        cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "zip" -> "application/zip"
            else -> "*/*"
        }
    }

    companion object {
        private const val REQUEST_MANAGE_STORAGE = 1000
        private const val EXPORT_FILE_REQUEST = 1001
    }
}

class FileAdapter(
    private var files: List<File>,
    private val onFileClick: (File) -> Unit,
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private var selectedFile: File? = null

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.tvFileName)
        val fileInfo: TextView = view.findViewById(R.id.tvFileInfo)
        val fileIcon: View = view.findViewById(R.id.ivFileIcon)
        val checkBox: View = view.findViewById(R.id.cbSelect)
    }

    fun updateFiles(newFiles: List<File>) {
        files = newFiles.sortedWith(
            compareBy(
                { !it.isDirectory },
                { it.name.lowercase() }
            ))
        notifyDataSetChanged()
    }

    fun setSelectedFile(file: File) {
        selectedFile = file
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): FileViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        val isSelected = file == selectedFile

        holder.fileName.text = file.name

        // Set file info (size and date)
        val fileInfo = StringBuilder()
        if (file.isFile) {
            fileInfo.append(formatFileSize(file.length()))
            fileInfo.append(" • ")
        }
        fileInfo.append(
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .format(Date(file.lastModified()))
        )

        holder.fileInfo.text = fileInfo

        // Set icon based on file type
        val iconRes = when {
            file.isDirectory -> android.R.drawable.ic_menu_upload
            file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif") ->
                android.R.drawable.ic_dialog_alert

            file.extension.lowercase() in listOf("pdf") -> android.R.drawable.ic_menu_upload
            file.extension.lowercase() in listOf("doc", "docx") -> android.R.drawable.ic_menu_edit
            file.extension.lowercase() in listOf("xls", "xlsx") -> android.R.drawable.ic_menu_edit
            file.extension.lowercase() in listOf(
                "zip",
                "rar"
            ) -> android.R.drawable.ic_menu_upload_you_tube

            else -> android.R.drawable.ic_menu_upload
        }

        holder.fileIcon.setBackgroundResource(iconRes)

        // Show/hide checkbox based on selection
        holder.checkBox.visibility = if (isSelected) View.VISIBLE else View.GONE

        // Set click listener
        holder.itemView.setOnClickListener {
            onFileClick(file)
        }
    }

    override fun getItemCount() = files.size

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
}
