package com.genesis.ai.app.data

import android.Manifest
import android.annotation.SuppressLint // ADDED IMPORT
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.genesis.ai.app.R
import kotlinx.coroutines.*
import java.io.File
import com.genesis.ai.app.GenesisApplication // ADDED IMPORT
import com.genesis.ai.app.data.logging.OracleDriveLogger // ADDED IMPORT
import java.text.SimpleDateFormat // ADDED IMPORT
import java.util.Date // ADDED IMPORT
import java.util.Locale // ADDED IMPORT


class FileManagerActivity : AppCompatActivity() {
    private val fileLoadScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentLoadJob: Job? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSelect: Button
    private lateinit var btnExport: Button
    private lateinit var btnCancel: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var currentPathText: TextView
    private lateinit var currentDirectory: File
    private var selectedFile: File? = null

    private lateinit var oracleDriveLogger: OracleDriveLogger // ADDED
    private val TAG = "FileManagerActivity" // ADDED

    private val createFileLauncher = // Renamed from createFile to avoid confusion
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            oracleDriveLogger.d(TAG, "createFileLauncher result URI: $uri")
            uri?.let { exportSelectedFile(it) } ?: oracleDriveLogger.w(TAG, "CreateDocument URI was null for export.")
        }

    private val openDocumentLauncher = // Renamed from openDocument
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            oracleDriveLogger.d(TAG, "openDocumentLauncher result URI: $uri")
            uri?.let { importFileFromUri(it) } ?: oracleDriveLogger.w(TAG, "OpenDocument URI was null for import.")
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            oracleDriveLogger.i(TAG, "Storage permission (WRITE_EXTERNAL_STORAGE) granted via launcher: $isGranted")
            if (isGranted) {
                loadFiles(currentDirectory)
            } else {
                showPermissionDeniedDialog("Write External Storage permission is required.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oracleDriveLogger = (application as GenesisApplication).oracleDriveLogger
        oracleDriveLogger.i(TAG, "onCreate called.")

        setContentView(R.layout.activity_file_manager)
        oracleDriveLogger.d(TAG, "Content view set.")

        recyclerView = findViewById(R.id.rvFiles)
        btnSelect = findViewById(R.id.btnSelect)
        btnExport = findViewById(R.id.btnExportFile)
        btnCancel = findViewById(R.id.btnCancel)
        btnSettings = findViewById(R.id.btnSettings)
        currentPathText = findViewById(R.id.tvCurrentPath)
        oracleDriveLogger.d(TAG, "Views initialized.")

        btnSelect.setOnClickListener {
            val localSelectedFile = selectedFile
            oracleDriveLogger.d(TAG, "Select button clicked. Selected file: ${localSelectedFile?.name}")
            localSelectedFile?.let { file ->
                val resultIntent = Intent().apply { data = Uri.fromFile(file) }
                setResult(RESULT_OK, resultIntent)
                finish()
            } ?: oracleDriveLogger.w(TAG, "Select button clicked but no file was selected.")
        }

        btnExport.setOnClickListener {
            oracleDriveLogger.d(TAG, "Export button clicked.")
            if (selectedFile != null) {
                oracleDriveLogger.i(TAG, "Attempting to export file: ${selectedFile?.name}")
                checkStoragePermissionAndPrepareExport()
            } else {
                oracleDriveLogger.w(TAG, "Export button clicked but no file selected.")
                Toast.makeText(this, "No file selected for export", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener {
            oracleDriveLogger.d(TAG, "Cancel button clicked. Finishing activity.")
            finish()
        }

        btnSettings.setOnClickListener {
            oracleDriveLogger.d(TAG, "Settings button clicked.")
            try {
                oracleDriveLogger.i(TAG, "Navigating to SettingsActivity.")
                startActivity(Intent(this, com.genesis.ai.app.ui.SettingsActivity::class.java))
            } catch (e: Exception) {
                oracleDriveLogger.e(TAG, "Failed to start SettingsActivity: ${e.message}", e)
                Toast.makeText(this, "Could not open settings.", Toast.LENGTH_SHORT).show()
            }
        }
        oracleDriveLogger.d(TAG, "Click listeners set.")

        setupRecyclerView()
        currentDirectory = Environment.getExternalStorageDirectory()
        oracleDriveLogger.i(TAG, "Initial directory set to: ${currentDirectory.absolutePath}")
        checkStoragePermissionAndLoadFiles()
        oracleDriveLogger.i(TAG, "onCreate completed.")
    }

    private fun checkStoragePermissionAndPrepareExport() {
        oracleDriveLogger.d(TAG, "checkStoragePermissionAndPrepareExport called.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                oracleDriveLogger.i(TAG, "MANAGE_EXTERNAL_STORAGE permission already granted (Android R+). Preparing export.")
                prepareExport()
            } else {
                oracleDriveLogger.w(TAG, "MANAGE_EXTERNAL_STORAGE permission NOT granted (Android R+). Requesting.")
                requestManageStoragePermission()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                oracleDriveLogger.i(TAG, "WRITE_EXTERNAL_STORAGE permission already granted (pre-Android R). Preparing export.")
                prepareExport()
            } else {
                oracleDriveLogger.w(TAG, "WRITE_EXTERNAL_STORAGE permission NOT granted (pre-Android R). Requesting via launcher.")
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun checkStoragePermissionAndLoadFiles() {
        oracleDriveLogger.d(TAG, "checkStoragePermissionAndLoadFiles for dir: ${currentDirectory.absolutePath}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                oracleDriveLogger.i(TAG, "MANAGE_EXTERNAL_STORAGE permission granted (Android R+). Loading files.")
                loadFiles(currentDirectory)
            } else {
                oracleDriveLogger.w(TAG, "MANAGE_EXTERNAL_STORAGE permission NOT granted (Android R+). Requesting.")
                requestManageStoragePermission()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                oracleDriveLogger.i(TAG, "READ_EXTERNAL_STORAGE permission granted (pre-Android R). Loading files.")
                loadFiles(currentDirectory)
            } else {
                oracleDriveLogger.w(TAG, "READ_EXTERNAL_STORAGE permission NOT granted (pre-Android R). Requesting WRITE as proxy for launcher.")
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = FileAdapter(emptyList()) { file ->
            oracleDriveLogger.d(TAG, "File clicked in adapter: ${file.name}, isDirectory: ${file.isDirectory}")
            if (file.isDirectory) {
                oracleDriveLogger.i(TAG, "Directory selected: ${file.absolutePath}. Navigating.")
                currentDirectory = file
                checkStoragePermissionAndLoadFiles()
                selectedFile = null
                btnSelect.isEnabled = false
            } else {
                selectedFile = file
                btnSelect.isEnabled = true
                oracleDriveLogger.i(TAG, "File selected: ${file.name}")
            }
            (recyclerView.adapter as? FileAdapter)?.setSelectedFile(file)
        }
        oracleDriveLogger.d(TAG, "RecyclerView setup complete.")
    }

    private fun loadFiles(directory: File) {
        oracleDriveLogger.i(TAG, "Loading files from directory: ${directory.absolutePath}")
        currentLoadJob?.cancel()
        currentPathText.text = getString(R.string.loading)

        currentLoadJob = fileLoadScope.launch {
            try {
                if (!directory.exists() || !directory.isDirectory) {
                    oracleDriveLogger.e(TAG, "Cannot load: Directory does not exist or is not a directory: ${directory.absolutePath}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FileManagerActivity, getString(R.string.directory_not_found), Toast.LENGTH_SHORT).show()
                        currentPathText.text = getString(R.string.directory_not_found)
                    }
                    return@launch
                }

                oracleDriveLogger.d(TAG, "Listing files in ${directory.absolutePath} on IO thread.")
                val files = withContext(Dispatchers.IO) { directory.listFiles()?.toList() ?: emptyList() }
                oracleDriveLogger.d(TAG, "Found ${files.size} files/dirs in ${directory.absolutePath}.")

                withContext(Dispatchers.Main) {
                    currentPathText.text = directory.absolutePath
                    (recyclerView.adapter as? FileAdapter)?.updateFiles(files)
                    oracleDriveLogger.d(TAG, "UI updated with files from ${directory.absolutePath}.")
                }
            } catch (e: SecurityException) {
                 oracleDriveLogger.e(TAG, "SecurityException loading files from ${directory.absolutePath}: ${e.message}", e)
                 withContext(Dispatchers.Main) {
                    Toast.makeText(this@FileManagerActivity, "Permission denied for ${directory.absolutePath}", Toast.LENGTH_SHORT).show()
                    currentPathText.text = "Permission Denied"
                 }
            } catch (e: Exception) {
                oracleDriveLogger.e(TAG, "Error loading files from ${directory.absolutePath}: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FileManagerActivity, getString(R.string.error_loading_files, e.localizedMessage), Toast.LENGTH_SHORT).show()
                    currentPathText.text = getString(R.string.error_loading_files, "")
                }
            }
        }
    }

    private fun importFileFromUri(uri: Uri) {
        oracleDriveLogger.i(TAG, "Importing file from URI: $uri")
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = getFileName(uri) ?: "imported_file_${System.currentTimeMillis()}"
                val file = File(currentDirectory, fileName)
                oracleDriveLogger.d(TAG, "Target file for import: ${file.absolutePath}")

                file.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
                oracleDriveLogger.i(TAG, "File imported successfully to ${file.absolutePath}")
                Toast.makeText(this, "File imported successfully", Toast.LENGTH_SHORT).show()
                loadFiles(currentDirectory)
            } ?: oracleDriveLogger.e(TAG, "Failed to open input stream for import URI: $uri")
        } catch (e: Exception) {
            val errorMsg = "Error importing file from URI $uri: ${e.message ?: "Unknown error"}"
            oracleDriveLogger.e(TAG, errorMsg, e)
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun prepareExport() {
        val localSelectedFile = selectedFile
        oracleDriveLogger.d(TAG, "prepareExport called for file: ${localSelectedFile?.name}")
        localSelectedFile?.let {
            createFileLauncher.launch(it.name)
        } ?: oracleDriveLogger.w(TAG, "prepareExport called but no file selected.")
    }

    private fun exportSelectedFile(uri: Uri) {
        oracleDriveLogger.i(TAG, "Exporting selected file to URI: $uri. Selected file: ${selectedFile?.name}")
        val fileToExport = selectedFile
        if (fileToExport == null) {
            oracleDriveLogger.w(TAG, "exportSelectedFile called but selectedFile is null.")
            Toast.makeText(this, "No file selected for export", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                fileToExport.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
            }
            oracleDriveLogger.i(TAG, "File exported successfully: ${fileToExport.name} to $uri")
            Toast.makeText(this, "File exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            val errorMsg = "Error exporting file ${fileToExport.name} to $uri: ${e.message ?: "Unknown error"}"
            oracleDriveLogger.e(TAG, errorMsg, e)
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestManageStoragePermission() {
        oracleDriveLogger.i(TAG, "Requesting MANAGE_APP_ALL_FILES_ACCESS_PERMISSION (Android R+)")
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
            startActivityForResult(intent, REQUEST_MANAGE_STORAGE_PERMISSION_CODE)
        } catch (e: Exception) {
            oracleDriveLogger.e(TAG, "Error starting intent for MANAGE_APP_ALL_FILES_ACCESS_PERMISSION (fallback): ${e.message}", e)
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE_PERMISSION_CODE)
            } catch (ex: Exception) {
                 oracleDriveLogger.e(TAG, "Fallback for MANAGE_ALL_FILES_ACCESS_PERMISSION also failed: ${ex.message}", ex)
                 Toast.makeText(this, "Could not open settings to grant file access.", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        oracleDriveLogger.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        if (requestCode == REQUEST_MANAGE_STORAGE_PERMISSION_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    oracleDriveLogger.i(TAG, "MANAGE_APP_ALL_FILES_ACCESS_PERMISSION granted via onActivityResult.")
                    loadFiles(currentDirectory)
                } else {
                    oracleDriveLogger.w(TAG, "MANAGE_APP_ALL_FILES_ACCESS_PERMISSION NOT granted via onActivityResult.")
                    showPermissionDeniedDialog("Manage all files access is required to list files.")
                }
            }
        }
    }

    private fun showPermissionRationaleDialog() {
        oracleDriveLogger.w(TAG, "Showing permission rationale dialog for WRITE_EXTERNAL_STORAGE.")
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Storage permission is required to manage files for older Android versions.")
            .setPositiveButton("Grant Permission") { _, _ ->
                oracleDriveLogger.d(TAG, "User clicked 'Grant Permission' from rationale.")
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                oracleDriveLogger.d(TAG, "User clicked 'Cancel' from rationale.")
                dialog.dismiss()
            }
            .show()
    }

    private fun showPermissionDeniedDialog(message: String = "Storage permission is essential for this feature. The screen will close.") {
        oracleDriveLogger.e(TAG, "Showing permission denied dialog: $message")
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                oracleDriveLogger.d(TAG, "User clicked 'OK' from permission denied. Finishing activity.")
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            result = cursor.getString(nameIndex)
                        } else {
                            oracleDriveLogger.w(TAG, "DISPLAY_NAME column not found for URI: $uri")
                        }
                    } else {
                        oracleDriveLogger.w(TAG, "Cursor empty for URI: $uri")
                    }
                }
            } catch (e: Exception) {
                oracleDriveLogger.w(TAG, "Error querying content resolver for file name: $uri. Error: ${e.message}", e)
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        oracleDriveLogger.v(TAG, "getFileName for URI $uri, result: $result")
        return result
    }

    override fun onDestroy() {
        super.onDestroy()
        oracleDriveLogger.i(TAG, "onDestroy called.")
        currentLoadJob?.cancel()
        fileLoadScope.cancel()
        oracleDriveLogger.d(TAG, "Coroutine scopes cancelled in onDestroy.")
    }

    companion object {
        private const val REQUEST_MANAGE_STORAGE_PERMISSION_CODE = 1000
        // private val TAG_STATIC = "FileManagerActivityCompanion" // Using instance TAG
    }
}

// FileAdapter class - NO CHANGES IN THIS SUBTASK
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

    private val fileComparator = compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }

    @SuppressLint("NotifyDataSetChanged")
    fun updateFiles(newFiles: List<File>) {
        val oldList = files
        files = newFiles.sortedWith(fileComparator)
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object :
            androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = files.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return oldList[oldPos].absolutePath == files[newPos].absolutePath
            }
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val oldFile = oldList[oldPos]
                val newFile = files[newPos]
                return oldFile.lastModified() == newFile.lastModified() &&
                        oldFile.length() == newFile.length() &&
                        oldFile.name == newFile.name
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setSelectedFile(file: File?) {
        val previousSelectedFile = selectedFile
        selectedFile = file
        previousSelectedFile?.let { oldFile ->
            val oldIndex = files.indexOf(oldFile)
            if (oldIndex != -1) notifyItemChanged(oldIndex)
        }
        selectedFile?.let { newFile ->
            val newIndex = files.indexOf(newFile)
            if (newIndex != -1) notifyItemChanged(newIndex)
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): FileViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    private var lastClickTime: Long = 0

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        val isSelected = file.absolutePath == selectedFile?.absolutePath

        holder.fileName.text = file.name
        val fileInfoText = if (file.isFile) {
            "${formatFileSize(file.length())} • ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(file.lastModified()))}"
        } else {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(file.lastModified()))
        }
        holder.fileInfo.text = fileInfoText

        val iconRes = when {
            file.isDirectory -> R.drawable.ic_folder_selector
            file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif") -> R.drawable.ic_image_selector
            file.extension.lowercase() in listOf("pdf") -> R.drawable.ic_pdf_selector
            file.extension.lowercase() in listOf("doc", "docx") -> R.drawable.ic_doc_selector
            file.extension.lowercase() in listOf("xls", "xlsx") -> R.drawable.ic_xls_selector
            file.extension.lowercase() in listOf("zip", "rar") -> R.drawable.ic_zip_selector
            else -> R.drawable.ic_file_selector
        }

        if (holder.fileIcon is android.widget.ImageView) {
            (holder.fileIcon as android.widget.ImageView).setImageResource(iconRes)
        } else {
             holder.fileIcon.setBackgroundResource(iconRes)
        }
        holder.checkBox.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime > 300) {
                lastClickTime = now
                onFileClick(file)
            }
        }
    }

    override fun getItemCount() = files.size

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
