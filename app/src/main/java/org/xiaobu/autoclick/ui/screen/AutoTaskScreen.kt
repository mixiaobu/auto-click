package org.xiaobu.autoclick.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import org.xiaobu.autoclick.AutoClickApp
import org.xiaobu.autoclick.data.task.AutoTaskActionType
import org.xiaobu.autoclick.data.task.AutoTaskConfig
import org.xiaobu.autoclick.data.task.AutoTaskFailureStrategy
import org.xiaobu.autoclick.data.task.AutoTaskStep
import org.xiaobu.autoclick.data.task.AutoTaskTarget
import org.xiaobu.autoclick.data.task.AutoTaskTargetType
import org.xiaobu.autoclick.service.AutoClickAccessibilityService
import org.xiaobu.autoclick.service.AutoTaskCoordinatePickerService
import org.xiaobu.autoclick.service.AutoTaskOverlayService
import org.xiaobu.autoclick.ui.component.ActionStepCoordinateSlot
import org.xiaobu.autoclick.ui.component.ActionStepEditorDialog
import org.xiaobu.autoclick.ui.component.ActionStepEditorState
import org.xiaobu.autoclick.ui.component.ActionStepTargetPickerSlot
import org.xiaobu.autoclick.ui.component.validateActionStepEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoTaskScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as AutoClickApp }
    val store = app.autoTaskStore
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(false) }
    var accessibilityGranted by remember { mutableStateOf(false) }
    var draftTask by remember { mutableStateOf(store.getDraft()) }
    var savedTasks by remember { mutableStateOf(store.getTasks()) }
    var editorState by remember { mutableStateOf<ActionStepEditorState?>(null) }
    var pickingSlot by remember { mutableStateOf<ActionStepCoordinateSlot?>(null) }
    var imagePickerSlot by remember { mutableStateOf<ActionStepTargetPickerSlot?>(null) }
    var pendingDeleteTask by remember { mutableStateOf<AutoTaskConfig?>(null) }
    var exportingTask by remember { mutableStateOf<AutoTaskConfig?>(null) }
    var taskOverlayVisible by remember { mutableStateOf(AutoTaskOverlayService.isOverlayVisible()) }
    var taskRunning by remember { mutableStateOf(AutoTaskOverlayService.isTaskRunning()) }

    fun refreshState() {
        overlayGranted = Settings.canDrawOverlays(context)
        accessibilityGranted = AutoClickAccessibilityService.isServiceEnabled(context)
        savedTasks = store.getTasks()
        taskOverlayVisible = AutoTaskOverlayService.isOverlayVisible()
        taskRunning = AutoTaskOverlayService.isTaskRunning()
    }

    fun syncDraft(task: AutoTaskConfig) {
        draftTask = task
        store.saveDraft(task)
        savedTasks = store.getTasks()
        if (AutoTaskOverlayService.isOverlayVisible()) {
            AutoTaskOverlayService.refresh(context)
        }
    }

    fun showTaskOverlay() {
        if (!Settings.canDrawOverlays(context)) {
            AutoClickApp.showToast("请先开启悬浮窗权限")
            openOverlayPermission(context)
            return
        }
        if (!AutoClickAccessibilityService.isServiceEnabled(context)) {
            AutoClickApp.showToast("请先开启无障碍权限")
            openAccessibilitySettings(context)
            return
        }
        if (draftTask.steps.isEmpty()) {
            AutoClickApp.showToast("请先添加至少一个步骤")
            return
        }
        syncDraft(
            draftTask.copy(
                name = draftTask.name.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
        AutoTaskOverlayService.show(context, autoStart = false)
        AutoClickApp.showToast("自动点击器控制器已显示")
    }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val slot = imagePickerSlot
            imagePickerSlot = null
            if (uri == null || slot == null) return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val currentEditor = editorState ?: return@rememberLauncherForActivityResult
            editorState = when (slot) {
                ActionStepTargetPickerSlot.TARGET -> currentEditor.copy(
                    target = currentEditor.target.copy(
                        type = AutoTaskTargetType.IMAGE,
                        imageUri = uri.toString()
                    )
                )

                ActionStepTargetPickerSlot.SECONDARY -> currentEditor.copy(
                    secondaryTarget = currentEditor.secondaryTarget.copy(
                        type = AutoTaskTargetType.IMAGE,
                        imageUri = uri.toString()
                    )
                )
            }
        }

    val exportTaskLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val task = exportingTask
        exportingTask = null
        if (uri == null || task == null) return@rememberLauncherForActivityResult
        val success = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)
                ?.use { writer -> writer.write(store.encodeTask(task)) }
        }.isSuccess
        AutoClickApp.showToast(
            if (success) {
                if (task.hasImageStep()) "已导出自动点击器，图片识别步骤需重新选图" else "已导出自动点击器"
            } else {
                "导出自动点击器失败"
            }
        )
    }

    val importTaskLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val importedTask = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }?.let(store::decodeTask)
        }.getOrNull()
        if (importedTask == null) {
            AutoClickApp.showToast("导入失败，JSON 格式不正确")
            return@rememberLauncherForActivityResult
        }
        val savedTask = importedTask.copy(
            id = UUID.randomUUID().toString(),
            name = importedTask.name.trim(),
            updatedAt = System.currentTimeMillis()
        )
        store.saveTask(savedTask)
        savedTasks = store.getTasks()
        AutoClickApp.showToast(
            if (savedTask.hasImageStep()) {
                "已导入自动点击器，图片识别步骤需重新选图"
            } else {
                "已导入自动点击器"
            }
        )
    }

    LaunchedEffect(Unit) {
        refreshState()
        while (true) {
            AutoTaskCoordinatePickerService.consumePickedPoint()?.let { point ->
                val currentEditor = editorState
                val slot = pickingSlot
                if (currentEditor != null && slot != null) {
                    editorState = when (slot) {
                        ActionStepCoordinateSlot.TARGET -> currentEditor.copy(
                            target = currentEditor.target.copy(
                                type = AutoTaskTargetType.COORDINATE,
                                x = point.x,
                                y = point.y
                            )
                        )

                        ActionStepCoordinateSlot.SECONDARY -> currentEditor.copy(
                            secondaryTarget = currentEditor.secondaryTarget.copy(
                                type = AutoTaskTargetType.COORDINATE,
                                x = point.x,
                                y = point.y
                            )
                        )
                    }
                    pickingSlot = null
                }
            }
            taskOverlayVisible = AutoTaskOverlayService.isOverlayVisible()
            taskRunning = AutoTaskOverlayService.isTaskRunning()
            delay(250)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动点击器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AutoTaskRunSection(
                overlayGranted = overlayGranted,
                accessibilityGranted = accessibilityGranted,
                overlayVisible = taskOverlayVisible,
                running = taskRunning,
                stepCount = draftTask.steps.size,
                onStartStop = {
                    if (taskOverlayVisible || taskRunning) {
                        AutoTaskOverlayService.hide(context)
                    } else {
                        showTaskOverlay()
                    }
                }
            )
            AutoTaskDraftSection(
                draftTask = draftTask,
                onDraftChange = ::syncDraft,
                onCreateNew = {
                    syncDraft(AutoTaskConfig(updatedAt = System.currentTimeMillis()))
                },
                onSave = {
                    if (draftTask.steps.isEmpty()) {
                        AutoClickApp.showToast("请先添加至少一个步骤")
                        return@AutoTaskDraftSection
                    }
                    val savedTask = draftTask.copy(
                        name = draftTask.name.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                    syncDraft(savedTask)
                    store.saveTask(savedTask)
                    savedTasks = store.getTasks()
                    AutoClickApp.showToast("自动点击器已保存")
                }
            )
            AutoTaskStepList(
                steps = draftTask.steps,
                onAddStep = { editorState = ActionStepEditorState() },
                onEditStep = { step -> editorState = ActionStepEditorState.from(step) },
                onDeleteStep = { step ->
                    syncDraft(
                        draftTask.copy(
                            steps = draftTask.steps.filterNot { it.id == step.id },
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                },
                onMoveStep = moveStep@ { step, targetIndex ->
                    val currentIndex = draftTask.steps.indexOfFirst { it.id == step.id }
                    if (currentIndex == -1 || targetIndex !in draftTask.steps.indices || currentIndex == targetIndex) {
                        return@moveStep
                    }
                    val updatedSteps = draftTask.steps.toMutableList().apply {
                        add(targetIndex, removeAt(currentIndex))
                    }
                    syncDraft(draftTask.copy(steps = updatedSteps, updatedAt = System.currentTimeMillis()))
                }
            )
            SavedAutoTaskSection(
                tasks = savedTasks,
                onImport = { importTaskLauncher.launch(arrayOf("application/json", "*/*")) },
                onApply = { task ->
                    syncDraft(task)
                    AutoClickApp.showToast("已加载到当前自动点击器")
                },
                onExport = { task ->
                    exportingTask = task
                    exportTaskLauncher.launch(buildAutoTaskFileName(task))
                },
                onDelete = { task -> pendingDeleteTask = task }
            )
        }
    }

    editorState?.let { state ->
        ActionStepEditorDialog(
            state = state,
            onDismiss = {
                editorState = null
                pickingSlot = null
                imagePickerSlot = null
            },
            onStateChange = { editorState = it },
            onPickCoordinate = { slot, currentTarget ->
                if (!Settings.canDrawOverlays(context)) {
                    AutoClickApp.showToast("请先开启悬浮窗权限")
                    openOverlayPermission(context)
                    return@ActionStepEditorDialog
                }
                pickingSlot = slot
                AutoTaskCoordinatePickerService.show(
                    context = context,
                    x = currentTarget.x.takeIf { it > 0 },
                    y = currentTarget.y.takeIf { it > 0 }
                )
                AutoClickApp.showToast("选点悬浮窗已启动，先点开始选择，再去目标页面单击坐标")
            },
            onPickImage = { slot ->
                imagePickerSlot = slot
                imagePickerLauncher.launch(arrayOf("image/*"))
            },
            onConfirm = {
                val current = editorState ?: return@ActionStepEditorDialog
                val error = validateActionStepEditor(current)
                if (error != null) {
                    AutoClickApp.showToast(error)
                    return@ActionStepEditorDialog
                }
                val step = current.toStep()
                val updatedSteps = if (draftTask.steps.any { it.id == step.id }) {
                    draftTask.steps.map { if (it.id == step.id) step else it }
                } else {
                    draftTask.steps + step
                }
                syncDraft(draftTask.copy(steps = updatedSteps, updatedAt = System.currentTimeMillis()))
                editorState = null
            }
        )
    }

    pendingDeleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTask = null },
            title = { Text("删除自动点击器") },
            text = { Text("确定要删除“${task.name}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.deleteTask(task.id)
                        savedTasks = store.getTasks()
                        pendingDeleteTask = null
                        AutoClickApp.showToast("自动点击器已删除")
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTask = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun AutoTaskRunSection(
    overlayGranted: Boolean,
    accessibilityGranted: Boolean,
    overlayVisible: Boolean,
    running: Boolean,
    stepCount: Int,
    onStartStop: () -> Unit
) {
    SectionSurface {
        Text(
            text = "运行状态",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Button(
            onClick = onStartStop,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (overlayVisible || running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (overlayVisible || running) "停止自动点击器" else "启动自动点击器")
        }
        StatusChip(
            label = "当前状态",
            value = when {
                running -> "执行中 · $stepCount 个步骤"
                overlayVisible -> "启动中 · $stepCount 个步骤"
                !overlayGranted || !accessibilityGranted -> "权限未完整开启 · $stepCount 个步骤"
                else -> "未启动 · $stepCount 个步骤"
            },
            highlighted = overlayVisible || running,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatusChip(
    label: String,
    value: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AutoTaskDraftSection(
    draftTask: AutoTaskConfig,
    onDraftChange: (AutoTaskConfig) -> Unit,
    onCreateNew: () -> Unit,
    onSave: () -> Unit
) {
    SectionSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "当前自动点击器",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onCreateNew) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("新建")
            }
        }
        OutlinedTextField(
            value = draftTask.name,
            onValueChange = { onDraftChange(draftTask.copy(name = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("名称") },
            singleLine = true
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "重复执行",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "开启后，整套步骤执行完会自动重新执行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draftTask.repeatEnabled,
                    onCheckedChange = { enabled ->
                        onDraftChange(
                            draftTask.copy(
                                repeatEnabled = enabled,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("保存自动点击器")
            }
        }
    }
}

@Composable
private fun AutoTaskStepList(
    steps: List<AutoTaskStep>,
    onAddStep: () -> Unit,
    onEditStep: (AutoTaskStep) -> Unit,
    onDeleteStep: (AutoTaskStep) -> Unit,
    onMoveStep: (AutoTaskStep, Int) -> Unit
) {
    SectionSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "执行步骤",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onAddStep) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("添加")
            }
        }
        if (steps.isEmpty()) {
            Text(
                text = "还没有步骤。添加步骤后，自动点击器会按这里的顺序执行。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "长按步骤卡片可拖动排序",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { index, step ->
                    key(step.id) {
                        AutoTaskStepRow(
                            index = index,
                            step = step,
                            lastIndex = steps.lastIndex,
                            onEdit = { onEditStep(step) },
                            onDelete = { onDeleteStep(step) },
                            onMoveStep = onMoveStep
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoTaskStepRow(
    index: Int,
    step: AutoTaskStep,
    lastIndex: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveStep: (AutoTaskStep, Int) -> Unit
) {
    var rowHeightPx by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val itemDistancePx = with(density) { rowHeightPx.toFloat() + 8.dp.toPx() }.coerceAtLeast(1f)
    val currentIndex by rememberUpdatedState(index)
    val currentStep by rememberUpdatedState(step)
    val currentLastIndex by rememberUpdatedState(lastIndex)
    val currentOnMoveStep by rememberUpdatedState(onMoveStep)
    val canReorder = lastIndex > 0
    val dragModifier = if (canReorder) {
        Modifier.pointerInput(step.id, itemDistancePx) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    dragging = true
                    dragOffsetY = 0f
                },
                onDragEnd = {
                    dragging = false
                    dragOffsetY = 0f
                },
                onDragCancel = {
                    dragging = false
                    dragOffsetY = 0f
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetY += dragAmount.y
                    val threshold = itemDistancePx / 2f
                    when {
                        dragOffsetY <= -threshold && currentIndex > 0 -> {
                            currentOnMoveStep(currentStep, currentIndex - 1)
                            dragOffsetY += itemDistancePx
                        }

                        dragOffsetY >= threshold && currentIndex < currentLastIndex -> {
                            currentOnMoveStep(currentStep, currentIndex + 1)
                            dragOffsetY -= itemDistancePx
                        }
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Surface(
        color = if (dragging) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (dragging) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowHeightPx = it.height }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (dragging) dragOffsetY else 0f
                scaleX = if (dragging) 1.01f else 1f
                scaleY = if (dragging) 1.01f else 1f
            }
            .then(dragModifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = step.title.ifBlank { step.actionType.title },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = buildStepSummary(step),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SavedAutoTaskSection(
    tasks: List<AutoTaskConfig>,
    onImport: () -> Unit,
    onApply: (AutoTaskConfig) -> Unit,
    onExport: (AutoTaskConfig) -> Unit,
    onDelete: (AutoTaskConfig) -> Unit
) {
    SectionSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已保存自动点击器",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onImport) {
                Text("导入")
            }
        }
        if (tasks.isEmpty()) {
            Text(
                text = "保存当前配置后，这里会显示可复用的自动点击器。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tasks.forEachIndexed { index, task ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = task.name.ifBlank { "自动点击器 ${index + 1}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = formatTime(task.updatedAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(task) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetaTag(text = "步骤 ${task.steps.size} 个")
                                MetaTag(text = if (task.repeatEnabled) "重复执行" else "执行一次")
                                if (task.hasImageStep()) {
                                    MetaTag(text = "包含图片识别")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onExport(task) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("导出")
                                }
                                Button(
                                    onClick = { onApply(task) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("加载")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaTag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SectionSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

private fun buildStepSummary(step: AutoTaskStep): String {
    val actionText = when (step.actionType) {
        AutoTaskActionType.WAIT -> "等待 ${step.durationMs}ms"
        AutoTaskActionType.WAIT_FOR_TARGET -> "等待出现 ${buildTargetSummary(step.target)}，最多 ${step.durationMs}ms"
        AutoTaskActionType.WAIT_FOR_TARGET_DISAPPEAR -> "等待消失 ${buildTargetSummary(step.target)}，最多 ${step.durationMs}ms"
        AutoTaskActionType.TAP -> "单击 ${buildTargetSummary(step.target)}"
        AutoTaskActionType.DOUBLE_TAP -> "双击 ${buildTargetSummary(step.target)}"
        AutoTaskActionType.LONG_PRESS -> "长按 ${buildTargetSummary(step.target)}"
        AutoTaskActionType.SWIPE -> "从 ${buildTargetSummary(step.target)} 滑动到 ${
            buildTargetSummary(step.secondaryTarget)
        }"
        AutoTaskActionType.SWIPE_UP -> "上滑"
        AutoTaskActionType.SWIPE_DOWN -> "下滑"
        AutoTaskActionType.SWIPE_LEFT -> "左滑"
        AutoTaskActionType.SWIPE_RIGHT -> "右滑"
        AutoTaskActionType.OPEN_APP -> "打开 ${step.appLabel.ifBlank { "应用" }}"
        AutoTaskActionType.BACK -> "执行返回"
        AutoTaskActionType.HOME -> "返回主页"
        AutoTaskActionType.RECENTS -> "打开最近任务"
        AutoTaskActionType.NOTIFICATIONS -> "打开通知栏"
        AutoTaskActionType.QUICK_SETTINGS -> "打开快捷设置"
        AutoTaskActionType.LOCK_SCREEN -> "锁屏"
    }
    val delayText = if (step.delayAfterMs > 0L) {
        "$actionText · 延迟 ${step.delayAfterMs}ms"
    } else {
        actionText
    }
    return when (step.failureStrategy) {
        AutoTaskFailureStrategy.CONTINUE -> "$delayText · 失败继续"
        AutoTaskFailureStrategy.RETRY -> "$delayText · 失败重试 ${step.failureRetryCount} 次"
        AutoTaskFailureStrategy.STOP -> delayText
    }
}

private fun buildTargetSummary(target: AutoTaskTarget?): String {
    if (target == null) return "未设置"
    return when (target.type) {
        AutoTaskTargetType.COORDINATE -> "坐标(${target.x}, ${target.y})"
        AutoTaskTargetType.NODE_TEXT -> "文字“${target.text}”第${target.index}个"
        AutoTaskTargetType.OCR_TEXT -> "OCR“${target.text}”第${target.index}个"
        AutoTaskTargetType.IMAGE -> {
            val imageName = runCatching { Uri.parse(target.imageUri).lastPathSegment }.getOrNull()
            imageName?.takeIf { it.isNotBlank() } ?: "图片识别"
        }
    }
}

private fun AutoTaskConfig.hasImageStep(): Boolean {
    return steps.any { step ->
        step.target?.type == AutoTaskTargetType.IMAGE ||
            step.secondaryTarget?.type == AutoTaskTargetType.IMAGE
    }
}

private fun formatTime(updatedAt: Long): String {
    if (updatedAt <= 0L) return "刚刚"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(updatedAt))
}

private fun buildAutoTaskFileName(task: AutoTaskConfig): String {
    val safeName = task.name
        .ifBlank { "auto_task" }
        .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
        .trim('_')
        .ifBlank { "auto_task" }
    return "$safeName.json"
}
