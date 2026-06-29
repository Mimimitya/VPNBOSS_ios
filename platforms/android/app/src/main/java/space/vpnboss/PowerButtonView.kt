package space.vpnboss

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

class PowerButtonView(context: Context) : View(context) {
    enum class State { OFF, CONNECTING, ON }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var state = State.OFF
    private var phase = 0f
    private var reveal = 1f
    private var animator: ValueAnimator? = null

    fun setState(next: State, animateSuccess: Boolean = false) {
        state = next
        animator?.cancel()
        when (next) {
            State.OFF -> { phase = 0f; reveal = 1f }
            State.CONNECTING -> animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1_050
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
            State.ON -> {
                reveal = if (animateSuccess) 0f else 1f
                animator = ValueAnimator.ofFloat(reveal, 1f).apply {
                    duration = if (animateSuccess) 620 else 1_800
                    interpolator = DecelerateInterpolator()
                    if (!animateSuccess) {
                        repeatMode = ValueAnimator.REVERSE
                        repeatCount = ValueAnimator.INFINITE
                    }
                    addUpdateListener {
                        val value = it.animatedValue as Float
                        reveal = if (animateSuccess) value else 1f
                        phase = if (animateSuccess) 0f else value
                        invalidate()
                    }
                    start()
                }
            }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = size * .48f
        fill.color = if (state == State.OFF) 0xFF2B0710.toInt() else 0xFF050505.toInt()
        canvas.drawCircle(cx, cy, radius, fill)

        stroke.strokeWidth = size * .061f
        if (state == State.CONNECTING) {
            val inset = size * .115f
            val arc = RectF(cx - radius + inset, cy - radius + inset, cx + radius - inset, cy + radius - inset)
            canvas.drawArc(arc, -90f + phase * 360f, 248f, false, stroke)
            return
        }

        val alpha = if (state == State.ON) (220 + 35 * phase).toInt() else 255
        stroke.alpha = alpha
        val top = cy - size * .27f
        val stemBottom = top + size * .25f * reveal
        canvas.drawLine(cx, top, cx, stemBottom, stroke)
        val inset = size * .19f
        val powerArc = RectF(cx - radius + inset, cy - radius + inset, cx + radius - inset, cy + radius - inset)
        canvas.drawArc(powerArc, -43f, 266f * reveal, false, stroke)
        stroke.alpha = 255
    }
}
