package com.genesis.ai.app.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.genesis.ai.app.R

class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val helpText = """
            <b>LSPosed Manager Help</b><br><br>
            <b>Modules:</b> Visualize, enable/disable, backup, and restore LSPosed modules.<br><br>
            <b>Backup/Restore:</b> Use the buttons to export or import your module configuration.<br><br>
            <b>File Manager:</b> Organize and clean up module files.<br><br>
            <b>Dark Mode:</b> Enable in system settings for a better experience.<br><br>
            <b>Security:</b> Only trusted modules should be enabled. Always keep backups.<br><br>
            <b>Need more help?</b> Contact support or visit the project homepage.
        """
        AlertDialog.Builder(this)
            .setTitle("Help & Info")
            .setMessage(android.text.Html.fromHtml(helpText))
            .setPositiveButton("OK", null)
            .show()
    }
}
