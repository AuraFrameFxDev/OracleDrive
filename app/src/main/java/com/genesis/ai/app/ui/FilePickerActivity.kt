package com.genesis.ai.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class FilePickerActivity : AppCompatActivity() {
    /**
     * Initializes the activity and launches a file picker for selecting a JSON file.
     *
     * @param savedInstanceState The previously saved instance state, if any.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/json"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(Intent.createChooser(intent, "Select Backup JSON"), 1001)
    }
    /**
     * Handles the result of the file picker activity.
     *
     * If a JSON file is successfully selected, reads its content and returns it as a string extra in the result intent.
     * If no file is selected or the operation is canceled, sets the result as canceled and finishes the activity.
     *
     * @param requestCode The integer request code originally supplied to startActivityForResult().
     * @param resultCode The integer result code returned by the child activity.
     * @param data An Intent, which can return result data to the caller.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                val inputStream = contentResolver.openInputStream(uri)
                val json = inputStream?.bufferedReader()?.readText() ?: ""
                val resultIntent = Intent()
                resultIntent.putExtra("json", json)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
}
