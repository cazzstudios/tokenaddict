package com.tokenaddict.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.ProgressBar

/**
 * A horizontal ProgressBar that draws a thin vertical "pace marker" line
 * indicating where usage *should* be given the elapsed time in the window.
 *
 * Set [markerPosition] to a value 0–100 (percentage of the bar) to show
 * the marker, or leave it at the default (-1) to hide it.
 */
class ProgressWithMarkerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.progressBarStyleHorizontal
) : ProgressBar(context, attrs, defStyleAttr) {

    /** Marker position as a percentage (0–100), or -1 to hide. */
    var markerPosition: Float = -1f
        set(value) {
            field = value.coerceIn(-1f, 100f)
            invalidate()
        }

    private val markerOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MARKER_OUTLINE_COLOR
        strokeWidth = MARKER_WIDTH_PX + OUTLINE_EXTRA_PX
        style = Paint.Style.STROKE
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MARKER_COLOR
        strokeWidth = MARKER_WIDTH_PX
        style = Paint.Style.STROKE
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (markerPosition < 0f) return

        val pl = paddingLeft.toFloat()
        val pr = paddingRight.toFloat()
        val drawableWidth = width - pl - pr
        if (drawableWidth <= 0f) return

        val x = pl + drawableWidth * markerPosition / 100f
        val top = paddingTop.toFloat()
        val bottom = (height - paddingBottom).toFloat()

        canvas.drawLine(x, top, x, bottom, markerOutlinePaint)
        canvas.drawLine(x, top, x, bottom, markerPaint)
    }

    companion object {
        private const val MARKER_WIDTH_PX = 6f
        private const val OUTLINE_EXTRA_PX = 4f
        private const val MARKER_COLOR = 0xFFFFFFFF.toInt()
        private const val MARKER_OUTLINE_COLOR = 0xAA000000.toInt()
    }
}
