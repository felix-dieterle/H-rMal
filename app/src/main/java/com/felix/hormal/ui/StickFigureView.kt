package com.felix.hormal.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * A simple stick-figure view that is progressively drawn as the child answers correctly
 * during the hearing test.
 *
 * Parts are revealed one by one as [correctCount] increases:
 *   1 → head
 *   2 → body
 *   3 → left arm
 *   4 → right arm
 *   5 → left leg
 *   6 → right leg
 *   7 → smile and eyes (the figure is now complete and happy)
 *
 * Call [setCorrectCount] after each correct answer to update the drawing.
 */
class StickFigureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var correctCount = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B5E20")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val smilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F9A825")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun setCorrectCount(count: Int) {
        correctCount = count
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f

        // Scale so the figure fills most of the view
        val scale = minOf(width, height) / 200f

        val headR    = 28f * scale   // head circle radius
        val headCy   = cy - 60f * scale  // head centre Y
        val neckY    = headCy + headR
        val bodyEndY = neckY + 60f * scale
        val armY     = neckY + 18f * scale
        val armLen   = 40f * scale
        val legLen   = 50f * scale

        // 1 – head
        if (correctCount >= 1) {
            canvas.drawCircle(cx, headCy, headR, paint)
        }
        // 2 – body
        if (correctCount >= 2) {
            canvas.drawLine(cx, neckY, cx, bodyEndY, paint)
        }
        // 3 – left arm
        if (correctCount >= 3) {
            canvas.drawLine(cx, armY, cx - armLen, armY + 20f * scale, paint)
        }
        // 4 – right arm
        if (correctCount >= 4) {
            canvas.drawLine(cx, armY, cx + armLen, armY + 20f * scale, paint)
        }
        // 5 – left leg
        if (correctCount >= 5) {
            canvas.drawLine(cx, bodyEndY, cx - armLen * 0.8f, bodyEndY + legLen, paint)
        }
        // 6 – right leg
        if (correctCount >= 6) {
            canvas.drawLine(cx, bodyEndY, cx + armLen * 0.8f, bodyEndY + legLen, paint)
        }
        // 7 – smile
        if (correctCount >= 7) {
            val smileRect = RectF(
                cx - headR * 0.55f,
                headCy - headR * 0.1f,
                cx + headR * 0.55f,
                headCy + headR * 0.65f
            )
            canvas.drawArc(smileRect, 10f, 160f, false, smilePaint)
            // Eyes – filled circles
            val eyeFillPaint = Paint(paint).apply { style = Paint.Style.FILL }
            val eyeOffsetX = headR * 0.35f
            val eyeY = headCy - headR * 0.2f
            canvas.drawCircle(cx - eyeOffsetX, eyeY, headR * 0.1f, eyeFillPaint)
            canvas.drawCircle(cx + eyeOffsetX, eyeY, headR * 0.1f, eyeFillPaint)
        }
    }
}
