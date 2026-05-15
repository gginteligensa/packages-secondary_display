package com.kozen.secondary_display

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * KozenPresentation — Standard Android Presentation class for secondary display.
 * This avoids using the conflicting Kozen Component SDK.
 */
class KozenPresentation(outerContext: Context, display: Display) : Presentation(outerContext, display) {

    private lateinit var rootContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // We create a root container to host any view we want to show
        rootContainer = FrameLayout(context)
        rootContainer.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(rootContainer)
    }

    fun showView(view: View) {
        rootContainer.removeAllViews()
        
        // Ensure view is not attached to another parent
        (view.parent as? ViewGroup)?.removeView(view)
        
        rootContainer.addView(view)
    }
    
    fun getResolution(): IntArray {
        val metrics = context.resources.displayMetrics
        return intArrayOf(metrics.widthPixels, metrics.heightPixels)
    }
}
