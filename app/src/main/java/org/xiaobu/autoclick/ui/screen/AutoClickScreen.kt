package org.xiaobu.autoclick.ui.screen

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.delay
import org.xiaobu.autoclick.AutoClickApp
import org.xiaobu.autoclick.data.click.AutoClickConfig
import org.xiaobu.autoclick.data.click.AutoClickPointConfig
import org.xiaobu.autoclick.data.click.AutoClickPresetConfig
import org.xiaobu.autoclick.service.AutoClickAccessibilityService
import org.xiaobu.autoclick.service.AutoClickOverlayService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoClickScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as AutoClickApp }
    val store = app.autoClickStore
    val lifecycleOwner = LocalLifecycleOwner.current
    var intervalText by rememberSaveable { mutableStateOf(store.getConfig().intervalMillis.toString()) }
    var durationText by rememberSaveable { mutableStateOf(store.getConfig().durationSeconds.toString()) }
    var maxClickCountText by rememberSaveable { mutableStateOf(store.getConfig().maxClickCount.toString()) }
    var presetNameText by rememberSaveable { mutableStateOf("") }
    var points by remember { mutableStateOf(emptyList<AutoClickPointConfig>()) }
    var presets by remember { mutableStateOf(emptyList<AutoClickPresetConfig>()) }
    var overlayVisible by remember { mutableStateOf(false) }
    var clicking by remember { mutableStateOf(false) }

    fun refreshStatus(syncInputs: Boolean = false) {
        val config = store.getConfig()
        if (syncInputs) {
            intervalText = config.intervalMillis.toString()
            durationText = config.durationSeconds.toString()
            maxClickCountText = config.maxClickCount.toString()
        }
        points = config.points
        presets = store.getPresets()
        overlayVisible = AutoClickOverlayService.isOverlayVisible()
        clicking = AutoClickOverlayService.isClicking()
    }

    fun saveConfig(saveAsPreset: Boolean, showToast: Boolean): AutoClickConfig {
        if (saveAsPreset && AutoClickOverlayService.isClicking()) {
            AutoClickApp.showToast("请先停止连点再保存配置")
            refreshStatus()
            return store.getConfig()
        }
        val intervalMillis = intervalText.toIntOrNull()?.coerceIn(50, 60_000) ?: 100
        val durationSeconds = durationText.toIntOrNull()?.coerceIn(0, 24 * 60 * 60) ?: 0
        val maxClickCount = maxClickCountText.toIntOrNull()?.coerceIn(0, 1_000_000) ?: 0
        val config = store.getConfig().copy(
            intervalMillis = intervalMillis,
            durationSeconds = durationSeconds,
            maxClickCount = maxClickCount
        )
        store.saveConfig(config)
        if (saveAsPreset) {
            store.savePreset(config, presetNameText)
        }
        intervalText = intervalMillis.toString()
        durationText = durationSeconds.toString()
        maxClickCountText = maxClickCount.toString()
        if (showToast) {
            AutoClickApp.showToast(if (saveAsPreset) "配置已保存" else "参数已保存")
        }
        refreshStatus()
        return config
    }

    fun applyPreset(preset: AutoClickPresetConfig) {
        if (AutoClickOverlayService.isClicking()) {
            AutoClickApp.showToast("请先停止连点再加载配置")
            refreshStatus()
            return
        }
        if (!Settings.canDrawOverlays(context) || !AutoClickAccessibilityService.isServiceEnabled(context)) {
            AutoClickApp.showToast("请先完成悬浮窗和无障碍授权")
            refreshStatus()
            return
        }
        store.saveConfig(preset.config)
        presetNameText = preset.name
        intervalText = preset.config.intervalMillis.toString()
        durationText = preset.config.durationSeconds.toString()
        maxClickCountText = preset.config.maxClickCount.toString()
        if (overlayVisible) {
            AutoClickOverlayService.refresh(context)
        }
        refreshStatus(syncInputs = true)
        AutoClickApp.showToast("已加载配置")
    }

    LaunchedEffect(Unit) {
        refreshStatus(syncInputs = true)
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshStatus()
            delay(1000)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus(syncInputs = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连点器") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        AutoClickContent(
            contentPadding = innerPadding,
            overlayVisible = overlayVisible,
            clicking = clicking,
            intervalText = intervalText,
            durationText = durationText,
            maxClickCountText = maxClickCountText,
            presetNameText = presetNameText,
            points = points,
            presets = presets,
            onIntervalChange = { intervalText = it.filter(Char::isDigit) },
            onDurationChange = { durationText = it.filter(Char::isDigit) },
            onMaxClickCountChange = { maxClickCountText = it.filter(Char::isDigit) },
            onPresetNameChange = { presetNameText = it },
            onCreateNew = {
                if (AutoClickOverlayService.isClicking()) {
                    AutoClickApp.showToast("请先停止连点再新建配置")
                    refreshStatus()
                } else {
                    store.saveConfig(AutoClickConfig())
                    presetNameText = ""
                    refreshStatus(syncInputs = true)
                }
            },
            onSave = { saveConfig(saveAsPreset = true, showToast = true) },
            onApplyPreset = ::applyPreset,
            onImportPreset = { preset ->
                store.savePreset(preset.config, preset.name)
                refreshStatus()
                AutoClickApp.showToast("已导入配置")
            },
            onExportPreset = { preset -> store.encodePreset(preset) },
            onDecodePreset = { rawJson -> store.decodePreset(rawJson) },
            onDeletePreset = { preset ->
                store.deletePreset(preset.id)
                refreshStatus()
                AutoClickApp.showToast("配置已删除")
            },
            onMovePoint = { point, targetIndex ->
                if (AutoClickOverlayService.isClicking()) {
                    AutoClickApp.showToast("请先停止连点再调整顺序")
                    refreshStatus()
                } else {
                    store.movePoint(point.id, targetIndex)
                    if (overlayVisible) {
                        AutoClickOverlayService.refresh(context)
                    }
                    refreshStatus()
                }
            },
            onStart = {
                saveConfig(saveAsPreset = false, showToast = false)
                if (!Settings.canDrawOverlays(context) || !AutoClickAccessibilityService.isServiceEnabled(context)) {
                    AutoClickApp.showToast("请先在首页开启悬浮窗和无障碍权限")
                } else {
                    AutoClickOverlayService.show(context)
                    refreshStatus()
                }
            },
            onStop = {
                AutoClickOverlayService.hide(context)
                refreshStatus()
            }
        )
    }
}

@Composable
private fun AutoClickContent(
    contentPadding: PaddingValues,
    overlayVisible: Boolean,
    clicking: Boolean,
    intervalText: String,
    durationText: String,
    maxClickCountText: String,
    presetNameText: String,
    points: List<AutoClickPointConfig>,
    presets: List<AutoClickPresetConfig>,
    onIntervalChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onMaxClickCountChange: (String) -> Unit,
    onPresetNameChange: (String) -> Unit,
    onCreateNew: () -> Unit,
    onSave: () -> Unit,
    onApplyPreset: (AutoClickPresetConfig) -> Unit,
    onImportPreset: (AutoClickPresetConfig) -> Unit,
    onExportPreset: (AutoClickPresetConfig) -> String,
    onDecodePreset: (String) -> AutoClickPresetConfig?,
    onDeletePreset: (AutoClickPresetConfig) -> Unit,
    onMovePoint: (AutoClickPointConfig, Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    var pendingDeletePreset by remember { mutableStateOf<AutoClickPresetConfig?>(null) }
    var exportingPreset by remember { mutableStateOf<AutoClickPresetConfig?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val preset = exportingPreset
        exportingPreset = null
        if (uri == null || preset == null) return@rememberLauncherForActivityResult
        val success = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(onExportPreset(preset))
            }
        }.isSuccess
        AutoClickApp.showToast(if (success) "已导出配置" else "导出失败")
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val preset = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }?.let(onDecodePreset)
        }.getOrNull()
        if (preset == null) {
            AutoClickApp.showToast("导入失败，JSON 格式不正确")
            return@rememberLauncherForActivityResult
        }
        onImportPreset(preset)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RuntimeSection(
            overlayVisible = overlayVisible,
            clicking = clicking,
            pointCount = points.size,
            onStart = onStart,
            onStop = onStop
        )
        CurrentConfigSection(
            intervalText = intervalText,
            durationText = durationText,
            maxClickCountText = maxClickCountText,
            presetNameText = presetNameText,
            points = points,
            onIntervalChange = onIntervalChange,
            onDurationChange = onDurationChange,
            onMaxClickCountChange = onMaxClickCountChange,
            onPresetNameChange = onPresetNameChange,
            onCreateNew = onCreateNew,
            onSave = onSave,
            reorderEnabled = !clicking,
            onMovePoint = onMovePoint
        )
        PresetSection(
            presets = presets,
            onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            onApplyPreset = onApplyPreset,
            onExportPreset = { preset ->
                exportingPreset = preset
                exportLauncher.launch(buildPresetFileName(preset))
            },
            onDeletePreset = { pendingDeletePreset = it }
        )
    }

    pendingDeletePreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDeletePreset = null },
            title = { Text("删除配置") },
            text = { Text("确定要删除“${preset.name.ifBlank { "这条配置" }}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletePreset = null
                        onDeletePreset(preset)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePreset = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun RuntimeSection(
    overlayVisible: Boolean,
    clicking: Boolean,
    pointCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    SectionSurface {
        Text(
            text = "运行",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Button(
            onClick = if (overlayVisible) onStop else onStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (overlayVisible) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (overlayVisible) "停止悬浮控制器" else "启动悬浮控制器")
        }
        StatusChip(
            label = "当前状态",
            value = when {
                clicking -> "连点中 · $pointCount 个指针"
                overlayVisible -> "控制器显示中 · $pointCount 个指针"
                else -> "未启动 · $pointCount 个指针"
            },
            highlighted = overlayVisible || clicking,
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
private fun CurrentConfigSection(
    intervalText: String,
    durationText: String,
    maxClickCountText: String,
    presetNameText: String,
    points: List<AutoClickPointConfig>,
    onIntervalChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onMaxClickCountChange: (String) -> Unit,
    onPresetNameChange: (String) -> Unit,
    onCreateNew: () -> Unit,
    onSave: () -> Unit,
    reorderEnabled: Boolean,
    onMovePoint: (AutoClickPointConfig, Int) -> Unit
) {
    SectionSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "当前配置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onCreateNew) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("新建")
            }
        }
        OutlinedTextField(
            value = presetNameText,
            onValueChange = onPresetNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("配置名称") },
            singleLine = true
        )
        OutlinedTextField(
            value = intervalText,
            onValueChange = onIntervalChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("点击间隔（毫秒，最低 50）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = durationText,
            onValueChange = onDurationChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("持续时间（秒，0 为持续）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = maxClickCountText,
            onValueChange = onMaxClickCountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("点击次数（0 为不限）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        ConfigSummary(
            intervalMillis = intervalText.toIntOrNull() ?: 100,
            durationSeconds = durationText.toIntOrNull() ?: 0,
            maxClickCount = maxClickCountText.toIntOrNull() ?: 0,
            points = points,
            reorderEnabled = reorderEnabled,
            onMovePoint = onMovePoint
        )
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.Save,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("保存到历史配置")
        }
    }
}

@Composable
private fun ConfigSummary(
    intervalMillis: Int,
    durationSeconds: Int,
    maxClickCount: Int,
    points: List<AutoClickPointConfig>,
    reorderEnabled: Boolean,
    onMovePoint: (AutoClickPointConfig, Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "当前指针数：${points.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetaTag(text = formatInterval(intervalMillis))
                MetaTag(text = formatDuration(durationSeconds))
                MetaTag(text = formatClickCount(maxClickCount))
            }
        }
    }
    if (points.isEmpty()) {
        Text(
            text = "启动悬浮控制器后会自动添加第一个指针，可在屏幕上拖动定位。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            points.forEachIndexed { index, point ->
                key(point.id) {
                    PointRow(
                        index = index,
                        point = point,
                        lastIndex = points.lastIndex,
                        reorderEnabled = reorderEnabled,
                        onMovePoint = onMovePoint
                    )
                }
            }
        }
    }
}

@Composable
private fun PointRow(
    index: Int,
    point: AutoClickPointConfig,
    lastIndex: Int,
    reorderEnabled: Boolean,
    onMovePoint: (AutoClickPointConfig, Int) -> Unit
) {
    var rowHeightPx by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val itemDistancePx = with(density) { rowHeightPx.toFloat() + 8.dp.toPx() }.coerceAtLeast(1f)
    val currentIndex by rememberUpdatedState(index)
    val currentPoint by rememberUpdatedState(point)
    val currentLastIndex by rememberUpdatedState(lastIndex)
    val currentOnMovePoint by rememberUpdatedState(onMovePoint)
    val canReorder = reorderEnabled && lastIndex > 0
    val dragModifier = if (canReorder) {
        Modifier.pointerInput(point.id, itemDistancePx) {
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
                            currentOnMovePoint(currentPoint, currentIndex - 1)
                            dragOffsetY += itemDistancePx
                        }
                        dragOffsetY >= threshold && currentIndex < currentLastIndex -> {
                            currentOnMovePoint(currentPoint, currentIndex + 1)
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
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "指针 ${index + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "坐标：${point.x}, ${point.y}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PresetSection(
    presets: List<AutoClickPresetConfig>,
    onImport: () -> Unit,
    onApplyPreset: (AutoClickPresetConfig) -> Unit,
    onExportPreset: (AutoClickPresetConfig) -> Unit,
    onDeletePreset: (AutoClickPresetConfig) -> Unit
) {
    SectionSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "历史配置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onImport) {
                Text("导入")
            }
        }
        if (presets.isEmpty()) {
            Text(
                text = "保存当前配置后，这里会显示可复用的历史配置。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEachIndexed { index, preset ->
                    PresetRow(
                        preset = preset,
                        index = index,
                        onApply = { onApplyPreset(preset) },
                        onExport = { onExportPreset(preset) },
                        onDelete = { onDeletePreset(preset) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: AutoClickPresetConfig,
    index: Int,
    onApply: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
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
                        text = preset.name.ifBlank { "配置 ${index + 1}" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatPresetTime(preset.savedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
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
                MetaTag(text = formatInterval(preset.config.intervalMillis))
                MetaTag(text = formatDuration(preset.config.durationSeconds))
                MetaTag(text = formatClickCount(preset.config.maxClickCount))
                MetaTag(text = "${preset.config.points.size} 个指针")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导出")
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("加载")
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

private fun formatDuration(durationSeconds: Int): String {
    return if (durationSeconds <= 0) "持续点击" else "$durationSeconds 秒"
}

private fun formatClickCount(maxClickCount: Int): String {
    return if (maxClickCount <= 0) "不限次数" else "$maxClickCount 次"
}

private fun formatInterval(intervalMillis: Int): String {
    return "${intervalMillis.coerceAtLeast(50)} 毫秒"
}

private fun formatPresetTime(timeMillis: Long): String {
    if (timeMillis <= 0L) return "刚刚保存"
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
    }.getOrDefault("刚刚保存")
}

private fun buildPresetFileName(preset: AutoClickPresetConfig): String {
    val safeName = preset.name
        .ifBlank { "auto_click_preset" }
        .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
        .trim('_')
        .ifBlank { "auto_click_preset" }
    return "$safeName.json"
}
