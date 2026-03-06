package com.translator.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    private var amplitudeLevel = 0f   // 0~1
    private var animPhase = 0f
    private var isAnimating = false

    private val bars = 9  // 파형 바 개수
    private val barWidth = 6f

    // 색상 배열 (그라데이션 효과)
    private val colors = intArrayOf(
        Color.parseColor("#FF6B9D"),
        Color.parseColor("#C44DFF"),
        Color.parseColor("#4FACFE"),
        Color.parseColor("#00F2FE"),
        Color.parseColor("#4FACFE"),
        Color.parseColor("#C44DFF"),
        Color.parseColor("#FF6B9D"),
        Color.parseColor("#C44DFF"),
        Color.parseColor("#4FACFE")
    )

    private val runnable = object : Runnable {
        override fun run() {
            if (isAnimating) {
                animPhase += 0.15f
                invalidate()
                postDelayed(this, 30)
            }
        }
    }

    fun startAnimation() {
        isAnimating = true
        post(runnable)
    }

    fun stopAnimation() {
        isAnimating = false
        amplitudeLevel = 0f
        invalidate()
    }

    fun setAmplitude(amplitude: Int) {
        // 0~32767 → 0~1
        amplitudeLevel = (amplitude / 32767f).coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isAnimating) {
            drawIdleState(canvas)
            return
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val maxBarHeight = height * 0.8f
        val minBarHeight = height * 0.15f

        val totalWidth = bars * barWidth * 3f
        val startX = centerX - totalWidth / 2f

        for (i in 0 until bars) {
            val x = startX + i * barWidth * 3f + barWidth / 2f

            // 각 바의 높이 = 사인파 + 진폭
            val phase = animPhase + i * 0.7f
            val sineValue = (sin(phase.toDouble()) + 1f) / 2f
            val extra = amplitudeLevel * 0.6f
            val barHeight = (minBarHeight + (maxBarHeight - minBarHeight) * (sineValue.toFloat() * (0.4f + extra)))
                .coerceIn(minBarHeight, maxBarHeight)

            paint.color = colors[i % colors.size]
            paint.strokeWidth = barWidth
            paint.alpha = 220

            canvas.drawLine(
                x, centerY - barHeight / 2f,
                x, centerY + barHeight / 2f,
                paint
            )
        }
    }

    private fun drawIdleState(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val idleHeight = height * 0.15f

        val totalWidth = bars * barWidth * 3f
        val startX = centerX - totalWidth / 2f

        for (i in 0 until bars) {
            val x = startX + i * barWidth * 3f + barWidth / 2f
            paint.color = colors[i % colors.size]
            paint.strokeWidth = barWidth
            paint.alpha = 80

            canvas.drawLine(
                x, centerY - idleHeight / 2f,
                x, centerY + idleHeight / 2f,
                paint
            )
        }
    }
}
