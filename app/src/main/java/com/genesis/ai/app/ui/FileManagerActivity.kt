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

    private fun loadFiles() {
        val files = currentDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        adapter.submitList(files)
        title = currentDir.absolutePath
    }

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

    override fun onBackPressed() {
        if (currentDir.parentFile != null && currentDir.absolutePath != "/data/adb/modules") {
            currentDir = currentDir.parentFile
            loadFiles()
        } else {
            super.onBackPressed()
        }
    }
}
