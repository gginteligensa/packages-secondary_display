package com.kozen.secondary_display

import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.hardware.display.DisplayManager
import android.view.Display
import com.kozen.component_client.ComponentEngine
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/**
 * SecondaryDisplayPlugin — Flutter Plugin entry point.
 * Single Responsibility: register the MethodChannel and delegate
 * every call to the appropriate manager.
 *
 * Business logic is in [ScreenUIManager].
 * Animation logic is in [AnimationHelper].
 */
class SecondaryDisplayPlugin : FlutterPlugin, MethodCallHandler {

    private val tag = "SD_Plugin"
    private val channelName = "secondary_display"

    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var uiManager: ScreenUIManager

    // ─── Queue for SDK Initialization ─────────────────────────────────────────
    private var pendingCallback: (() -> Unit)? = null
    private var isInitializing = false
    private var lastInitAttemptTime = 0L
    private val INIT_COOLDOWN_MS = 10000L // 10 seconds cooldown

    // ─── Theme defaults (overridable via initialize()) ────────────────────────
    private var approvedColorTop: Int = 0xFF00C853.toInt()
    private var approvedColorBottom: Int = 0xFF1B5E20.toInt()
    private var rejectedColorTop: Int = 0xFFD32F2F.toInt()
    private var rejectedColorBottom: Int = 0xFF7F0000.toInt()
    private var approvedLabel: String = "APROBADO"
    private var rejectedLabel: String = "DENEGADO"
    private var customWallpaperLogoPath: String? = null
    private var customGifPath: String? = null

    private var presentation: KozenPresentation? = null
    private var lifecycleCallbacks: android.app.Application.ActivityLifecycleCallbacks? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        uiManager = ScreenUIManager(context)
        channel = MethodChannel(binding.binaryMessenger, channelName)
        channel.setMethodCallHandler(this)
        
        // ⚠️ SDK init is now LAZY - only on explicit 'initialize' call from Flutter.
        // This ensures the card reader hardware bus is free at startup.
        
        setupPresentation()
        
        lifecycleCallbacks = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {
                Log.d(tag, "Activity stopped (App backgrounded/exiting). Showing wallpaper.")
                uiManager.showWallpaper(customWallpaperLogoPath) {}
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        }
        (context as? android.app.Application)?.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        
        Log.d(tag, "SecondaryDisplayPlugin attached")
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        lifecycleCallbacks?.let {
            (context as? android.app.Application)?.unregisterActivityLifecycleCallbacks(it)
        }
        lifecycleCallbacks = null
        Log.d(tag, "SecondaryDisplayPlugin detached")
    }

    // ─── Presentation Setup ──────────────────────────────────────────────────
    
    private fun setupPresentation() {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        
        Handler(Looper.getMainLooper()).post {
            val allDisplays = dm.displays
            Log.d(tag, "--- Available Displays (${allDisplays.size}) ---")
            for (d in allDisplays) {
                Log.d(tag, "Display: ID=${d.displayId}, Name=${d.name}, Flags=${d.flags}")
            }
            
            val presentationDisplays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            var targetDisplay: Display? = if (presentationDisplays.isNotEmpty()) presentationDisplays[0] else null
            
            if (targetDisplay == null && allDisplays.size > 1) {
                targetDisplay = allDisplays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            }
            
            if (targetDisplay != null) {
                Log.d(tag, "Secondary display selected: ${targetDisplay.name} (ID=${targetDisplay.displayId})")
                try {
                    presentation = KozenPresentation(context, targetDisplay)
                    presentation?.show()
                    uiManager.presentation = presentation
                    Log.d(tag, "✅ KozenPresentation initialized and shown")
                } catch (e: Exception) {
                    Log.e(tag, "❌ Failed to initialize KozenPresentation: ${e.message}")
                }
            } else {
                Log.w(tag, "⚠️ No secondary display found natively. We might need the SDK manager.")
            }
        }
    }

    /**
     * Ensures the Kozen SDK is initialized before any UI operation.
     * Thread-safe and debounced to prevent concurrent initialization and display requests.
     */
    private fun ensureSDKReady(onReady: () -> Unit) {
        if (presentation != null) {
            onReady()
            return
        }
        val mgr = ComponentEngine.secondaryScreenManager
        val isAlive = (mgr as? android.os.IInterface)?.asBinder()?.isBinderAlive == true
        if (mgr != null && isAlive) {
            onReady()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastInitAttemptTime < INIT_COOLDOWN_MS) {
            Log.d(tag, "ensureSDKReady: Skipping initialization due to cooldown. SDK is not connected.")
            onReady()
            return
        }
        lastInitAttemptTime = now

        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
             val callbackToRun = synchronized(this) {
                if (isInitializing) {
                    Log.w(tag, "⚠️ ensureSDKReady: SDK init timed out after 2.5 seconds. Resetting state.")
                    isInitializing = false
                    val cb = pendingCallback
                    pendingCallback = null
                    cb
                } else {
                    null
                }
            }
            callbackToRun?.let {
                try {
                    it()
                } catch (e: Exception) {
                    Log.e(tag, "Error executing callback after timeout: ${e.message}")
                }
            }
        }
        
        synchronized(this) {
            pendingCallback = onReady
            if (isInitializing) {
                Log.d(tag, "ensureSDKReady: Already initializing. Overwrote pending callback.")
                return
            }
            isInitializing = true
        }

        Log.d(tag, "ensureSDKReady: SDK not yet initialized or dead (isAlive=$isAlive). Initializing now...")
        
        // Schedule safety timeout
        handler.postDelayed(timeoutRunnable, 2500)

        try {
            ComponentEngine.init(context) { code, errorMsg ->
                handler.removeCallbacks(timeoutRunnable)
                Handler(Looper.getMainLooper()).post {
                    val callbackToRun = synchronized(this) {
                        if (!isInitializing) return@post
                        isInitializing = false
                        val cb = pendingCallback
                        pendingCallback = null
                        cb
                    }
                    if (code == 0) {
                        Log.d(tag, "✅ SDK initialized successfully in ensureSDKReady.")
                    } else {
                        Log.e(tag, "❌ SDK init failed in ensureSDKReady: $code - $errorMsg.")
                    }
                    callbackToRun?.let {
                        try {
                            it()
                        } catch (e: Exception) {
                            Log.e(tag, "Error executing callback: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            handler.removeCallbacks(timeoutRunnable)
            Log.e(tag, "Failed to call ComponentEngine.init in ensureSDKReady: ${e.message}")
            val callbackToRun = synchronized(this) {
                isInitializing = false
                val cb = pendingCallback
                pendingCallback = null
                cb
            }
            callbackToRun?.let {
                try {
                    it()
                } catch (e: Exception) {
                    Log.e(tag, "Error executing callback: ${e.message}")
                }
            }
        }
    }

    // ─── MethodCallHandler ────────────────────────────────────────────────────

    override fun onMethodCall(call: MethodCall, result: Result) {

        when (call.method) {
            // ── Initialization & Config ──
            "initialize" -> {
                applyThemeFromArgs(call)
                lastInitAttemptTime = 0L
                ensureSDKReady {
                    result.success(true)
                }
            }

            // ── Basic controls ──
            "getScreenResolution" -> {
                val res = presentation?.getResolution()
                if (res != null && res.size >= 2) {
                    result.success(mapOf("width" to res[0], "height" to res[1]))
                } else {
                    result.error("ERROR", "Could not get resolution", null)
                }
            }
            "power" -> {
                // Not supported via standard Presentation API easily, but we can dismiss/show
                val on = call.argument<Boolean>("on") ?: true
                if (on) presentation?.show() else presentation?.dismiss()
                result.success(true)
            }
            "setBrightness" -> {
                // Not supported via standard Presentation API
                result.success(false)
            }
            "getBrightness" -> {
                result.success(100)
            }
            "getPowerOnStatus" -> {
                result.success(presentation?.isShowing == true)
            }

            // ── UI Screens ──
            "showWallpaper" -> {
                ensureSDKReady {
                    uiManager.showWallpaper(customWallpaperLogoPath) { success: Boolean ->
                        result.success(success)
                    }
                }
            }
            "showWelcome" -> {
                ensureSDKReady {
                    uiManager.showWelcome { success: Boolean -> result.success(success) }
                }
            }
            "showAmount" -> {
                val amount = call.argument<String>("amount") ?: "0,00"
                val title = call.argument<String>("title") ?: ""
                val currency = call.argument<String>("currency") ?: ""
                ensureSDKReady {
                    uiManager.showAmount(amount, title, currency) { success: Boolean ->
                        result.success(success)
                    }
                }
            }
            "showStatus" -> {
                val title = call.argument<String>("title") ?: ""
                val subtitle = call.argument<String>("subtitle") ?: ""
                ensureSDKReady {
                    uiManager.showStatus(title, subtitle) { success: Boolean ->
                        result.success(success)
                    }
                }
            }

            // ── Animated Screens ──
            "showReadCard" -> {
                ensureSDKReady {
                    uiManager.showReadCard(customGifPath) { success: Boolean ->
                        result.success(success)
                    }
                }
            }
            "showApproved" -> {
                ensureSDKReady {
                    uiManager.showApproved(
                        bgColorTop = approvedColorTop,
                        bgColorBottom = approvedColorBottom,
                        label = approvedLabel
                    ) { success: Boolean -> result.success(success) }
                }
            }
            "showRejected" -> {
                val message = call.argument<String>("message") ?: ""
                ensureSDKReady {
                    uiManager.showRejected(
                        message = message,
                        bgColorTop = rejectedColorTop,
                        bgColorBottom = rejectedColorBottom,
                        label = rejectedLabel
                    ) { success: Boolean -> result.success(success) }
                }
            }

            "pauseForCardRead" -> {
                Log.d("HW_BUS", "======================================")
                Log.d("HW_BUS", ">>> PAUSE: pauseForCardRead called")
                if (presentation != null) {
                    Log.d("HW_BUS", "    Bypassing ComponentEngine.deInit() because native Presentation is active.")
                } else {
                    Log.d("HW_BUS", "    Calling ComponentEngine.deInit() to release hardware bus")
                    try {
                        ComponentEngine.deInit()
                        Log.d("HW_BUS", ">>> PAUSE: ✅ deInit() OK — bus is FREE")
                    } catch (e: Exception) {
                        Log.e("HW_BUS", ">>> PAUSE: ❌ deInit() FAILED: ${e.message}")
                    }
                }
                Log.d("HW_BUS", "======================================")
                result.success(true)
            }
            "resumeAfterCardRead" -> {
                Log.d("HW_BUS", "======================================")
                Log.d("HW_BUS", "<<< RESUME: resumeAfterCardRead called")
                ensureSDKReady {
                    Log.d("HW_BUS", "<<< RESUME: ✅ SDK re-init / ready")
                    Log.d("HW_BUS", "======================================")
                    result.success(true)
                }
            }

            else -> result.notImplemented()
        }
    }

    // ─── Theme application ────────────────────────────────────────────────────

    private fun applyThemeFromArgs(call: MethodCall) {
        val theme = call.argument<Map<String, Any>>("theme") ?: return

        (theme["primaryColorHex"] as? String)?.let { hex ->
            try { approvedColorTop = android.graphics.Color.parseColor(hex) } catch (_: Exception) {}
        }
        (theme["secondaryColorHex"] as? String)?.let { hex ->
            try { approvedColorBottom = android.graphics.Color.parseColor(hex) } catch (_: Exception) {}
        }
        (theme["rejectedColorTopHex"] as? String)?.let { hex ->
            try { rejectedColorTop = android.graphics.Color.parseColor(hex) } catch (_: Exception) {}
        }
        (theme["rejectedColorBottomHex"] as? String)?.let { hex ->
            try { rejectedColorBottom = android.graphics.Color.parseColor(hex) } catch (_: Exception) {}
        }
        (theme["approvedLabel"] as? String)?.let { approvedLabel = it }
        (theme["rejectedLabel"] as? String)?.let { rejectedLabel = it }
        (theme["wallpaperLogoPath"] as? String)?.let { customWallpaperLogoPath = it }
        (theme["readCardGifPath"] as? String)?.let { customGifPath = it }

        Log.d(tag, "Theme applied: approved=$approvedLabel, rejected=$rejectedLabel")
    }
}
