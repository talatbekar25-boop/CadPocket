package com.example.cadpocket

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class DxfView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val entityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2F2F2.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFC107.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val measurePointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFC107.toInt()
        style = Paint.Style.FILL
    }

    private var drawing: Drawing? = null
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var visibleLayers: Set<String> = emptySet()

    private var measureMode = false
    private var measureA: Pt? = null
    private var measureB: Pt? = null
    var onMeasurementChanged: ((Float?) -> Unit)? = null
    var onCoordinatePicked: ((Pt) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = scale
                scale = (scale * detector.scaleFactor).coerceIn(0.01f, 10000f)
                val fx = detector.focusX
                val fy = detector.focusY
                val ratio = scale / oldScale
                offsetX = fx - (fx - offsetX) * ratio
                offsetY = fy - (fy - offsetY) * ratio
                invalidate()
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (measureMode) return true
                offsetX -= dx
                offsetY -= dy
                invalidate()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val pt = screenToWorld(e.x, e.y)
                onCoordinatePicked?.invoke(pt)
                if (measureMode) addMeasurePoint(pt)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!measureMode) fitToScreen()
                return true
            }
        })

    fun setDrawing(d: Drawing) {
        drawing = d
        visibleLayers = d.layers.toSet()
        clearMeasurement()
        post { fitToScreen() }
    }

    fun setVisibleLayers(layers: Set<String>) {
        visibleLayers = layers
        invalidate()
    }

    fun setMeasureMode(enabled: Boolean) {
        measureMode = enabled
        if (!enabled) clearMeasurement()
        invalidate()
    }

    fun isMeasureMode(): Boolean = measureMode

    fun clearMeasurement() {
        measureA = null
        measureB = null
        onMeasurementChanged?.invoke(null)
        invalidate()
    }

    private fun addMeasurePoint(pt: Pt) {
        if (measureA == null || measureB != null) {
            measureA = pt
            measureB = null
            onMeasurementChanged?.invoke(null)
        } else {
            measureB = pt
            val a = measureA!!
            val d = hypot((pt.x - a.x).toDouble(), (pt.y - a.y).toDouble()).toFloat()
            onMeasurementChanged?.invoke(d)
        }
        invalidate()
    }

    private fun screenToWorld(x: Float, y: Float): Pt =
        Pt((x - offsetX) / scale, -(y - offsetY) / scale)

    fun fitToScreen() {
        val d = drawing ?: return
        if (width == 0 || height == 0) return
        val b = bounds(d) ?: return
        val pad = 50f
        val sx = (width - 2 * pad) / max(1f, b.width())
        val sy = (height - 2 * pad) / max(1f, b.height())
        scale = min(sx, sy)
        offsetX = width / 2f - b.centerX() * scale
        offsetY = height / 2f + b.centerY() * scale
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = drawing ?: return

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, -scale)
        entityPaint.strokeWidth = 2f / scale
        measurePaint.strokeWidth = 3f / scale

        d.entities.asSequence()
            .filter { it.layer in visibleLayers }
            .forEach { e -> drawEntity(canvas, e) }

        val a = measureA
        val b = measureB
        if (a != null) {
            measurePointPaint.color = 0xFFFFC107.toInt()
            canvas.drawCircle(a.x, a.y, 7f / scale, measurePointPaint)
        }
        if (a != null && b != null) {
            canvas.drawLine(a.x, a.y, b.x, b.y, measurePaint)
            canvas.drawCircle(b.x, b.y, 7f / scale, measurePointPaint)
        }
        canvas.restore()
    }

    private fun drawEntity(canvas: Canvas, e: Entity) {
        when (e) {
            is Entity.Line -> canvas.drawLine(e.a.x, e.a.y, e.b.x, e.b.y, entityPaint)
            is Entity.Circle -> canvas.drawCircle(e.c.x, e.c.y, e.r, entityPaint)
            is Entity.Arc -> {
                val r = RectF(e.c.x - e.r, e.c.y - e.r, e.c.x + e.r, e.c.y + e.r)
                var sweep = e.endDeg - e.startDeg
                if (sweep < 0f) sweep += 360f
                canvas.drawArc(r, e.startDeg, sweep, false, entityPaint)
            }
            is Entity.Polyline -> {
                val p = Path()
                p.moveTo(e.pts[0].x, e.pts[0].y)
                for (i in 1 until e.pts.size) p.lineTo(e.pts[i].x, e.pts[i].y)
                if (e.closed) p.close()
                canvas.drawPath(p, entityPaint)
            }
        }
    }

    private fun bounds(d: Drawing): RectF? {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        fun add(x: Float, y: Float) {
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }
        d.entities.forEach { e ->
            when (e) {
                is Entity.Line -> { add(e.a.x, e.a.y); add(e.b.x, e.b.y) }
                is Entity.Circle -> { add(e.c.x - e.r, e.c.y - e.r); add(e.c.x + e.r, e.c.y + e.r) }
                is Entity.Arc -> { add(e.c.x - e.r, e.c.y - e.r); add(e.c.x + e.r, e.c.y + e.r) }
                is Entity.Polyline -> e.pts.forEach { add(it.x, it.y) }
            }
        }
        return if (minX.isFinite()) RectF(minX, minY, maxX, maxY) else null
    }
}
