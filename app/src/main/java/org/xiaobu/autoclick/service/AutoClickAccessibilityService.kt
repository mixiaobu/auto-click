package org.xiaobu.autoclick.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xiaobu.autoclick.AutoClickApp
import org.xiaobu.autoclick.data.task.AutoTaskActionType
import org.xiaobu.autoclick.data.task.AutoTaskStep
import org.xiaobu.autoclick.data.task.AutoTaskTarget
import org.xiaobu.autoclick.data.task.AutoTaskTargetType
import org.xiaobu.autoclick.data.trigger.AutoTriggerConfig
import org.xiaobu.autoclick.data.trigger.AutoTriggerEventType

@SuppressLint("AccessibilityPolicy")
class AutoClickAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val triggerExecutionJobs = mutableMapOf<String, Job>()
    private val triggerLastRunAt = mutableMapOf<String, Long>()
    private val triggerExecutionMutex = Mutex()
    private var keepAliveView: View? = null
    private var keepAliveWindowManager: WindowManager? = null

    companion object {
        private const val TAG = "AutoClickA11y"
        private const val DEFAULT_TAP_DURATION_MS = 20L
        private const val DEFAULT_LONG_PRESS_DURATION_MS = 500L
        private const val DEFAULT_DOUBLE_TAP_INTERVAL_MS = 100L
        private const val DEFAULT_SWIPE_DURATION_MS = 500L

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

        suspend fun dispatchTapAwait(
            x: Int,
            y: Int,
            durationMs: Long = DEFAULT_TAP_DURATION_MS
        ): Boolean {
            return currentService?.tapAwait(Point(x, y), durationMs) == true
        }

        suspend fun dispatchDoubleTap(
            x: Int,
            y: Int,
            durationMs: Long = DEFAULT_TAP_DURATION_MS,
            intervalMs: Long = DEFAULT_DOUBLE_TAP_INTERVAL_MS
        ): Boolean {
            return currentService?.doubleTapAwait(Point(x, y), durationMs, intervalMs) == true
        }

        suspend fun dispatchLongPress(
            x: Int,
            y: Int,
            durationMs: Long = DEFAULT_LONG_PRESS_DURATION_MS
        ): Boolean {
            return currentService?.longPressAwait(Point(x, y), durationMs) == true
        }

        suspend fun dispatchSwipe(
            fromX: Int,
            fromY: Int,
            toX: Int,
            toY: Int,
            durationMs: Long = DEFAULT_SWIPE_DURATION_MS
        ): Boolean {
            return currentService?.swipeAwait(
                from = Point(fromX, fromY),
                to = Point(toX, toY),
                durationMs = durationMs
            ) == true
        }

        fun performBack(): Boolean = currentService?.performGlobalAction(GLOBAL_ACTION_BACK) == true

        fun performHome(): Boolean = currentService?.performGlobalAction(GLOBAL_ACTION_HOME) == true

        fun performRecents(): Boolean = currentService?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true

        fun openNotifications(): Boolean =
            currentService?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true

        fun openQuickSettings(): Boolean =
            currentService?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) == true

        fun lockScreen(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                currentService?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
            } else {
                false
            }
        }

        fun findNodeBoundsByText(
            text: String,
            index: Int = 0,
            exact: Boolean = false
        ): Rect? {
            return currentService?.findBoundsByNodeText(text, index, exact)
        }

        fun findNodeCenterByText(
            text: String,
            index: Int = 0,
            exact: Boolean = false
        ): Point? {
            return findNodeBoundsByText(text, index, exact)?.centerPoint()
        }

        suspend fun executeTaskStep(step: AutoTaskStep): Boolean {
            return currentService?.executeStep(step) == true
        }

        private fun Rect.centerPoint(): Point {
            return Point(centerX(), centerY())
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentService = this
        ensureKeepAliveOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        handleTriggerEvent(event)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        serviceScope.cancel()
        removeKeepAliveOverlay()
        if (currentService === this) {
            currentService = null
        }
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        removeKeepAliveOverlay()
        if (currentService === this) {
            currentService = null
        }
        return super.onUnbind(intent)
    }

    private fun ensureKeepAliveOverlay() {
        if (keepAliveView != null) return
        val windowManager = keepAliveWindowManager
            ?: (getSystemService(WINDOW_SERVICE) as WindowManager).also {
                keepAliveWindowManager = it
            }
        val view = View(this).apply {
            alpha = 0f
            isClickable = false
            isFocusable = false
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            alpha = 0f
        }
        runCatching {
            windowManager.addView(view, params)
            keepAliveView = view
        }.onFailure { error ->
            Log.w(TAG, "ensureKeepAliveOverlay failed", error)
        }
    }

    private fun removeKeepAliveOverlay() {
        val windowManager = keepAliveWindowManager ?: return
        val view = keepAliveView ?: return
        runCatching { windowManager.removeView(view) }
        keepAliveView = null
    }

    private fun handleTriggerEvent(event: AccessibilityEvent) {
        val triggerEventType = AutoTriggerEventType.fromAccessibilityEvent(event.eventType) ?: return
        val sourcePackageName = event.packageName?.toString().orEmpty()
        if (sourcePackageName.isBlank() || sourcePackageName == packageName) return

        val triggers = (application as? AutoClickApp)
            ?.autoTriggerStore
            ?.getTriggers()
            .orEmpty()
            .filter { config ->
                shouldExecuteTrigger(
                    trigger = config,
                    eventType = triggerEventType,
                    packageName = sourcePackageName,
                    event = event
                )
            }
        if (triggers.isEmpty()) return

        triggers.forEach(::scheduleTriggerExecution)
    }

    private fun scheduleTriggerExecution(trigger: AutoTriggerConfig) {
        triggerLastRunAt[trigger.id] = System.currentTimeMillis()
        val executionJob = serviceScope.launch {
            try {
                val success = triggerExecutionMutex.withLock {
                    executeTriggerSteps(trigger)
                }
                if (!success) return@launch
                AutoClickApp.showToast("已触发 ${trigger.name.ifBlank { "触发器" }}")
            } catch (_: CancellationException) {
                Log.d(TAG, "trigger canceled: ${trigger.name}")
            }
        }
        triggerExecutionJobs[trigger.id] = executionJob
        executionJob.invokeOnCompletion {
            if (triggerExecutionJobs[trigger.id] === executionJob) {
                triggerExecutionJobs.remove(trigger.id)
            }
        }
    }

    private suspend fun executeTriggerSteps(trigger: AutoTriggerConfig): Boolean {
        for (step in trigger.steps) {
            val success = executeStep(step)
            if (!success) {
                Log.w(TAG, "trigger step failed: trigger=${trigger.name} step=${step.title}")
                return false
            }
            if (step.delayAfterMs > 0L) {
                delay(step.delayAfterMs)
            }
        }
        return true
    }

    private fun shouldExecuteTrigger(
        trigger: AutoTriggerConfig,
        eventType: AutoTriggerEventType,
        packageName: String,
        event: AccessibilityEvent
    ): Boolean {
        if (!trigger.enabled) return false
        if (trigger.effectiveEventTypes.none { it == eventType }) return false
        if (trigger.steps.isEmpty()) return false

        val targetPackages = trigger.targetApps
            .map { it.packageName.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                listOf(trigger.packageName.trim()).filter { it.isNotBlank() }
            }
        if (targetPackages.isEmpty()) return false
        if (targetPackages.none { packageName.equals(it, ignoreCase = true) }) return false

        val keyword = trigger.pageKeyword.trim()
        if (keyword.isNotBlank() && !matchesTriggerKeyword(event, keyword, trigger.keywordExact)) {
            return false
        }

        val now = System.currentTimeMillis()
        val lastRunAt = triggerLastRunAt[trigger.id] ?: 0L
        val isExecuting = triggerExecutionJobs[trigger.id]?.isActive == true
        if (isExecuting) return false
        if (now - lastRunAt < trigger.cooldownMs.coerceAtLeast(0L)) return false
        return true
    }

    private fun matchesTriggerKeyword(
        event: AccessibilityEvent,
        keyword: String,
        exact: Boolean
    ): Boolean {
        val candidates = buildList {
            add(event.className?.toString().orEmpty())
            add(event.contentDescription?.toString().orEmpty())
            event.text.orEmpty().forEach { add(it.toString()) }
        }.filter { it.isNotBlank() }
        if (candidates.any { matchesText(it, keyword, exact) }) return true
        return findBoundsByNodeText(keyword, index = 0, exact = exact) != null
    }

    private suspend fun executeStep(step: AutoTaskStep): Boolean {
        val success = when (step.actionType) {
            AutoTaskActionType.WAIT -> {
                delay(step.durationMs.coerceAtLeast(20L))
                true
            }

            AutoTaskActionType.TAP -> {
                val point = resolveTargetCenter(step.target) ?: return false
                tapAwait(point, step.durationMs)
            }

            AutoTaskActionType.DOUBLE_TAP -> {
                val point = resolveTargetCenter(step.target) ?: return false
                doubleTapAwait(point, durationMs = step.durationMs)
            }

            AutoTaskActionType.LONG_PRESS -> {
                val point = resolveTargetCenter(step.target) ?: return false
                longPressAwait(point, durationMs = step.durationMs.coerceAtLeast(300L))
            }

            AutoTaskActionType.SWIPE -> {
                val from = resolveTargetCenter(step.target) ?: return false
                val to = resolveTargetCenter(step.secondaryTarget) ?: return false
                swipeAwait(from, to, durationMs = step.durationMs.coerceAtLeast(100L))
            }

            AutoTaskActionType.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            AutoTaskActionType.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            AutoTaskActionType.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            AutoTaskActionType.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            AutoTaskActionType.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            AutoTaskActionType.LOCK_SCREEN -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    false
                }
            }
        }
        if (!success) {
            Log.w(TAG, "execute step failed: ${step.actionType} ${step.title}")
        }
        return success
    }

    private suspend fun resolveTargetCenter(target: AutoTaskTarget?): Point? {
        if (target == null) return null
        return when (target.type) {
            AutoTaskTargetType.COORDINATE -> Point(target.x, target.y)
            AutoTaskTargetType.NODE_TEXT -> findBoundsByNodeTextWithRetry(
                text = target.text,
                index = target.index - 1,
                exact = target.exact
            )?.let { Point(it.centerX(), it.centerY()) }

            AutoTaskTargetType.OCR_TEXT -> findBoundsByOcr(
                text = target.text,
                index = target.index - 1,
                exact = target.exact
            )?.let { Point(it.centerX(), it.centerY()) }

            AutoTaskTargetType.IMAGE -> {
                val bitmap = loadTargetBitmap(target.imageUri) ?: return null
                findBoundsByImage(bitmap)?.let { Point(it.centerX(), it.centerY()) }
            }
        }
    }

    private fun tap(point: Point, durationMs: Long): Boolean {
        return dispatchGesture(
            buildTapGesture(point, durationMs, startTimeMs = 0L),
            null,
            null
        )
    }

    private suspend fun tapAwait(
        point: Point,
        durationMs: Long,
        startTimeMs: Long = 0L
    ): Boolean {
        return dispatchGestureAwait(buildTapGesture(point, durationMs, startTimeMs))
    }

    private suspend fun doubleTapAwait(
        point: Point,
        durationMs: Long,
        intervalMs: Long = DEFAULT_DOUBLE_TAP_INTERVAL_MS
    ): Boolean {
        val firstTap = tapAwait(point, durationMs)
        if (!firstTap) return false
        delay(intervalMs.coerceAtLeast(40L))
        return tapAwait(point, durationMs)
    }

    private suspend fun longPressAwait(point: Point, durationMs: Long): Boolean {
        return tapAwait(point, durationMs.coerceAtLeast(DEFAULT_LONG_PRESS_DURATION_MS))
    }

    private suspend fun swipeAwait(
        from: Point,
        to: Point,
        durationMs: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(from.x.toFloat(), from.y.toFloat())
            lineTo(to.x.toFloat(), to.y.toFloat())
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
        return dispatchGestureAwait(gesture)
    }

    private fun buildTapGesture(
        point: Point,
        durationMs: Long,
        startTimeMs: Long
    ): GestureDescription {
        val path = Path().apply {
            moveTo(point.x.toFloat(), point.y.toFloat())
        }
        return GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    startTimeMs.coerceAtLeast(0L),
                    durationMs.coerceAtLeast(1L)
                )
            )
            .build()
    }

    private suspend fun dispatchGestureAwait(gesture: GestureDescription): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val started = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
                null
            )
            if (!started && continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    private suspend fun findBoundsByNodeTextWithRetry(
        text: String,
        index: Int,
        exact: Boolean
    ): Rect? {
        repeat(3) { attempt ->
            findBoundsByNodeText(text = text, index = index, exact = exact)?.let { return it }
            if (attempt < 2) delay(160L)
        }
        return null
    }

    private fun findBoundsByNodeText(
        text: String,
        index: Int = 0,
        exact: Boolean = false
    ): Rect? {
        val keyword = text.trim()
        if (keyword.isBlank()) return null
        val matches = collectNodeMatches(keyword, exact)
        return matches
            .getOrNull(index.coerceAtLeast(0))
            ?.let { node ->
                Rect().also(node::getBoundsInScreen)
            }
    }

    private suspend fun findBoundsByOcr(
        text: String,
        index: Int = 0,
        exact: Boolean = false
    ): Rect? {
        val keyword = text.trim()
        if (keyword.isBlank()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "OCR requires Android 11 or above")
            return null
        }
        val screenshot = takeScreenshotBitmap() ?: return null
        val recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(screenshot, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val matchedRects = buildList {
                        result.textBlocks.forEach { block ->
                            if (matchesText(block.text.orEmpty(), keyword, exact)) {
                                block.boundingBox?.let(::add)
                            }
                            block.lines.forEach { line ->
                                if (matchesText(line.text.orEmpty(), keyword, exact)) {
                                    line.boundingBox?.let(::add)
                                }
                                line.elements.forEach { element ->
                                    if (matchesText(element.text.orEmpty(), keyword, exact)) {
                                        element.boundingBox?.let(::add)
                                    }
                                }
                            }
                        }
                    }.distinctBy { it.rectKey() }
                    if (continuation.isActive) {
                        continuation.resume(matchedRects.getOrNull(index.coerceAtLeast(0)))
                    }
                    recognizer.close()
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "OCR failed: text=$keyword", error)
                    if (continuation.isActive) continuation.resume(null)
                    recognizer.close()
                }
                .addOnCanceledListener {
                    if (continuation.isActive) continuation.resume(null)
                    recognizer.close()
                }
        }
    }

    private suspend fun findBoundsByImage(
        targetBitmap: Bitmap,
        threshold: Float = 0.90f,
        sampleStep: Int = 4,
        searchStep: Int = 4
    ): Rect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val screenshot = takeScreenshotBitmap() ?: return null
        return withContext(Dispatchers.Default) {
            findTemplateBounds(
                screenBitmap = screenshot,
                targetBitmap = targetBitmap,
                threshold = threshold,
                sampleStep = sampleStep,
                searchStep = searchStep
            )
        }
    }

    private suspend fun loadTargetBitmap(imageUri: String): Bitmap? {
        if (imageUri.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                when {
                    imageUri.startsWith("content://") || imageUri.startsWith("file://") -> {
                        contentResolver.openInputStream(Uri.parse(imageUri))?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    }

                    imageUri.startsWith("http://") || imageUri.startsWith("https://") -> {
                        URL(imageUri).openStream().use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    }

                    else -> BitmapFactory.decodeFile(imageUri)
                }
            }.getOrNull()
        }
    }

    private suspend fun takeScreenshotBitmap(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { continuation ->
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            val bitmap = runCatching {
                                result.hardwareBuffer.use { hardwareBuffer ->
                                    Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                                        ?.copy(Bitmap.Config.ARGB_8888, false)
                                }
                            }.getOrNull()
                            if (continuation.isActive) continuation.resume(bitmap)
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "takeScreenshot failed: errorCode=$errorCode")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                )
            } catch (error: Exception) {
                Log.e(TAG, "takeScreenshot exception", error)
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun collectNodeMatches(
        keyword: String,
        exact: Boolean
    ): List<AccessibilityNodeInfo> {
        val roots = buildList {
            rootInActiveWindow?.let(::add)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                windows.orEmpty()
                    .mapNotNull { it.root }
                    .forEach { root ->
                        if (none { it == root }) add(root)
                    }
            }
        }
        if (roots.isEmpty()) return emptyList()

        val nodes = mutableListOf<AccessibilityNodeInfo>()
        roots.forEach { root ->
            runCatching { root.findAccessibilityNodeInfosByText(keyword) }
                .onSuccess { found ->
                    found.orEmpty()
                        .filter { node ->
                            matchesText(node.text?.toString().orEmpty(), keyword, exact) ||
                                matchesText(node.contentDescription?.toString().orEmpty(), keyword, exact)
                        }
                        .forEach(nodes::add)
                }
            traverseNodesByText(root, keyword, exact, nodes)
        }
        return nodes
            .filter { node ->
                Rect().also(node::getBoundsInScreen).let { it.width() > 0 && it.height() > 0 }
            }
            .distinctBy { node ->
                val rect = Rect().also(node::getBoundsInScreen)
                "${node.windowId}:${node.viewIdResourceName.orEmpty()}:${node.text?.toString().orEmpty()}:${rect.left},${rect.top},${rect.right},${rect.bottom}"
            }
    }

    private fun traverseNodesByText(
        node: AccessibilityNodeInfo,
        keyword: String,
        exact: Boolean,
        matches: MutableList<AccessibilityNodeInfo>
    ) {
        val nodeText = node.text?.toString().orEmpty()
        val contentDescription = node.contentDescription?.toString().orEmpty()
        if (
            matchesText(nodeText, keyword, exact) ||
            matchesText(contentDescription, keyword, exact)
        ) {
            matches.add(node)
        }
        repeat(node.childCount) { index ->
            node.getChild(index)?.let { child ->
                traverseNodesByText(child, keyword, exact, matches)
            }
        }
    }

    private fun matchesText(
        source: String,
        target: String,
        exact: Boolean
    ): Boolean {
        if (source.isBlank() || target.isBlank()) return false
        return if (exact) {
            source == target
        } else {
            source.contains(target, ignoreCase = true)
        }
    }

    private fun Rect.rectKey(): String {
        return "$left,$top,$right,$bottom"
    }

    private fun findTemplateBounds(
        screenBitmap: Bitmap,
        targetBitmap: Bitmap,
        threshold: Float,
        sampleStep: Int,
        searchStep: Int
    ): Rect? {
        val screenWidth = screenBitmap.width
        val screenHeight = screenBitmap.height
        val targetWidth = targetBitmap.width
        val targetHeight = targetBitmap.height
        if (targetWidth <= 0 || targetHeight <= 0) return null
        if (targetWidth > screenWidth || targetHeight > screenHeight) return null

        val safeSampleStep = sampleStep.coerceAtLeast(1)
        val safeSearchStep = searchStep.coerceAtLeast(1)
        val targetPixels = IntArray(targetWidth * targetHeight)
        val screenPixels = IntArray(screenWidth * screenHeight)
        targetBitmap.getPixels(targetPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        screenBitmap.getPixels(screenPixels, 0, screenWidth, 0, 0, screenWidth, screenHeight)

        var bestScore = Float.MIN_VALUE
        var bestX = -1
        var bestY = -1
        var y = 0
        while (y <= screenHeight - targetHeight) {
            var x = 0
            while (x <= screenWidth - targetWidth) {
                var sampleCount = 0
                var totalSimilarity = 0f
                var stop = false
                var targetY = 0
                while (targetY < targetHeight && !stop) {
                    var targetX = 0
                    while (targetX < targetWidth) {
                        val targetColor = targetPixels[targetY * targetWidth + targetX]
                        val targetAlpha = targetColor ushr 24 and 0xFF
                        if (targetAlpha >= 16) {
                            val screenColor = screenPixels[(y + targetY) * screenWidth + (x + targetX)]
                            val similarity = colorSimilarity(targetColor, screenColor)
                            totalSimilarity += similarity
                            sampleCount++
                            if (sampleCount > 0) {
                                val currentAverage = totalSimilarity / sampleCount
                                if (currentAverage + 0.08f < threshold) {
                                    stop = true
                                    break
                                }
                            }
                        }
                        targetX += safeSampleStep
                    }
                    targetY += safeSampleStep
                }
                if (sampleCount > 0 && !stop) {
                    val average = totalSimilarity / sampleCount
                    if (average > bestScore) {
                        bestScore = average
                        bestX = x
                        bestY = y
                    }
                }
                x += safeSearchStep
            }
            y += safeSearchStep
        }

        return if (bestScore >= threshold && bestX >= 0 && bestY >= 0) {
            Rect(bestX, bestY, bestX + targetWidth, bestY + targetHeight)
        } else {
            null
        }
    }

    private fun colorSimilarity(colorA: Int, colorB: Int): Float {
        val redDiff = abs((colorA shr 16 and 0xFF) - (colorB shr 16 and 0xFF))
        val greenDiff = abs((colorA shr 8 and 0xFF) - (colorB shr 8 and 0xFF))
        val blueDiff = abs((colorA and 0xFF) - (colorB and 0xFF))
        return 1f - ((redDiff + greenDiff + blueDiff) / (255f * 3f))
    }
}
