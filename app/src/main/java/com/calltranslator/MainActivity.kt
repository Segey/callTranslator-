package com.calltranslator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var enableSwitch: SwitchCompat
    private lateinit var overlayBtn: Button
    private lateinit var permissionsBtn: Button

    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Все разрешения необходимы для работы", Toast.LENGTH_LONG).show()
        }
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        enableSwitch = findViewById<SwitchCompat>(R.id.enableSwitch)
        overlayBtn = findViewById(R.id.overlayPermissionBtn)
        permissionsBtn = findViewById(R.id.permissionsBtn)

        permissionsBtn.setOnClickListener {
            permissionLauncher.launch(requiredPermissions)
        }

        overlayBtn.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            prefs.edit().putBoolean("enabled", isChecked).apply()
            if (isChecked) {
                Toast.makeText(this, "Переводчик включён. Включайте громкую связь при звонке.", Toast.LENGTH_LONG).show()
            }
        }

        // Restore saved state
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        enableSwitch.isChecked = prefs.getBoolean("enabled", true)

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val hasPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        val hasOverlay = Settings.canDrawOverlays(this)

        val status = buildString {
            appendLine("Статус разрешений:")
            appendLine("• Микрофон: ${if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "✓" else "✗"}")
            appendLine("• Телефон: ${if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) "✓" else "✗"}")
            appendLine("• Overlay: ${if (hasOverlay) "✓" else "✗"}")
            appendLine()
            if (hasPermissions && hasOverlay) {
                appendLine("Готов к работе.")
                appendLine("При звонке включи громкую связь.")
            } else {
                appendLine("Выдай все разрешения для работы.")
            }
        }
        statusText.text = status

        permissionsBtn.text = if (hasPermissions) "Разрешения выданы ✓" else "Выдать разрешения"
        overlayBtn.text = if (hasOverlay) "Overlay выдан ✓" else "Разрешить overlay"
    }
}
