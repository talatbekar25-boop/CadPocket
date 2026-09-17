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
        findViewById<Button>(R.id.sampleButton).setOnClickListener { openSample() }
        findViewById<Button>(R.id.fitButton).setOnClickListener { dxfView.fitToScreen() }
    }

    private fun openFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Tum dosyalari goster. Bazi Android dosya secicileri DXF MIME turunu tanimadigi icin
            // yalnizca DXF filtresi kullanmak dosyanin gorunmemesine neden olabiliyor.
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
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
        } catch (_: Exception) {
            // Her dosya saglayici kalici izin desteklemez; normal okuma yine denenir.
        }

        val name = displayName(uri)
        if (!name.lowercase().endsWith(".dxf")) {
            Toast.makeText(this, "Bu dosya DXF degil: $name", Toast.LENGTH_LONG).show()
            statusText.text = "DXF dosyasi secin"
            return
        }
        loadDxf(uri, name)
    }

    private fun loadDxf(uri: Uri, name: String) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Dosya okunamadi")
            showDrawing(text, name)
        } catch (e: Exception) {
            showError(e)
        }
    }

    private fun openSample() {
        try {
            val text = resources.openRawResource(R.raw.ornek).bufferedReader().use { it.readText() }
            showDrawing(text, "ornek.dxf")
        } catch (e: Exception) {
            showError(e)
        }
    }

    private fun showDrawing(text: String, name: String) {
        val drawing = DxfParser.parse(text)
        if (drawing.entities.isEmpty()) {
            error("DXF okundu ancak desteklenen cizim nesnesi bulunamadi")
        }
        dxfView.setDrawing(drawing)
        dxfView.post { dxfView.fitToScreen() }
        statusText.text = "$name • ${drawing.entities.size} nesne"
    }

    private fun showError(e: Exception) {
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        statusText.text = "Dosya acilamadi: $detail"
        Toast.makeText(this, "Dosya acilamadi: $detail", Toast.LENGTH_LONG).show()
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment ?: "DXF"
    }
}
