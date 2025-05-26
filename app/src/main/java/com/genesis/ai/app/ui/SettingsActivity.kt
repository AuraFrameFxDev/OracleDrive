package com.genesis.ai.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.genesis.ai.app.R
import com.genesis.ai.app.utils.PreferenceHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var preferenceHelper: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        preferenceHelper = PreferenceHelper(this)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        saveButton = findViewById(R.id.saveButton)

        // Load saved API key if exists
        preferenceHelper.getApiKey()?.let {
            apiKeyInput.setText(it)
        }

        saveButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isNotEmpty()) {
                preferenceHelper.saveApiKey(apiKey)
                Toast.makeText(this, "API Key saved", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Please enter an API Key", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
