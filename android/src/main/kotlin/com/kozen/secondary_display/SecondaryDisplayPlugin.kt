package com.kozen.secondary_display

import android.content.Context
import android.util.Log
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

    // ─── Theme defaults (overridable via initialize()) ────────────────────────
    private var approvedColorTop: Int = 0xFF00C853.toInt()
    private var approvedColorBottom: Int = 0xFF1B5E20.toInt()
    private var rejectedColorTop: Int = 0xFFD32F2F.toInt()
    private var rejectedColorBottom: Int = 0xFF7F0000.toInt()
    private var approvedLabel: String = "APROBADO"
    private var rejectedLabel: String = "DENEGADO"
    private var customWallpaperLogoPath: String? = null
    private var customGifPath: String? = null

    private var lifecycleCallbacks: android.app.Application.ActivityLifecycleCallbacks? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        uiManager = ScreenUIManager(context)
        channel = MethodChannel(binding.binaryMessenger, channelName)
        channel.setMethodCallHandler(this)
        initSDK()
        
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

    // ─── SDK init ─────────────────────────────────────────────────────────────

    private fun initSDK() {
        ComponentEngine.init(context) { result, errorMsg ->
            when {
                result == 0 -> Log.d(tag, "SDK initialized")
                result == -10004 -> Log.w(tag, "SDK version mismatch, continuing anyway")
                else -> Log.e(tag, "SDK init failed: $result - $errorMsg")
            }
        }
    }

    // ─── MethodCallHandler ────────────────────────────────────────────────────

    override fun onMethodCall(call: MethodCall, result: Result) {
        // Ensure SDK manager is available
        if (ComponentEngine.secondaryScreenManager == null) {
            initSDK()
        }

        when (call.method) {
            // ── Initialization & Config ──
            "initialize" -> {
                applyThemeFromArgs(call)
                result.success(true)
            }

            // ── Basic controls ──
            "getScreenResolution" -> {
                val res = ComponentEngine.secondaryScreenManager?.screenResolution
                if (res != null && res.size >= 2) {
                    result.success(mapOf("width" to res[0], "height" to res[1]))
                } else {
                    result.error("ERROR", "Could not get resolution", null)
                }
            }
            "power" -> {
                val on = call.argument<Boolean>("on") ?: true
                val status = ComponentEngine.secondaryScreenManager?.power(on)
                result.success(status == 0)
            }
            "setBrightness" -> {
                val value = call.argument<Int>("value") ?: 50
                val status = ComponentEngine.secondaryScreenManager?.setBrightness(value)
                result.success(status == 0)
            }
            "getBrightness" -> {
                result.success(ComponentEngine.secondaryScreenManager?.brightness)
            }
            "getPowerOnStatus" -> {
                result.success(ComponentEngine.secondaryScreenManager?.powerOnStatus == 0)
            }

            // ── UI Screens ──
            "showWallpaper" -> {
                uiManager.showWallpaper(customWallpaperLogoPath) { success ->
                    result.success(success)
                }
            }
            "showWelcome" -> {
                uiManager.showWelcome { success -> result.success(success) }
            }
            "showAmount" -> {
                val amount = call.argument<String>("amount") ?: "0,00"
                val title = call.argument<String>("title") ?: ""
                val currency = call.argument<String>("currency") ?: ""
                uiManager.showAmount(amount, title, currency) { success ->
                    result.success(success)
                }
            }
            "showStatus" -> {
                val title = call.argument<String>("title") ?: ""
                val subtitle = call.argument<String>("subtitle") ?: ""
                uiManager.showStatus(title, subtitle) { success ->
                    result.success(success)
                }
            }

            // ── Animated Screens ──
            "showReadCard" -> {
                uiManager.showReadCard(customGifPath) { success ->
                    result.success(success)
                }
            }
            "showApproved" -> {
                uiManager.showApproved(
                    bgColorTop = approvedColorTop,
                    bgColorBottom = approvedColorBottom,
                    label = approvedLabel
                ) { success -> result.success(success) }
            }
            "showRejected" -> {
                val message = call.argument<String>("message") ?: ""
                uiManager.showRejected(
                    message = message,
                    bgColorTop = rejectedColorTop,
                    bgColorBottom = rejectedColorBottom,
                    label = rejectedLabel
                ) { success -> result.success(success) }
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
