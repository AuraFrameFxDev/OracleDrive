package com.genesis.ai.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.genesis.ai.app.R
import java.io.File

class FileManagerActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter
    private var currentDir: File = File("/data/adb/modules")

    /**
     * Initializes the file manager activity, setting up the UI and displaying the contents of the root directory.
     *
     * Sets the layout, configures the RecyclerView with a file adapter, and loads the initial list of files and directories.
     * Handles item clicks to navigate into directories or open files.
     *
     * @param savedInstanceState The previously saved instance state, if any.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter { file ->
            if (file.isDirectory) {
                currentDir = file
                loadFiles()
            } else {
                openFile(file)
            }
        }
        recyclerView.adapter = adapter
        loadFiles()
    }

    /**
     * Loads and displays the contents of the current directory, sorting directories before files and updating the activity title.
     */
    private fun loadFiles() {
        val files = currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        adapter.submitList(files)
        title = currentDir.absolutePath
    }

    /**
     * Attempts to open the specified file using an appropriate external app.
     *
     * If no suitable app is available to handle the file, displays a toast message to inform the user.
     *
     * @param file The file to be opened.
     */
    private fun openFile(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.fromFile(file), "*/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open this file.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Handles the back button press to navigate up one directory level if possible.
     *
     * If the current directory is not the root (`/data/adb/modules`), navigates to its parent directory and reloads the file list. Otherwise, performs the default back action.
     */
    override fun onBackPressed() {
        if (currentDir.parentFile != null && currentDir.absolutePath != "/data/adb/modules") {
            currentDir = currentDir.parentFile
            loadFiles()
        } else {
            super.onBackPressed()
        }
    }
}
