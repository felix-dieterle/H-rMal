package com.felix.hormal.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.felix.hormal.model.FREQUENCIES

/**
 * A lightweight audiogram view that is updated in real time during the hearing test.
 *
 * As each frequency threshold is confirmed, [setThreshold] is called to draw the new
 * data point and connect it to the previous one.  Left-ear results are drawn in blue
 * (◯), right-ear results in red (×), matching the style used in ResultActivity.
 */
class LiveAudiogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Threshold arrays; -1 = not yet measured, 99 = no response
    private val leftThresholds  = IntArray(FREQUENCIES.size) { -1 }
    private val rightThresholds = IntArray(FREQUENCIES.size) { -1 }

    // Frequency labels shown on the X axis
    private val freqLabels = arrayOf("250", "500", "1k", "2k", "4k", "8k")

    // dB range displayed on the Y axis (standard audiogram: 0 at top, 90 at bottom)
    private val DB_MIN = 0
    private val DB_MAX = 90

    private val paintLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val paintRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C62828")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val paintAxisLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 20f
        textAlign = Paint.Align.RIGHT
    }

    // Padding inside the view (leave room for axis labels)
    private val PAD_LEFT   = 80f
    private val PAD_TOP    = 20f
    private val PAD_RIGHT  = 20f
    private val PAD_BOTTOM = 40f

    /** Plot area bounds – recalculated in onDraw. */
    private var plotLeft   = 0f
    private var plotTop    = 0f
    private var plotRight  = 0f
    private var plotBottom = 0f

    /** Update the threshold for one frequency/ear combination and redraw. */
    fun setThreshold(freqIndex: Int, db: Int, leftEar: Boolean) {
        if (freqIndex < 0 || freqIndex >= FREQUENCIES.size) return
        if (leftEar) leftThresholds[freqIndex]  = db
        else         rightThresholds[freqIndex] = db
        invalidate()
    }

    /** Reset all thresholds (called at the start of a new test). */
    fun reset() {
        leftThresholds.fill(-1)
        rightThresholds.fill(-1)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        plotLeft   = PAD_LEFT
        plotTop    = PAD_TOP
        plotRight  = width  - PAD_RIGHT
        plotBottom = height - PAD_BOTTOM

        drawGrid(canvas)
        drawAxesLabels(canvas)
        drawThresholdLine(canvas, leftThresholds,  paintLeft,  leftSymbol = true)
        drawThresholdLine(canvas, rightThresholds, paintRight, leftSymbol = false)
    }

    private fun drawGrid(canvas: Canvas) {
        val dbSteps = intArrayOf(0, 20, 40, 60, 80)
        for (db in dbSteps) {
            val y = dbToY(db)
            canvas.drawLine(plotLeft, y, plotRight, y, paintGrid)
        }
        for (i in FREQUENCIES.indices) {
            val x = freqToX(i)
            canvas.drawLine(x, plotTop, x, plotBottom, paintGrid)
        }
        canvas.drawRect(plotLeft, plotTop, plotRight, plotBottom, paintGrid)
    }

    private fun drawAxesLabels(canvas: Canvas) {
        for (i in FREQUENCIES.indices) {
            canvas.drawText(freqLabels[i], freqToX(i), plotBottom + PAD_BOTTOM * 0.8f, paintLabel)
        }
        val dbSteps = intArrayOf(0, 20, 40, 60, 80)
        for (db in dbSteps) {
            canvas.drawText("$db", plotLeft - 8f, dbToY(db) + paintAxisLabel.textSize / 3f, paintAxisLabel)
        }
    }

    private fun drawThresholdLine(
        canvas: Canvas,
        thresholds: IntArray,
        paint: Paint,
        leftSymbol: Boolean
    ) {
        val symbolRadius = 10f
        var prevX = 0f
        var prevY = 0f
        var prevValid = false

        for (i in thresholds.indices) {
            val db = thresholds[i]
            if (db < 0) { prevValid = false; continue }
            val displayDb = if (db == 99) DB_MAX else db
            val x = freqToX(i)
            val y = dbToY(displayDb)

            if (prevValid) {
                canvas.drawLine(prevX, prevY, x, y, paint)
            }

            if (leftSymbol) {
                canvas.drawCircle(x, y, symbolRadius, paint)
            } else {
                val d = symbolRadius * 0.75f
                canvas.drawLine(x - d, y - d, x + d, y + d, paint)
                canvas.drawLine(x + d, y - d, x - d, y + d, paint)
            }

            prevX = x; prevY = y; prevValid = true
        }
    }

    private fun freqToX(index: Int): Float {
        val span = plotRight - plotLeft
        return plotLeft + index * span / (FREQUENCIES.size - 1).toFloat()
    }

    private fun dbToY(db: Int): Float {
        val span = plotBottom - plotTop
        return plotTop + (db - DB_MIN).toFloat() / (DB_MAX - DB_MIN) * span
    }
}
