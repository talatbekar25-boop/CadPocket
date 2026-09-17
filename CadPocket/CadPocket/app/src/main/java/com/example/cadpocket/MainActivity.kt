package com.example.cadpocket

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var dxfView: DxfView
    private lateinit var statusText: TextView
    private lateinit var coordinateText: TextView
    private lateinit var measureButton: Button
    private var currentDrawing: Drawing? = null
    private var selectedLayers: MutableSet<String> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dxfView = findViewById(R.id.dxfView)
        statusText = findViewById(R.id.statusText)
        coordinateText = findViewById(R.id.coordinateText)
        measureButton = findViewById(R.id.measureButton)

        findViewById<Button>(R.id.openButton).setOnClickListener { openFile() }
        findViewById<Button>(R.id.sampleButton).setOnClickListener { openSample() }
        findViewById<Button>(R.id.fitButton).setOnClickListener { dxfView.fitToScreen() }
        findViewById<Button>(R.id.layersButton).setOnClickListener { showLayersDialog() }
        measureButton.setOnClickListener { toggleMeasure() }
        findViewById<Button>(R.id.clearMeasureButton).setOnClickListener { dxfView.clearMeasurement() }

        dxfView.onCoordinatePicked = { pt ->
            coordinateText.text = String.format(Locale.US, "X: %.2f   Y: %.2f", pt.x, pt.y)
        }
        dxfView.onMeasurementChanged = { distance ->
            statusText.text = when {
                dxfView.isMeasureMode() && distance == null -> "Ölçüm: ilk ve ikinci noktaya dokunun"
                distance != null -> String.format(Locale.US, "Mesafe: %.3f birim", distance)
                currentDrawing != null -> drawingSummary()
                else -> "DXF seçin veya ÖRNEK AÇ düğmesine basın"
            }
        }
    }

    private fun toggleMeasure() {
        val enabled = !dxfView.isMeasureMode()
        dxfView.setMeasureMode(enabled)
        measureButton.text = if (enabled) "ÖLÇÜM AÇIK" else "ÖLÇ"
        statusText.text = if (enabled) {
            "Ölçüm: iki noktaya sırayla dokunun"
        } else {
            drawingSummary()
        }
    }

    private fun showLayersDialog() {
        val drawing = currentDrawing
        if (drawing == null) {
            Toast.makeText(this, "Önce bir DXF açın", Toast.LENGTH_SHORT).show()
            return
        }
        val layers = drawing.layers.toTypedArray()
        val checked = BooleanArray(layers.size) { layers[it] in selectedLayers }
        AlertDialog.Builder(this)
            .setTitle("Katmanlar")
            .setMultiChoiceItems(layers, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("UYGULA") { _, _ ->
                selectedLayers.clear()
                layers.forEachIndexed { index, name -> if (checked[index]) selectedLayers += name }
                dxfView.setVisibleLayers(selectedLayers)
                statusText.text = "${selectedLayers.size}/${layers.size} katman görünür"
            }
            .setNeutralButton("TÜMÜ") { _, _ ->
                selectedLayers = layers.toMutableSet()
                dxfView.setVisibleLayers(selectedLayers)
                statusText.text = drawingSummary()
            }
            .setNegativeButton("İPTAL", null)
            .show()
    }

    private fun openFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
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
        }

        val name = displayName(uri)
        if (!name.lowercase().endsWith(".dxf")) {
            Toast.makeText(this, "Bu dosya DXF değil: $name", Toast.LENGTH_LONG).show()
            statusText.text = "DXF dosyası seçin"
            return
        }
        loadDxf(uri, name)
    }

    private fun loadDxf(uri: Uri, name: String) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Dosya okunamadı")
            showDrawing(text, name)
        } catch (e: Exception) {
            showError(e)
        }
    }

    private fun openSample() {
        try {
            val text = assets.open("ornek.dxf").bufferedReader().use { it.readText() }
            showDrawing(text, "ornek.dxf")
        } catch (e: Exception) {
            showError(e)
        }
    }

    private fun showDrawing(text: String, name: String) {
        val drawing = DxfParser.parse(text)
        currentDrawing = drawing
        selectedLayers = drawing.layers.toMutableSet()
        dxfView.setDrawing(drawing)
        dxfView.setVisibleLayers(selectedLayers)
        dxfView.post { dxfView.fitToScreen() }
        statusText.tag = name
        statusText.text = drawingSummary()
        coordinateText.text = "X: --   Y: --"
        measureButton.text = "ÖLÇ"
    }

    private fun drawingSummary(): String {
        val drawing = currentDrawing ?: return "DXF seçin veya ÖRNEK AÇ düğmesine basın"
        val name = statusText.tag?.toString() ?: "DXF"
        return "$name • ${drawing.entities.size} nesne • ${drawing.layers.size} katman"
    }

    private fun showError(e: Exception) {
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        statusText.text = "Dosya açılamadı: $detail"
        Toast.makeText(this, "Dosya açılamadı: $detail", Toast.LENGTH_LONG).show()
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment ?: "DXF"
    }
}
