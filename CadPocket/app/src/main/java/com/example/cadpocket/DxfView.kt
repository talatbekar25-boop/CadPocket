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
import kotlin.math.max
import kotlin.math.min

class DxfView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private var drawing: Drawing? = null
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

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
                invalidate(); return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                offsetX -= dx; offsetY -= dy; invalidate(); return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                fitToScreen(); return true
            }
        })

    fun setDrawing(d: Drawing) {
        drawing = d
        post { fitToScreen() }
    }

    fun fitToScreen() {
        val d = drawing ?: return
        if (width == 0 || height == 0) return
        val b = bounds(d) ?: return
        val pad = 40f
        val sx = (width - 2 * pad) / max(1f, b.width())
        val sy = (height - 2 * pad) / max(1f, b.height())
        scale = min(sx, sy)
        offsetX = width / 2f - b.centerX() * scale
        offsetY = height / 2f + b.centerY() * scale
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = drawing ?: return
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, -scale)
        paint.strokeWidth = 2f / scale
        d.entities.forEach { e ->
            when (e) {
                is Entity.Line -> canvas.drawLine(e.a.x, e.a.y, e.b.x, e.b.y, paint)
                is Entity.Circle -> canvas.drawCircle(e.c.x, e.c.y, e.r, paint)
                is Entity.Arc -> {
                    val r = RectF(e.c.x - e.r, e.c.y - e.r, e.c.x + e.r, e.c.y + e.r)
                    var sweep = e.endDeg - e.startDeg
                    if (sweep < 0f) sweep += 360f
                    canvas.drawArc(r, e.startDeg, sweep, false, paint)
                }
                is Entity.Polyline -> {
                    val p = Path(); p.moveTo(e.pts[0].x, e.pts[0].y)
                    for (i in 1 until e.pts.size) p.lineTo(e.pts[i].x, e.pts[i].y)
                    if (e.closed) p.close()
                    canvas.drawPath(p, paint)
                }
            }
        }
        canvas.restore()
    }

    private fun bounds(d: Drawing): RectF? {
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        fun add(x: Float, y: Float) { minX = min(minX,x); minY = min(minY,y); maxX = max(maxX,x); maxY = max(maxY,y) }
        d.entities.forEach { e -> when(e) {
            is Entity.Line -> { add(e.a.x,e.a.y); add(e.b.x,e.b.y) }
            is Entity.Circle -> { add(e.c.x-e.r,e.c.y-e.r); add(e.c.x+e.r,e.c.y+e.r) }
            is Entity.Arc -> { add(e.c.x-e.r,e.c.y-e.r); add(e.c.x+e.r,e.c.y+e.r) }
            is Entity.Polyline -> e.pts.forEach { add(it.x,it.y) }
        }}
        return if (minX.isFinite()) RectF(minX,minY,maxX,maxY) else null
    }
}
