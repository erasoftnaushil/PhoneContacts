package com.contactsapp2025.universal.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import org.fossify.commons.R

class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.md_green_200_dark)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var rotationAngle = 0f
    private val matrix = Matrix()
    private var sweepGradient: SweepGradient? = null

    private var animator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        val radius = Math.min(cx, cy)
        
        sweepGradient = SweepGradient(
            cx, cy,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#00000000"), Color.parseColor("#444CAC51"), Color.parseColor("#AA4CAC51")),
            floatArrayOf(0f, 0.5f, 0.95f, 1f)
        )
        sweepPaint.shader = sweepGradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = Math.min(cx, cy) - 4f

        // Draw circles
        val circleCount = 3
        val radiusStep = maxRadius / circleCount
        for (i in 1..circleCount) {
            canvas.drawCircle(cx, cy, radiusStep * i, gridPaint)
        }

        // Draw crosshairs
        canvas.drawLine(cx, 0f, cx, height.toFloat(), gridPaint)
        canvas.drawLine(0f, cy, width.toFloat(), cy, gridPaint)

        // Draw center dot
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4CAC51")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 6f, dotPaint)

        // Draw sweep
        sweepGradient?.let {
            matrix.setRotate(rotationAngle, cx, cy)
            it.setLocalMatrix(matrix)
            canvas.drawCircle(cx, cy, maxRadius, sweepPaint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
