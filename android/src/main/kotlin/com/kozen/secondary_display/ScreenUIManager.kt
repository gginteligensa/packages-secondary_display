package com.kozen.secondary_display

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.kozen.component.secondaryScreen.IResultCallback
import com.kozen.component.secondaryScreen.ISecondaryScreen
import com.kozen.component_client.ComponentEngine

/**
 * ScreenUIManager — Single Responsibility: inflates XML layouts and sends
 * them to the SecondaryScreenManager. It does NOT handle animations —
 * that is delegated to [AnimationHelper].
 *
 * Software brightness is simulated by wrapping every view with a semi-transparent
 * black overlay before sending it to the SDK, so the effect works regardless of
 * whether the hardware supports backlight control.
 */
class ScreenUIManager(private val context: Context) {

    private val tag = "SD_ScreenUIManager"
    private val animationHelper = AnimationHelper(context)
    var presentation: KozenPresentation? = null

    /** Current software brightness level 0–100. 100 = fully bright, 0 = fully dark. */
    private var brightnessLevel: Int = 60

    // ─── Software brightness ──────────────────────────────────────────────────

    /**
     * Updates the software brightness level. The next view shown will use this value.
     * Also applies immediately to the Presentation overlay if one is active.
     */
    fun setBrightness(level: Int) {
        brightnessLevel = level.coerceIn(0, 100)
        presentation?.setBrightness(brightnessLevel)
        Log.d(tag, "Software brightness updated to $brightnessLevel%")
    }

    /**
     * Wraps [view] inside a FrameLayout with a black dimming overlay whose alpha
     * reflects the current [brightnessLevel]. The outer FrameLayout is sized to
     * exactly [w]×[h] so the SDK receives a correctly-sized bitmap.
     *
     * brightness=100 → overlay alpha 0.0 (transparent, full brightness)
     * brightness=30  → overlay alpha 0.59 (visible dimming)
     * brightness=0   → overlay alpha 0.85 (very dark, but not pitch-black)
     */
    private fun wrapWithBrightnessOverlay(view: View, w: Int, h: Int): View {
        val overlayAlpha = ((100 - brightnessLevel) / 100f * 0.85f).coerceIn(0f, 0.85f)
        if (overlayAlpha == 0f) return view  // full brightness — no wrapping needed

        val wrapper = FrameLayout(context)
        wrapper.layoutParams = FrameLayout.LayoutParams(w, h)

        // Content
        (view.parent as? FrameLayout)?.removeView(view)
        view.layoutParams = FrameLayout.LayoutParams(w, h)
        wrapper.addView(view)

        // Dimming overlay on top
        val overlay = View(context)
        overlay.setBackgroundColor(Color.BLACK)
        overlay.alpha = overlayAlpha
        overlay.isClickable = false
        overlay.isFocusable = false
        overlay.layoutParams = FrameLayout.LayoutParams(w, h)
        wrapper.addView(overlay)

        Log.d(tag, "Brightness overlay applied: level=$brightnessLevel%, alpha=$overlayAlpha")
        return wrapper
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun manager(): ISecondaryScreen? = ComponentEngine.secondaryScreenManager

    private fun resolution(): Pair<Int, Int> {
        val pres = presentation
        if (pres != null) {
            val res = pres.getResolution()
            return Pair(res[0], res[1])
        }
        val res = manager()?.screenResolution
        return Pair(
            if (res != null && res.size >= 1) res[0] else 378,
            if (res != null && res.size >= 2) res[1] else 172
        )
    }

    private fun measureAndLayout(view: View, w: Int, h: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)
    }

    private fun showView(view: View, onSuccess: () -> Unit, onFailure: (Int, String?) -> Unit) {
        val pres = presentation
        if (pres != null) {
            Handler(Looper.getMainLooper()).post {
                try {
                    pres.showView(view)  // KozenPresentation applies its own overlay
                    onSuccess()
                } catch (e: Exception) {
                    Log.e(tag, "Native presentation showView failed: ${e.message}")
                    onFailure(-2, e.message)
                }
            }
            return
        }

        val mgr = manager() ?: run {
            onFailure(-1, "Manager not initialized")
            return
        }
        val (w, h) = resolution()
        measureAndLayout(view, w, h)
        // Wrap with software brightness overlay before handing to the SDK
        val displayView = wrapWithBrightnessOverlay(view, w, h)
        measureAndLayout(displayView, w, h)
        mgr.show(displayView, object : IResultCallback {
            override fun onSuccess() { onSuccess() }
            override fun onFailure(code: Int, msg: String?) { onFailure(code, msg) }
        })
    }

    fun stopAnimations() = animationHelper.stopAnimation()

    // ─── Static Screens ───────────────────────────────────────────────────────

    /** Shows the custom branded wallpaper. On failure, falls back to the SDK default. */
    fun showWallpaper(logoPath: String? = null, onDone: (Boolean) -> Unit) {
        stopAnimations()
        if (presentation == null && manager() == null) {
            onDone(false)
            return
        }
        try {
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.secondary_wallpaper, null)

            // Custom logo override
            if (logoPath != null) {
                try {
                    val imgView = view.findViewById<ImageView>(R.id.wallpaper_logo)
                    val bitmap = android.graphics.BitmapFactory.decodeFile(logoPath)
                    if (bitmap != null) imgView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.w(tag, "Could not load custom logo from path: $logoPath")
                }
            }

            showView(view,
                onSuccess = { Log.d(tag, "Wallpaper shown"); onDone(true) },
                onFailure = { code, msg ->
                    Log.w(tag, "Wallpaper failed ($code)")
                    onDone(false)
                }
            )
        } catch (e: Exception) {
            Log.e(tag, "Error inflating wallpaper: ${e.message}")
            onDone(false)
        }
    }

    /** Shows the welcome screen. */
    fun showWelcome(onDone: (Boolean) -> Unit) {
        stopAnimations()
        if (presentation == null && manager() == null) {
            onDone(false)
            return
        }
        try {
            val view = LayoutInflater.from(context).inflate(R.layout.secondary_welcome, null)
            showView(view,
                onSuccess = { onDone(true) },
                onFailure = { _, _ -> onDone(false) }
            )
        } catch (e: Exception) {
            Log.e(tag, "Error showing welcome: ${e.message}")
            onDone(false)
        }
    }

    /** Shows an amount screen. */
    fun showAmount(amount: String, title: String, currency: String, onDone: (Boolean) -> Unit) {
        stopAnimations()
        if (presentation == null && manager() == null) {
            onDone(false)
            return
        }
        try {
            val view = LayoutInflater.from(context).inflate(R.layout.secondary_amount, null)
            view.findViewById<TextView>(R.id.amount_text).text = amount
            if (title.isNotEmpty()) {
                view.findViewById<TextView>(R.id.amount_title).text = title.uppercase()
            }
            val currencyTv = view.findViewById<TextView>(R.id.currency_text)
            if (currency.isNotEmpty()) {
                currencyTv.text = currency
                currencyTv.visibility = View.VISIBLE
            } else {
                currencyTv.visibility = View.GONE
            }
            showView(view,
                onSuccess = { onDone(true) },
                onFailure = { _, _ -> onDone(false) }
            )
        } catch (e: Exception) {
            Log.e(tag, "Error showing amount: ${e.message}")
            onDone(false)
        }
    }

    /** Shows a status / instruction message screen. */
    fun showStatus(title: String, subtitle: String, onDone: (Boolean) -> Unit) {
        stopAnimations()
        if (presentation == null && manager() == null) {
            onDone(false)
            return
        }
        try {
            val view = LayoutInflater.from(context).inflate(R.layout.secondary_status, null)
            view.findViewById<TextView>(R.id.status_title).text = title
            view.findViewById<TextView>(R.id.status_subtitle).text = subtitle
            showView(view,
                onSuccess = { onDone(true) },
                onFailure = { _, _ -> onDone(false) }
            )
        } catch (e: Exception) {
            Log.e(tag, "Error showing status: ${e.message}")
            onDone(false)
        }
    }

    // ─── Animated Screens ─────────────────────────────────────────────────────

    /** Plays the read-card GIF animation. */
    fun showReadCard(customGifPath: String? = null, onDone: (Boolean) -> Unit) {
        val pres = presentation
        val mgr = if (pres == null) (manager() ?: run { onDone(false); return }) else null
        val (w, h) = resolution()

        Handler(Looper.getMainLooper()).post {
            try {
                stopAnimations()
                val view = LayoutInflater.from(context).inflate(R.layout.secondary_read_card, null)
                val imageView = view.findViewById<ImageView>(R.id.read_card_gif)

                animationHelper.playGif(
                    gifResId = R.raw.read_card_gif,
                    widthPX = w,
                    heightPX = h,
                    imageView = imageView,
                    view = view,
                    manager = mgr,
                    presentation = pres,
                    onFirstFrameShown = { onDone(true) },
                    onFirstFrameError = { _, _ -> onDone(false) }
                )
            } catch (e: Exception) {
                Log.e(tag, "Error starting read card animation: ${e.message}")
                onDone(false)
            }
        }
    }

    /** Plays the approved animated result screen. */
    fun showApproved(
        bgColorTop: Int = 0xFF00C853.toInt(),
        bgColorBottom: Int = 0xFF1B5E20.toInt(),
        label: String = "APROBADO",
        onDone: (Boolean) -> Unit
    ) {
        val pres = presentation
        val mgr = if (pres == null) (manager() ?: run { onDone(false); return }) else null
        val (w, h) = resolution()

        Handler(Looper.getMainLooper()).post {
            stopAnimations()
            val view = LayoutInflater.from(context).inflate(R.layout.secondary_read_card, null)
            val imageView = view.findViewById<ImageView>(R.id.read_card_gif)
            animationHelper.playResultAnimation(
                isApproved = true,
                bgColorTop = bgColorTop,
                bgColorBottom = bgColorBottom,
                iconColor = android.graphics.Color.WHITE,
                labelText = label,
                subText = "",
                approvedLabel = label,
                rejectedLabel = "",
                widthPX = w,
                heightPX = h,
                imageView = imageView,
                view = view,
                manager = mgr,
                presentation = pres,
                onSuccess = { onDone(true) },
                onFailure = { _, _ -> onDone(false) }
            )
        }
    }

    /** Plays the rejected animated result screen. */
    fun showRejected(
        message: String = "",
        bgColorTop: Int = 0xFFD32F2F.toInt(),
        bgColorBottom: Int = 0xFF7F0000.toInt(),
        label: String = "DENEGADO",
        onDone: (Boolean) -> Unit
    ) {
        val pres = presentation
        val mgr = if (pres == null) (manager() ?: run { onDone(false); return }) else null
        val (w, h) = resolution()

        Handler(Looper.getMainLooper()).post {
            stopAnimations()
            val view = LayoutInflater.from(context).inflate(R.layout.secondary_read_card, null)
            val imageView = view.findViewById<ImageView>(R.id.read_card_gif)
            animationHelper.playResultAnimation(
                isApproved = false,
                bgColorTop = bgColorTop,
                bgColorBottom = bgColorBottom,
                iconColor = android.graphics.Color.WHITE,
                labelText = label,
                subText = message,
                approvedLabel = "",
                rejectedLabel = label,
                widthPX = w,
                heightPX = h,
                imageView = imageView,
                view = view,
                manager = mgr,
                presentation = pres,
                onSuccess = { onDone(true) },
                onFailure = { _, _ -> onDone(false) }
            )
        }
    }
}
