package com.kozen.secondary_display

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import com.kozen.component.secondaryScreen.IResultCallback
import com.kozen.component_client.ComponentEngine

/**
 * AnimationHelper — Single Responsibility: manages all animated content
 * for the secondary display (GIF frame playback, and Canvas-drawn result screens).
 */
internal class AnimationHelper(private val context: Context) {

    private val tag = "SD_AnimationHelper"
    private var gifAnimHandler: Handler? = null
    private var gifAnimRunnable: Runnable? = null

    // ─── Public control ──────────────────────────────────────────────────────

    fun stopAnimation() {
        gifAnimRunnable?.let { gifAnimHandler?.removeCallbacks(it) }
        gifAnimHandler = null
        gifAnimRunnable = null
    }

    // ─── GIF Animation ───────────────────────────────────────────────────────

    fun playGif(
        gifResId: Int,
        widthPX: Int,
        heightPX: Int,
        imageView: ImageView,
        view: android.view.View,
        manager: com.kozen.component.secondaryScreen.ISecondaryScreen,
        onFirstFrameShown: () -> Unit,
        onFirstFrameError: (Int, String?) -> Unit
    ) {
        stopAnimation()
        val inputStream = context.resources.openRawResource(gifResId)
        @Suppress("DEPRECATION")
        val movie = android.graphics.Movie.decodeStream(inputStream)
        inputStream.close()

        if (movie == null || movie.width() == 0) {
            Log.e(tag, "Failed to decode GIF resource id=$gifResId")
            onFirstFrameError(-1, "Could not decode GIF")
            return
        }

        val duration = movie.duration().coerceAtLeast(1)
        val frameIntervalMs = 50L
        val startTime = System.currentTimeMillis()
        val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(widthPX, android.view.View.MeasureSpec.EXACTLY)
        val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(heightPX, android.view.View.MeasureSpec.EXACTLY)

        var firstFrame = true
        val handler = Handler(Looper.getMainLooper())
        gifAnimHandler = handler

        val runnable = object : Runnable {
            override fun run() {
                val elapsed = ((System.currentTimeMillis() - startTime) % duration).toInt()
                movie.setTime(elapsed)

                val bitmap = Bitmap.createBitmap(widthPX, heightPX, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val scaleX = widthPX.toFloat() / movie.width()
                val scaleY = heightPX.toFloat() / movie.height()
                canvas.scale(scaleX, scaleY)
                movie.draw(canvas, 0f, 0f)

                imageView.setImageBitmap(bitmap)
                view.measure(widthSpec, heightSpec)
                view.layout(0, 0, widthPX, heightPX)

                val callback = if (firstFrame) {
                    firstFrame = false
                    object : IResultCallback {
                        override fun onSuccess() { onFirstFrameShown() }
                        override fun onFailure(code: Int, msg: String?) {
                            stopAnimation()
                            onFirstFrameError(code, msg)
                        }
                    }
                } else {
                    object : IResultCallback {
                        override fun onSuccess() {}
                        override fun onFailure(code: Int, msg: String?) {
                            Log.w(tag, "GIF frame failed: $code")
                        }
                    }
                }
                manager.show(view, callback)
                handler.postDelayed(this, frameIntervalMs)
            }
        }
        gifAnimRunnable = runnable
        handler.post(runnable)
    }

    // ─── Canvas Result Animation ─────────────────────────────────────────────

    fun playResultAnimation(
        isApproved: Boolean,
        bgColorTop: Int,
        bgColorBottom: Int,
        iconColor: Int,
        labelText: String,
        subText: String,
        approvedLabel: String,
        rejectedLabel: String,
        widthPX: Int,
        heightPX: Int,
        imageView: ImageView,
        view: android.view.View,
        manager: com.kozen.component.secondaryScreen.ISecondaryScreen,
        onSuccess: () -> Unit,
        onFailure: (Int, String?) -> Unit
    ) {
        stopAnimation()

        val totalFrames = 16
        val holdFrames = 40
        var frame = 0
        var resultSent = false

        val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(widthPX, android.view.View.MeasureSpec.EXACTLY)
        val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(heightPX, android.view.View.MeasureSpec.EXACTLY)

        val handler = Handler(Looper.getMainLooper())
        gifAnimHandler = handler

        val runnable = object : Runnable {
            override fun run() {
                val progress = if (frame < totalFrames) {
                    val t = frame.toFloat() / totalFrames
                    1f - (1f - t) * (1f - t)
                } else 1f

                val bmp = drawResultBitmap(
                    widthPX, heightPX,
                    bgColorTop, bgColorBottom,
                    progress, iconColor,
                    isApproved, labelText, subText
                )
                imageView.setImageBitmap(bmp)
                view.measure(widthSpec, heightSpec)
                view.layout(0, 0, widthPX, heightPX)

                val isLast = frame >= totalFrames + holdFrames
                manager.show(view, object : IResultCallback {
                    override fun onSuccess() {
                        if (!resultSent) { resultSent = true; onSuccess() }
                    }
                    override fun onFailure(code: Int, msg: String?) {
                        if (!resultSent) { resultSent = true; onFailure(code, msg) }
                    }
                })

                frame++
                if (!isLast) {
                    handler.postDelayed(this, 40L)
                } else {
                    gifAnimHandler = null
                    gifAnimRunnable = null
                }
            }
        }
        gifAnimRunnable = runnable
        handler.post(runnable)
    }

    // ─── Canvas drawing ──────────────────────────────────────────────────────

    private fun drawResultBitmap(
        w: Int, h: Int,
        bgColorTop: Int, bgColorBottom: Int,
        iconProgress: Float,
        iconColor: Int,
        isApproved: Boolean,
        labelText: String,
        subText: String
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Background gradient
        val gradient = LinearGradient(0f, 0f, 0f, h.toFloat(), bgColorTop, bgColorBottom, Shader.TileMode.CLAMP)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val cx = w * 0.25f
        val cy = h * 0.5f
        val circleR = h * 0.25f

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        canvas.drawCircle(cx, cy, circleR, circlePaint)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            style = Paint.Style.STROKE
            strokeWidth = h * 0.07f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        if (isApproved) {
            val p1x = cx - circleR * 0.45f; val p1y = cy
            val p2x = cx - circleR * 0.1f;  val p2y = cy + circleR * 0.4f
            val p3x = cx + circleR * 0.5f;  val p3y = cy - circleR * 0.4f
            val seg1Len = Math.hypot((p2x - p1x).toDouble(), (p2y - p1y).toDouble()).toFloat()
            val seg2Len = Math.hypot((p3x - p2x).toDouble(), (p3y - p2y).toDouble()).toFloat()
            val drawnLen = (seg1Len + seg2Len) * iconProgress
            if (drawnLen <= seg1Len) {
                val t = drawnLen / seg1Len
                canvas.drawLine(p1x, p1y, p1x + (p2x - p1x) * t, p1y + (p2y - p1y) * t, iconPaint)
            } else {
                canvas.drawLine(p1x, p1y, p2x, p2y, iconPaint)
                val t = (drawnLen - seg1Len) / seg2Len
                canvas.drawLine(p2x, p2y, p2x + (p3x - p2x) * t, p2y + (p3y - p2y) * t, iconPaint)
            }
        } else {
            val r = circleR * 0.5f
            if (iconProgress <= 0.5f) {
                val t = iconProgress / 0.5f
                canvas.drawLine(cx - r, cy - r, cx - r + (2 * r) * t, cy - r + (2 * r) * t, iconPaint)
            } else {
                canvas.drawLine(cx - r, cy - r, cx + r, cy + r, iconPaint)
                val t = (iconProgress - 0.5f) / 0.5f
                canvas.drawLine(cx + r, cy - r, cx + r - (2 * r) * t, cy - r + (2 * r) * t, iconPaint)
            }
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textSize = h * 0.18f
            letterSpacing = 0.05f
        }
        val textX = w * 0.42f
        val textY = if (subText.isEmpty()) h * 0.57f else h * 0.42f
        canvas.drawText(labelText, textX, textY, textPaint)

        if (subText.isNotEmpty()) {
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCFFFFFF.toInt()
                typeface = Typeface.DEFAULT
                textSize = h * 0.11f
            }
            val subTruncated = if (subText.length > 22) subText.substring(0, 22) else subText
            canvas.drawText(subTruncated.uppercase(), textX, h * 0.68f, subPaint)
        }

        return bmp
    }
}
