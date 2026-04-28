package org.xiaobu.autoclick.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt
import org.xiaobu.autoclick.AutoClickApp
import org.xiaobu.autoclick.MainActivity
import org.xiaobu.autoclick.R
import org.xiaobu.autoclick.ui.component.AutoTaskCoordinatePickerOverlay
import org.xiaobu.autoclick.ui.theme.AutoclickTheme

class AutoTaskCoordinatePickerService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val ACTION_SHOW = "org.xiaobu.autoclick.action.SHOW_AUTO_TASK_COORDINATE_PICKER"
        private const val ACTION_HIDE = "org.xiaobu.autoclick.action.HIDE_AUTO_TASK_COORDINATE_PICKER"
        private const val EXTRA_X = "extra_x"
        private const val EXTRA_Y = "extra_y"
        private const val NOTIFICATION_CHANNEL_ID = "auto_task_coordinate_picker"
        private const val NOTIFICATION_ID = 20022
        private const val MARKER_SIZE_DP = 26

        @Volatile
        private var pickerVisible = false

        @Volatile
        private var pickedPoint: Point? = null

        fun show(context: Context, x: Int? = null, y: Int? = null) {
            val intent = Intent(context, AutoTaskCoordinatePickerService::class.java).apply {
                action = ACTION_SHOW
                x?.let { putExtra(EXTRA_X, it) }
                y?.let { putExtra(EXTRA_Y, it) }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun hide(context: Context) {
            context.startService(
                Intent(context, AutoTaskCoordinatePickerService::class.java).apply {
                    action = ACTION_HIDE
                }
            )
        }

        fun consumePickedPoint(): Point? {
            val point = pickedPoint
            pickedPoint = null
            return point
        }

        fun isPickerVisible(): Boolean = pickerVisible
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var selectionView: ComposeView? = null
    private var markerView: FrameLayout? = null
    private var markerParams: WindowManager.LayoutParams? = null
    private var selectedX by mutableStateOf<Int?>(null)
    private var selectedY by mutableStateOf<Int?>(null)
    private var selecting by mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureNotificationChannel()
        startForegroundWithType(buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        when (intent?.action) {
            ACTION_HIDE -> {
                stopPicker()
                stopSelf()
            }

            else -> {
                selectedX = intent?.getIntExtra(EXTRA_X, -1)?.takeIf { it >= 0 }
                selectedY = intent?.getIntExtra(EXTRA_Y, -1)?.takeIf { it >= 0 }
                selecting = false
                showPanel()
                updateMarker()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPicker()
        pickerVisible = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun showPanel() {
        if (!Settings.canDrawOverlays(this)) {
            AutoClickApp.showToast("请先开启悬浮窗权限")
            stopSelf()
            return
        }
        if (panelView == null) {
            val composeView = ComposeView(this).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setViewTreeLifecycleOwner(this@AutoTaskCoordinatePickerService)
                setViewTreeSavedStateRegistryOwner(this@AutoTaskCoordinatePickerService)
                setContent {
                    AutoclickTheme {
                        AutoTaskCoordinatePickerOverlay(
                            selecting = selecting,
                            selectedX = selectedX,
                            selectedY = selectedY,
                            onStartSelecting = ::beginSelecting,
                            onConfirm = ::confirmSelection,
                            onCancel = { hide(this@AutoTaskCoordinatePickerService) },
                            onMove = ::movePanel
                        )
                    }
                }
            }
            val params = createLayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = ((resources.displayMetrics.widthPixels - 220.dpPx) / 2).coerceAtLeast(0)
                y = 84.dpPx
            }
            panelView = composeView
            panelParams = params
            windowManager.addView(composeView, params)
        }
        pickerVisible = true
    }

    private fun beginSelecting() {
        selecting = true
        showSelectionOverlay()
    }

    private fun confirmSelection() {
        val x = selectedX ?: return
        val y = selectedY ?: return
        pickedPoint = Point(x, y)
        bringAppToFront()
        hide(this)
    }

    private fun bringAppToFront() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(intent) }
    }

    private fun showSelectionOverlay() {
        if (selectionView != null) return
        val composeView = ComposeView(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewTreeLifecycleOwner(this@AutoTaskCoordinatePickerService)
            setViewTreeSavedStateRegistryOwner(this@AutoTaskCoordinatePickerService)
            setContent {
                AutoclickTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.16f))
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    handleSelectionTap(offset.x, offset.y)
                                }
                            }
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "单击屏幕添加坐标",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
        val params = createLayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        selectionView = composeView
        windowManager.addView(composeView, params)
    }

    private fun handleSelectionTap(localX: Float, localY: Float) {
        val location = IntArray(2)
        selectionView?.getLocationOnScreen(location)
        selectedX = location[0] + localX.roundToInt()
        selectedY = location[1] + localY.roundToInt()
        selecting = false
        updateMarker()
        removeSelectionOverlay()
    }

    private fun removeSelectionOverlay() {
        selectionView?.let { view -> runCatching { windowManager.removeView(view) } }
        selectionView = null
        selecting = false
    }

    private fun updateMarker() {
        val x = selectedX
        val y = selectedY
        if (x == null || y == null) {
            markerView?.let { runCatching { windowManager.removeView(it) } }
            markerView = null
            markerParams = null
            return
        }
        val size = MARKER_SIZE_DP.dpPx
        if (markerView == null) {
            val view = createMarkerView(size)
            val params = createLayoutParams(size, size).apply {
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            }
            markerView = view
            markerParams = params
            windowManager.addView(view, params)
        }
        markerParams?.let { params ->
            params.x = x - size / 2
            params.y = y - size / 2
            markerView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
        }
    }

    private fun createMarkerView(size: Int): FrameLayout {
        val crossColor = 0xFFFFFFFF.toInt()
        val lineWidth = 2.dpPx
        val dotSize = 6.dpPx
        return FrameLayout(this).apply {
            addView(
                android.view.View(this@AutoTaskCoordinatePickerService).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = 1.dpPx.toFloat()
                        setColor(crossColor)
                    }
                },
                FrameLayout.LayoutParams(size, lineWidth, Gravity.CENTER)
            )
            addView(
                android.view.View(this@AutoTaskCoordinatePickerService).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = 1.dpPx.toFloat()
                        setColor(crossColor)
                    }
                },
                FrameLayout.LayoutParams(lineWidth, size, Gravity.CENTER)
            )
            addView(
                android.view.View(this@AutoTaskCoordinatePickerService).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(crossColor)
                    }
                },
                FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER)
            )
        }
    }

    private fun stopPicker() {
        removeSelectionOverlay()
        markerView?.let { view -> runCatching { windowManager.removeView(view) } }
        markerView = null
        markerParams = null
        panelView?.let { view -> runCatching { windowManager.removeView(view) } }
        panelView = null
        panelParams = null
        selecting = false
        pickerVisible = false
    }

    private fun movePanel(offsetX: Int, offsetY: Int) {
        val view = panelView ?: return
        val params = panelParams ?: return
        val maxX = (resources.displayMetrics.widthPixels - view.width.coerceAtLeast(180.dpPx)).coerceAtLeast(0)
        val maxY = (resources.displayMetrics.heightPixels - view.height.coerceAtLeast(120.dpPx)).coerceAtLeast(0)
        params.x = (params.x + offsetX).coerceIn(0, maxX)
        params.y = (params.y + offsetY).coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun createLayoutParams(width: Int, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "自动任务选点器",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "自动任务坐标选点悬浮窗"
            }
        )
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("自动任务选点器")
            .setContentText("先开始选择，再单击目标位置，最后确认坐标")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private val Int.dpPx: Int
        get() = (this * resources.displayMetrics.density).roundToInt()
}
