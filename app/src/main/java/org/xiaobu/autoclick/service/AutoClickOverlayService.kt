package org.xiaobu.autoclick.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.xiaobu.autoclick.AutoClickApp
import org.xiaobu.autoclick.data.click.AutoClickPointConfig
import org.xiaobu.autoclick.ui.component.AutoClickOverlayPanel
import org.xiaobu.autoclick.ui.component.OverlayQuickControlBubble
import org.xiaobu.autoclick.ui.theme.AutoclickTheme

class AutoClickOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val ACTION_SHOW = "org.xiaobu.autoclick.action.SHOW_AUTO_CLICK_OVERLAY"
        private const val ACTION_HIDE = "org.xiaobu.autoclick.action.HIDE_AUTO_CLICK_OVERLAY"
        private const val ACTION_REFRESH = "org.xiaobu.autoclick.action.REFRESH_AUTO_CLICK_OVERLAY"
        private const val POINTER_TOUCH_SIZE_DP = 42
        private const val POINTER_VISUAL_SIZE_DP = 18
        private const val POINTER_BADGE_SIZE_DP = 12
        private const val MAX_POINTS = 10

        @Volatile
        private var overlayVisible = false

        @Volatile
        private var clicking = false

        fun show(context: Context) {
            context.startService(Intent(context, AutoClickOverlayService::class.java).apply {
                action = ACTION_SHOW
            })
        }

        fun hide(context: Context) {
            context.startService(Intent(context, AutoClickOverlayService::class.java).apply {
                action = ACTION_HIDE
            })
        }

        fun refresh(context: Context) {
            context.startService(Intent(context, AutoClickOverlayService::class.java).apply {
                action = ACTION_REFRESH
            })
        }

        fun isOverlayVisible(): Boolean = overlayVisible

        fun isClicking(): Boolean = clicking
    }

    private data class PointerOverlay(
        val view: FrameLayout,
        val params: WindowManager.LayoutParams
    )

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pointerOverlays = linkedMapOf<String, PointerOverlay>()

    private lateinit var windowManager: WindowManager
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelAttached = false
    private var quickControlView: ComposeView? = null
    private var quickControlParams: WindowManager.LayoutParams? = null
    private var clickJob: Job? = null
    private var addingPointId: String? = null
    private var completedClickCount = 0
    private var panelStatusText by mutableStateOf("悬浮控制器已就绪")
    private var panelClicking by mutableStateOf(false)
    private var panelAddingMode by mutableStateOf(false)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val app: AutoClickApp
        get() = application as AutoClickApp

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        when (intent?.action) {
            ACTION_HIDE -> {
                stopClicking()
                removeOverlay()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_REFRESH -> {
                if (panelView != null || pointerOverlays.isNotEmpty()) {
                    rebuildPointerViews()
                    updatePanelState()
                }
            }

            else -> showOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopClicking()
        removeOverlay()
        serviceScope.cancel()
        overlayVisible = false
        clicking = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            AutoClickApp.showToast("请先开启悬浮窗权限")
            stopSelf()
            return
        }
        ensureAtLeastOnePoint()
        rebuildPointerViews()
        if (panelView == null) {
            createPanelView()
        }
        overlayVisible = true
        updatePanelState()
    }

    private fun ensureAtLeastOnePoint() {
        val config = app.autoClickStore.getConfig()
        if (config.points.isNotEmpty()) return
        val centerPoint = resolveSuggestedPoint(config.points.size)
        val newPoint = app.autoClickStore.addPoint(centerPoint.first, centerPoint.second)
        addingPointId = newPoint?.id
    }

    private fun rebuildPointerViews() {
        removePointerViews()
        val points = app.autoClickStore.getConfig().points
        points.forEachIndexed { index, point ->
            val pointerOverlay = createPointerOverlay(
                point = point,
                order = index + 1,
                highlighted = point.id == addingPointId
            )
            pointerOverlays[point.id] = pointerOverlay
            runCatching { windowManager.addView(pointerOverlay.view, pointerOverlay.params) }
        }
        if (clicking) {
            setPointerTouchable(false)
        }
        overlayVisible = pointerOverlays.isNotEmpty() || panelView != null
    }

    private fun createPointerOverlay(
        point: AutoClickPointConfig,
        order: Int,
        highlighted: Boolean
    ): PointerOverlay {
        val touchSize = POINTER_TOUCH_SIZE_DP.dp
        val indicatorSize = POINTER_VISUAL_SIZE_DP.dp
        val params = createLayoutParams(touchSize, touchSize).apply {
            x = point.x - touchSize / 2
            y = point.y - touchSize / 2
        }
        val container = FrameLayout(this)
        val ringColor = if (highlighted) 0xFFFF7043.toInt() else 0xE6FFFFFF.toInt()
        val dotColor = if (highlighted) 0xFFFF7043.toInt() else 0xFFFFFFFF.toInt()
        val indicatorContainer = FrameLayout(this)
        val ringView = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x0FFFFFFF)
                setStroke(2.dp, ringColor)
            }
        }
        val horizontalLine = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 1.dp.toFloat()
                setColor(ringColor)
            }
        }
        val verticalLine = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 1.dp.toFloat()
                setColor(ringColor)
            }
        }
        val dotView = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
            }
        }
        val orderView = TextView(this).apply {
            text = order.toString()
            setTextColor(0xFF121212.toInt())
            textSize = 9f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFFFFF.toInt())
            }
        }

        indicatorContainer.addView(
            ringView,
            FrameLayout.LayoutParams(indicatorSize, indicatorSize, Gravity.CENTER)
        )
        indicatorContainer.addView(
            horizontalLine,
            FrameLayout.LayoutParams(indicatorSize + 6.dp, 1.dp, Gravity.CENTER)
        )
        indicatorContainer.addView(
            verticalLine,
            FrameLayout.LayoutParams(1.dp, indicatorSize + 6.dp, Gravity.CENTER)
        )
        indicatorContainer.addView(
            dotView,
            FrameLayout.LayoutParams(4.dp, 4.dp, Gravity.CENTER)
        )
        container.addView(
            indicatorContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        container.addView(
            orderView,
            FrameLayout.LayoutParams(
                POINTER_BADGE_SIZE_DP.dp,
                POINTER_BADGE_SIZE_DP.dp,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = 2.dp
                rightMargin = 2.dp
            }
        )

        enablePointerDrag(
            view = container,
            params = params,
            pointId = point.id,
            size = touchSize
        )
        return PointerOverlay(view = container, params = params)
    }

    private fun createPanelView() {
        val composeView = ComposeView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setViewTreeLifecycleOwner(this@AutoClickOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AutoClickOverlayService)
            setContent {
                AutoclickTheme {
                    AutoClickOverlayPanel(
                        statusText = panelStatusText,
                        clicking = panelClicking,
                        addingMode = panelAddingMode,
                        onStartStop = {
                            if (clicking) stopClicking() else startClicking()
                        },
                        onAdd = ::beginAddPoint,
                        onFinish = ::finishAddPoint,
                        onCancel = ::cancelAddPoint,
                        onClose = { hide(this@AutoClickOverlayService) },
                        onMove = ::movePanel
                    )
                }
            }
        }
        val params = createLayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            x = 20.dp
            y = 120.dp
        }
        panelView = composeView
        panelParams = params
        attachPanelViewIfNeeded()
        syncPanelInteractionState()
    }

    private fun beginAddPoint() {
        if (clicking) {
            AutoClickApp.showToast("请先停止连点再添加指针")
            return
        }
        if (addingPointId != null) {
            updatePanelState()
            return
        }
        val currentPoints = app.autoClickStore.getConfig().points
        if (currentPoints.size >= MAX_POINTS) {
            AutoClickApp.showToast("最多只能添加 $MAX_POINTS 个指针")
            return
        }
        val suggestedPoint = resolveSuggestedPoint(currentPoints.size)
        val newPoint = app.autoClickStore.addPoint(
            x = suggestedPoint.first,
            y = suggestedPoint.second
        ) ?: return
        addingPointId = newPoint.id
        rebuildPointerViews()
        updatePanelState()
    }

    private fun finishAddPoint() {
        if (addingPointId == null) return
        addingPointId = null
        rebuildPointerViews()
        updatePanelState()
    }

    private fun cancelAddPoint() {
        val pointId = addingPointId ?: return
        app.autoClickStore.removePoint(pointId)
        addingPointId = null
        ensureAtLeastOnePoint()
        rebuildPointerViews()
        updatePanelState()
    }

    private fun startClicking() {
        if (addingPointId != null) {
            AutoClickApp.showToast("请先完成当前指针的添加")
            return
        }
        if (!AutoClickAccessibilityService.isServiceEnabled(this)) {
            AutoClickApp.showToast("请先开启连点器无障碍服务")
            updatePanelState()
            return
        }
        val currentPoints = app.autoClickStore.getConfig().points
        if (currentPoints.isEmpty()) {
            AutoClickApp.showToast("请先添加至少一个指针")
            return
        }

        stopClicking()
        clicking = true
        setPointerTouchable(false)
        syncPanelInteractionState()
        updatePanelState()
        clickJob = serviceScope.launch {
            val startTime = SystemClock.elapsedRealtime()
            var pointerIndex = 0
            completedClickCount = 0
            while (clicking) {
                val config = app.autoClickStore.getConfig()
                val points = config.points
                if (points.isEmpty()) {
                    AutoClickApp.showToast("当前没有可点击的指针")
                    break
                }
                val intervalMs = config.intervalMillis.coerceAtLeast(50).toLong()
                val durationMs = config.durationSeconds.toLong().coerceAtLeast(0L) * 1000L
                val maxClickCount = config.maxClickCount.coerceAtLeast(0)
                if (maxClickCount > 0 && completedClickCount >= maxClickCount) {
                    break
                }
                val elapsedBeforeTap = SystemClock.elapsedRealtime() - startTime
                if (durationMs > 0L && elapsedBeforeTap >= durationMs) {
                    break
                }
                val point = points[pointerIndex % points.size]
                val tapPoint = resolveTapPoint(point)
                val success = AutoClickAccessibilityService.dispatchTapAwait(tapPoint.x, tapPoint.y)
                if (!success) {
                    AutoClickApp.showToast("点击失败，请检查无障碍服务")
                    break
                }
                pointerIndex++
                completedClickCount++
                updatePanelState()
                if (maxClickCount > 0 && completedClickCount >= maxClickCount) {
                    break
                }
                if (durationMs > 0L) {
                    val remainingMs = durationMs - (SystemClock.elapsedRealtime() - startTime)
                    if (remainingMs <= 0L) break
                    delay(minOf(intervalMs, remainingMs))
                } else {
                    delay(intervalMs)
                }
            }
            clicking = false
            setPointerTouchable(true)
            syncPanelInteractionState()
            updatePanelState()
        }
    }

    private fun stopClicking() {
        clickJob?.cancel()
        clickJob = null
        clicking = false
        completedClickCount = 0
        setPointerTouchable(true)
        syncPanelInteractionState()
        updatePanelState()
    }

    private fun updatePanelState() {
        overlayVisible = panelView != null && pointerOverlays.isNotEmpty()
        val config = app.autoClickStore.getConfig()
        val pointCount = config.points.size
        panelClicking = clicking
        panelAddingMode = addingPointId != null
        panelStatusText = when {
            addingPointId != null -> "拖动新指针到目标位置，然后点击完成"
            clicking -> buildString {
                append("正在按顺序点击 $pointCount 个指针")
                append(" · 间隔 ${config.intervalMillis} 毫秒")
                if (config.durationSeconds > 0) {
                    append(" · ${config.durationSeconds} 秒")
                } else {
                    append(" · 持续点击")
                }
                if (config.maxClickCount > 0) {
                    append(" · $completedClickCount/${config.maxClickCount} 次")
                }
            }
            pointCount == 0 -> "先添加第一个指针，再开始连点"
            else -> "可以继续添加指针，确认位置后点击开始"
        }
        syncPanelInteractionState()
    }

    private fun removeOverlay() {
        stopClicking()
        removePointerViews()
        detachPanelViewIfNeeded()
        panelView = null
        panelParams = null
        removeQuickControlView()
        addingPointId = null
        overlayVisible = false
        panelStatusText = "悬浮控制器已就绪"
        completedClickCount = 0
        panelClicking = false
        panelAddingMode = false
    }

    private fun removePointerViews() {
        pointerOverlays.values.forEach { overlay ->
            runCatching { windowManager.removeView(overlay.view) }
        }
        pointerOverlays.clear()
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
            baseWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun baseWindowFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }

    private fun enablePointerDrag(
        view: View,
        params: WindowManager.LayoutParams,
        pointId: String,
        size: Int
    ) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        var downRawX = 0f
        var downRawY = 0f
        var initialX = 0
        var initialY = 0
        var dragging = false
        val longPressRunnable = Runnable {
            if (!dragging) {
                handlePointerLongPress(pointId)
            }
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    dragging = false
                    view.postDelayed(longPressRunnable, longPressTimeout)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && (abs(deltaX) >= touchSlop || abs(deltaY) >= touchSlop)) {
                        dragging = true
                        view.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        params.x = (initialX + deltaX.roundToInt()).coerceIn(
                            0,
                            (resources.displayMetrics.widthPixels - size).coerceAtLeast(0)
                        )
                        params.y = (initialY + deltaY.roundToInt()).coerceIn(
                            0,
                            (resources.displayMetrics.heightPixels - size).coerceAtLeast(0)
                        )
                        windowManager.updateViewLayout(view, params)
                        app.autoClickStore.updatePoint(
                            pointId = pointId,
                            x = params.x + size / 2,
                            y = params.y + size / 2
                        )
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(longPressRunnable)
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun handlePointerLongPress(pointId: String) {
        if (clicking) {
            AutoClickApp.showToast("请先停止连点再删除指针")
            return
        }
        val currentPoints = app.autoClickStore.getConfig().points
        if (currentPoints.size <= 1) {
            AutoClickApp.showToast("至少保留一个指针")
            return
        }
        app.autoClickStore.removePoint(pointId)
        if (addingPointId == pointId) {
            addingPointId = null
        }
        rebuildPointerViews()
        updatePanelState()
        AutoClickApp.showToast("指针已删除")
    }

    private fun movePanel(offsetX: Int, offsetY: Int) {
        val view = panelView ?: return
        val params = panelParams ?: return
        val maxX = (resources.displayMetrics.widthPixels - view.width.coerceAtLeast(170.dp))
            .coerceAtLeast(0)
        val maxY = (resources.displayMetrics.heightPixels - view.height.coerceAtLeast(120.dp))
            .coerceAtLeast(0)
        params.x = (params.x + offsetX).coerceIn(0, maxX)
        params.y = (params.y + offsetY).coerceIn(0, maxY)
        if (panelAttached) {
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        updateQuickControlPosition()
    }

    private fun syncPanelInteractionState() {
        if (clicking) {
            detachPanelViewIfNeeded()
            ensureQuickControlView()
            updateQuickControlPosition()
        } else {
            removeQuickControlView()
            attachPanelViewIfNeeded()
        }
    }

    private fun ensureQuickControlView() {
        if (quickControlView != null) return
        val composeView = ComposeView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setViewTreeLifecycleOwner(this@AutoClickOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AutoClickOverlayService)
            setContent {
                AutoclickTheme {
                    OverlayQuickControlBubble(
                        text = "停止",
                        onClick = ::stopClicking
                    )
                }
            }
        }
        val params = createLayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        quickControlView = composeView
        quickControlParams = params
        runCatching { windowManager.addView(composeView, params) }
        updateQuickControlPosition()
    }

    private fun updateQuickControlPosition() {
        val panel = panelView ?: return
        val panelLayoutParams = panelParams ?: return
        val quickView = quickControlView ?: return
        val quickParams = quickControlParams ?: return
        val panelWidth = panel.width.takeIf { it > 0 } ?: 156.dp
        val bubbleWidth = quickView.width.takeIf { it > 0 } ?: 72.dp
        quickParams.x = (panelLayoutParams.x + panelWidth - bubbleWidth / 2).coerceAtLeast(0)
        quickParams.y = (panelLayoutParams.y - 10.dp).coerceAtLeast(0)
        runCatching { windowManager.updateViewLayout(quickView, quickParams) }
    }

    private fun removeQuickControlView() {
        quickControlView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        quickControlView = null
        quickControlParams = null
    }

    private fun attachPanelViewIfNeeded() {
        val view = panelView ?: return
        val params = panelParams ?: return
        if (panelAttached) return
        runCatching { windowManager.addView(view, params) }
            .onSuccess { panelAttached = true }
    }

    private fun detachPanelViewIfNeeded() {
        val view = panelView ?: return
        if (!panelAttached) return
        runCatching { windowManager.removeView(view) }
        panelAttached = false
    }

    private fun resolveSuggestedPoint(index: Int): Pair<Int, Int> {
        val displayMetrics = resources.displayMetrics
        val baseX = displayMetrics.widthPixels / 2
        val baseY = displayMetrics.heightPixels / 2
        val step = 52.dp
        val offsets = listOf(
            0 to 0,
            step to 0,
            0 to step,
            -step to 0,
            0 to -step,
            step to step,
            -step to step,
            step to -step,
            -step to -step,
            step * 2 to 0
        )
        val (offsetX, offsetY) = offsets.getOrElse(index) {
            ((index % 3) - 1) * step to ((index / 3) - 1) * step
        }
        return (baseX + offsetX).coerceIn(20.dp, displayMetrics.widthPixels - 20.dp) to
            (baseY + offsetY).coerceIn(20.dp, displayMetrics.heightPixels - 20.dp)
    }

    private fun resolveTapPoint(point: AutoClickPointConfig): Point {
        val pointerView = pointerOverlays[point.id]?.view
        if (pointerView != null) {
            val location = IntArray(2)
            pointerView.getLocationOnScreen(location)
            return Point(
                location[0] + pointerView.width / 2,
                location[1] + pointerView.height / 2
            )
        }
        return Point(point.x, point.y)
    }

    private fun setPointerTouchable(touchable: Boolean) {
        pointerOverlays.values.forEach { overlay ->
            val targetFlags = if (touchable) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            if (overlay.params.flags != targetFlags) {
                overlay.params.flags = targetFlags
                runCatching { windowManager.updateViewLayout(overlay.view, overlay.params) }
            }
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()
}
