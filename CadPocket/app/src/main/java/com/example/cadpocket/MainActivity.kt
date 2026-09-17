package com.example.cadpocket

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var dxfView: DxfView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dxfView = findViewById(R.id.dxfView)
        statusText = findViewById(R.id.statusText)
        findViewById<Button>(R.id.openButton).setOnClickListener { openFile() }
        findViewById<Button>(R.id.fitButton).setOnClickListener { dxfView.fitToScreen() }
    }

    private fun openFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/dxf", "application/x-dxf", "text/plain", "application/octet-stream"))
        }
        startActivityForResult(intent, 1001)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1001 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        loadDxf(uri)
    }

    private fun loadDxf(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Dosya okunamadı")
            val drawing = DxfParser.parse(text)
            dxfView.setDrawing(drawing)
            statusText.text = "${displayName(uri)} • ${drawing.entities.size} nesne"
        } catch (e: Exception) {
            statusText.text = "Dosya açılamadı"
            Toast.makeText(this, e.message ?: "Bilinmeyen hata", Toast.LENGTH_LONG).show()
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return "DXF"
    }
}
