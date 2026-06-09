package org.xiaobu.autoclick.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
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
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.xiaobu.autoclick.AutoClickApp
import org.xiaobu.autoclick.data.task.AutoTaskFailureStrategy
import org.xiaobu.autoclick.ui.component.AutoTaskOverlayPanel
import org.xiaobu.autoclick.ui.component.OverlayQuickControlBubble
import org.xiaobu.autoclick.ui.theme.AutoclickTheme

class AutoTaskOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val ACTION_SHOW = "org.xiaobu.autoclick.action.SHOW_AUTO_TASK_OVERLAY"
        private const val ACTION_HIDE = "org.xiaobu.autoclick.action.HIDE_AUTO_TASK_OVERLAY"
        private const val ACTION_REFRESH = "org.xiaobu.autoclick.action.REFRESH_AUTO_TASK_OVERLAY"
        private const val EXTRA_AUTO_START = "extra_auto_start"

        @Volatile
        private var overlayVisible = false

        @Volatile
        private var taskRunning = false

        fun show(context: Context, autoStart: Boolean = false) {
            context.startService(Intent(context, AutoTaskOverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_AUTO_START, autoStart)
            })
        }

        fun hide(context: Context) {
            context.startService(Intent(context, AutoTaskOverlayService::class.java).apply {
                action = ACTION_HIDE
            })
        }

        fun refresh(context: Context) {
            context.startService(Intent(context, AutoTaskOverlayService::class.java).apply {
                action = ACTION_REFRESH
            })
        }

        fun isOverlayVisible(): Boolean = overlayVisible

        fun isTaskRunning(): Boolean = taskRunning
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelAttached = false
    private var quickControlView: ComposeView? = null
    private var quickControlParams: WindowManager.LayoutParams? = null
    private var runJob: Job? = null
    private var panelTaskName by mutableStateOf("自动点击器")
    private var panelStatusText by mutableStateOf("悬浮控制器已就绪")
    private var panelCurrentStepText by mutableStateOf("还没有开始执行")
    private var panelRunning by mutableStateOf(false)

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
                stopTask(showStopped = false)
                removeOverlay()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_REFRESH -> {
                if (panelView != null) {
                    updatePanelState()
                }
            }

            else -> {
                showOverlay()
                if (intent?.getBooleanExtra(EXTRA_AUTO_START, false) == true) {
                    startTask()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTask(showStopped = false)
        removeOverlay()
        serviceScope.cancel()
        overlayVisible = false
        taskRunning = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            AutoClickApp.showToast("请先开启悬浮窗权限")
            stopSelf()
            return
        }
        if (panelView == null) {
            createPanelView()
        }
        overlayVisible = true
        updatePanelState()
    }

    private fun createPanelView() {
        val composeView = ComposeView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setViewTreeLifecycleOwner(this@AutoTaskOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AutoTaskOverlayService)
            setContent {
                AutoclickTheme {
                    AutoTaskOverlayPanel(
                        taskName = panelTaskName,
                        statusText = panelStatusText,
                        currentStepText = panelCurrentStepText,
                        running = panelRunning,
                        onStartStop = {
                            if (taskRunning) stopTask() else startTask()
                        },
                        onClose = { hide(this@AutoTaskOverlayService) },
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
        syncOverlayInteractionState()
    }

    private fun startTask() {
        if (taskRunning) return
        if (!AutoClickAccessibilityService.isServiceEnabled(this)) {
            AutoClickApp.showToast("请先开启无障碍权限")
            updatePanelState("请先开启无障碍权限", "还没有开始执行")
            return
        }
        val task = app.autoTaskStore.getDraft()
        if (task.steps.isEmpty()) {
            AutoClickApp.showToast("请先添加至少一个步骤")
            updatePanelState("当前自动点击器没有步骤", "还没有开始执行")
            return
        }

        runJob?.cancel()
        taskRunning = true
        panelRunning = true
        panelTaskName = task.name.ifBlank { "自动点击器" }
        panelStatusText = if (task.repeatEnabled) {
            "重复运行中，将循环执行 ${task.steps.size} 个步骤"
        } else {
            "正在按顺序执行 ${task.steps.size} 个步骤"
        }
        panelCurrentStepText = "准备开始"
        syncOverlayInteractionState()

        runJob = serviceScope.launch {
            var finished = true
            var round = 0
            while (taskRunning) {
                round++
                for ((index, step) in task.steps.withIndex()) {
                    if (!taskRunning) {
                        finished = false
                        return@launch
                    }
                    panelCurrentStepText = buildString {
                        if (task.repeatEnabled) {
                            append("第 ")
                            append(round)
                            append(" 轮 · ")
                        }
                        append("步骤 ")
                        append(index + 1)
                        append("/")
                        append(task.steps.size)
                        append(" · ")
                        append(step.title.ifBlank { step.actionType.title })
                    }
                    val success = AutoClickAccessibilityService.executeTaskStepWithRetry(step)
                    if (!success) {
                        if (step.failureStrategy != AutoTaskFailureStrategy.CONTINUE) {
                            finished = false
                            panelStatusText = "执行失败，请检查步骤或权限"
                            panelCurrentStepText = "停在 ${step.title.ifBlank { step.actionType.title }}"
                            AutoClickApp.showToast("自动点击器执行失败")
                            syncOverlayInteractionState()
                            return@launch
                        }
                        panelStatusText = "步骤执行失败，已按策略继续"
                        panelCurrentStepText = "跳过 ${step.title.ifBlank { step.actionType.title }}"
                    }
                    if (step.delayAfterMs > 0L) {
                        delay(step.delayAfterMs)
                    }
                }
                if (!task.repeatEnabled) break
                if (taskRunning) {
                    panelStatusText = "重复运行中，将开始第 ${round + 1} 轮"
                    panelCurrentStepText = "本轮 ${task.steps.size} 个步骤已完成"
                }
            }
            taskRunning = false
            panelRunning = false
            if (finished) {
                if (task.repeatEnabled) {
                    panelStatusText = "重复执行已停止"
                    panelCurrentStepText = "已完成第 $round 轮执行"
                } else {
                    panelStatusText = "自动点击器已执行完成"
                    panelCurrentStepText = "已完成 ${task.steps.size} 个步骤"
                }
            }
            syncOverlayInteractionState()
        }
    }

    private fun stopTask(showStopped: Boolean = true) {
        runJob?.cancel()
        runJob = null
        taskRunning = false
        panelRunning = false
        if (showStopped) {
            panelStatusText = "自动点击器已停止"
            panelCurrentStepText = "可以重新启动当前任务"
        }
        syncOverlayInteractionState()
    }

    private fun updatePanelState(
        statusText: String? = null,
        stepText: String? = null
    ) {
        val task = app.autoTaskStore.getDraft()
        panelTaskName = task.name.ifBlank { "自动点击器" }
        if (!taskRunning) {
            panelStatusText = statusText ?: when {
                task.steps.isEmpty() -> "先添加步骤，再点击启动"
                task.repeatEnabled -> "已准备 ${task.steps.size} 个步骤，启动后会循环执行"
                else -> "已准备 ${task.steps.size} 个步骤"
            }
            panelCurrentStepText = stepText ?: if (task.steps.isEmpty()) {
                "还没有开始执行"
            } else {
                "待执行：${task.steps.first().title.ifBlank { task.steps.first().actionType.title }}"
            }
        }
        panelRunning = taskRunning
        overlayVisible = panelView != null
        syncOverlayInteractionState()
    }

    private fun removeOverlay() {
        detachPanelViewIfNeeded()
        panelView = null
        panelParams = null
        removeQuickControlView()
        panelTaskName = "自动点击器"
        panelStatusText = "悬浮控制器已就绪"
        panelCurrentStepText = "还没有开始执行"
        panelRunning = false
        overlayVisible = false
    }

    private fun movePanel(offsetX: Int, offsetY: Int) {
        val view = panelView ?: return
        val params = panelParams ?: return
        val maxX = (resources.displayMetrics.widthPixels - view.width.coerceAtLeast(180.dp))
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

    private fun syncOverlayInteractionState() {
        if (taskRunning) {
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
            setViewTreeLifecycleOwner(this@AutoTaskOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AutoTaskOverlayService)
            setContent {
                AutoclickTheme {
                    OverlayQuickControlBubble(
                        text = "停止",
                        onClick = { stopTask() }
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
        windowManager.addView(composeView, params)
        updateQuickControlPosition()
    }

    private fun updateQuickControlPosition() {
        val panel = panelView ?: return
        val panelLayoutParams = panelParams ?: return
        val quickView = quickControlView ?: return
        val quickParams = quickControlParams ?: return
        val panelWidth = panel.width.takeIf { it > 0 } ?: 172.dp
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

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()
}
