package org.xiaobu.autoclick.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Point
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

@SuppressLint("AccessibilityPolicy")
class AutoClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val DEFAULT_TAP_DURATION_MS = 20L

        @Volatile
        private var currentService: AutoClickAccessibilityService? = null

        fun isServiceEnabled(context: Context): Boolean {
            if (currentService != null) return true
            val expectedService = ComponentName(context, AutoClickAccessibilityService::class.java)
                .flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1

            return accessibilityEnabled && enabledServices
                .split(':')
                .any { it.equals(expectedService, ignoreCase = true) }
        }

        fun dispatchTap(
            x: Int,
            y: Int,
            durationMs: Long = DEFAULT_TAP_DURATION_MS
        ): Boolean {
            return currentService?.tap(Point(x, y), durationMs) == true
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (currentService === this) {
            currentService = null
        }
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (currentService === this) {
            currentService = null
        }
        return super.onUnbind(intent)
    }

    private fun tap(point: Point, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(point.x.toFloat(), point.y.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    durationMs.coerceAtLeast(1L)
                )
            )
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
