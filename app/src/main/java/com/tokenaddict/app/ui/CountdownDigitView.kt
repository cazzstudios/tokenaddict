package com.tokenaddict.app.ui

import android.content.Context
import android.graphics.Typeface
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewPropertyAnimator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.color.MaterialColors
import java.util.Locale

/**
 * A two-digit countdown segment that displays an integer 0–99 formatted as "%02d"
 * with a smooth animated digit-change effect.
 *
 * When [setDigitValue] is called with a new value and [animate] is true, the view
 * performs a 250ms scale/crossfade animation: old text scales down + fades out,
 * then new text scales up + fades in.
 *
 * Respects [Settings.Global.ANIMATOR_DURATION_SCALE]: if scale is 0, animation is skipped.
 */
class CountdownDigitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var currentValue: Int = -1
    private var currentAnimator: ViewPropertyAnimator? = null

    init {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        gravity = Gravity.CENTER
        val colorOnSurface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurface,
            0xFF000000.toInt()
        )
        setTextColor(colorOnSurface)
    }

    /**
     * Set the digit value to display.
     *
     * @param value Integer 0–99 (values outside are clamped).
     * @param animate Whether to animate the transition. Default true.
     */
    fun setDigitValue(value: Int, animate: Boolean = true) {
        val clamped = value.coerceIn(0, 99)
        if (clamped == currentValue) return
        currentValue = clamped

        if (!animate || !isAnimationEnabled()) {
            cancelCurrentAnimation()
            text = String.format(Locale.ROOT, "%02d", clamped)
            return
        }

        cancelCurrentAnimation()

        val fadeOut = animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(125L)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                text = String.format(Locale.ROOT, "%02d", clamped)
                scaleX = 1.2f
                scaleY = 1.2f
                alpha = 0f
                val fadeIn = animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(125L)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .withEndAction {
                        currentAnimator = null
                    }
                currentAnimator = fadeIn
                fadeIn.start()
            }
        currentAnimator = fadeOut
        fadeOut.start()
    }

    /** Returns the current digit value (0–99), or 0 if not yet set. */
    fun getCurrentDigitValue(): Int = if (currentValue < 0) 0 else currentValue

    private fun isAnimationEnabled(): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale != 0f
        } catch (_: Exception) {
            true
        }
    }

    private fun cancelCurrentAnimation() {
        currentAnimator?.cancel()
        currentAnimator = null
    }

    override fun onDetachedFromWindow() {
        cancelCurrentAnimation()
        super.onDetachedFromWindow()
    }
}
