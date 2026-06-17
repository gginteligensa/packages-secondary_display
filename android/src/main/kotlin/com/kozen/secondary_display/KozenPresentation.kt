package com.kozen.secondary_display

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.util.Log

/**
 * KozenPresentation — Standard Android Presentation class for secondary display.
 * This avoids using the conflicting Kozen Component SDK.
 *
 * Software brightness is simulated with a semi-transparent black overlay on top
 * of all content, so the effect is visible regardless of hardware backlight support.
 */
class KozenPresentation(outerContext: Context, display: Display) : Presentation(outerContext, display) {

    private val tag = "KozenPresentation"

    private lateinit var rootContainer: FrameLayout

    /**
     * Overlay view that dims the screen. Alpha is 0 at full brightness and increases
     * as brightness decreases, giving a software dimming effect.
     */
    private lateinit var brightnessOverlay: View

    /** Current brightness level 0–100. Default matches the app default (60). */
    private var currentBrightnessLevel: Int = 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root container that hosts all content views
        rootContainer = FrameLayout(context)
        rootContainer.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Dimming overlay — always the top-most child so it covers all content
        brightnessOverlay = View(context)
        brightnessOverlay.setBackgroundColor(Color.BLACK)
        brightnessOverlay.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        // Mark it so it never intercepts touch (secondary screen is display-only anyway)
        brightnessOverlay.isClickable = false
        brightnessOverlay.isFocusable = false

        // A FrameLayout wrapping both content area and the overlay
        val wrapper = FrameLayout(context)
        wrapper.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        wrapper.addView(rootContainer)
        wrapper.addView(brightnessOverlay)

        setContentView(wrapper)

        // Apply default brightness immediately
        applyBrightnessOverlay(currentBrightnessLevel)
    }

    fun showView(view: View) {
        rootContainer.removeAllViews()

        // Ensure view is not attached to another parent
        (view.parent as? ViewGroup)?.removeView(view)

        rootContainer.addView(view)
        // Re-apply overlay so it stays on top after content change
        applyBrightnessOverlay(currentBrightnessLevel)
    }

    /**
     * Sets the software brightness level (0–100).
     * 100 = fully bright (overlay fully transparent).
     * 0   = fully dark   (overlay fully opaque).
     */
    fun setBrightness(level: Int) {
        currentBrightnessLevel = level.coerceIn(0, 100)
        applyBrightnessOverlay(currentBrightnessLevel)
        Log.d(tag, "Software brightness set to $currentBrightnessLevel% → overlay alpha=${brightnessOverlay.alpha}")
    }

    private fun applyBrightnessOverlay(level: Int) {
        // alpha 0.0 = transparent (full brightness), 1.0 = opaque black (no light)
        // We cap the max dimming at 0.85 so the screen never goes completely black
        // while the app is running (only power(false) should fully blank it).
        val overlayAlpha = ((100 - level) / 100f * 0.85f).coerceIn(0f, 0.85f)
        if (::brightnessOverlay.isInitialized) {
            brightnessOverlay.alpha = overlayAlpha
        }
    }

    fun getResolution(): IntArray {
        val metrics = context.resources.displayMetrics
        return intArrayOf(metrics.widthPixels, metrics.heightPixels)
    }
}
