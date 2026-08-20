package com.partybuilding.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.Log

/**
 * Animated drawable that cycles through GIF (or animated WebP) frames.
 * Uses ImageDecoder (API 28+) where available; falls back to first frame
 * for older devices or static images.
 */
class AnimatedGifDrawable(
    private val frames: List<Bitmap>,
    private val frameDelaysMs: IntArray,
) : Drawable(), Animatable {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val srcRect = Rect()
    private var startTime = -1L
    private var currentFrame = 0
    private var running = false

    constructor(frames: List<Bitmap>, frameDelayMs: Int) : this(frames, IntArray(frames.size) { frameDelayMs })

    override fun draw(canvas: Canvas) {
        if (frames.isEmpty()) return
        val bitmap = frames[currentFrame.coerceIn(0, frames.lastIndex)]
        srcRect.set(0, 0, bitmap.width, bitmap.height)
        val dstRect = bounds
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        if (running) {
            val now = SystemClock.uptimeMillis()
            val elapsed = (now - startTime).toInt()
            val totalDelay = frameDelaysMs.sum()
            if (totalDelay > 0) {
                val pos = elapsed % totalDelay
                var acc = 0
                for (i in frames.indices) {
                    acc += frameDelaysMs[i]
                    if (pos < acc) {
                        if (i != currentFrame) {
                            currentFrame = i
                            invalidateSelf()
                        }
                        break
                    }
                }
            }
        }
    }

    override fun start() {
        if (frames.size <= 1) return
        running = true
        startTime = SystemClock.uptimeMillis()
        invalidateSelf()
    }

    override fun stop() {
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = frames.firstOrNull()?.width ?: 0
    override fun getIntrinsicHeight(): Int = frames.firstOrNull()?.height ?: 0

    companion object {
        fun fromStatic(bitmap: Bitmap): AnimatedGifDrawable =
            AnimatedGifDrawable(listOf(bitmap), IntArray(1) { 1000 })

        fun fromAnimatedFrames(frames: List<Bitmap>, delaysMs: IntArray): AnimatedGifDrawable =
            AnimatedGifDrawable(frames, delaysMs).also { it.start() }
    }
}
