package org.xiaobu.autoclick.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import org.xiaobu.autoclick.data.app.InstalledAppInfo
import org.xiaobu.autoclick.data.app.getInstalledAppInfo
import org.xiaobu.autoclick.data.task.AutoTaskActionType
import org.xiaobu.autoclick.data.task.AutoTaskStep
import org.xiaobu.autoclick.data.task.AutoTaskTarget
import org.xiaobu.autoclick.data.task.AutoTaskTargetType
import org.xiaobu.autoclick.data.trigger.AutoTriggerApp
import org.xiaobu.autoclick.data.trigger.AutoTriggerConfig
import org.xiaobu.autoclick.data.trigger.AutoTriggerEventType
import org.xiaobu.autoclick.ui.component.ActionStepEditorDialog
import org.xiaobu.autoclick.ui.component.ActionStepEditorState
import org.xiaobu.autoclick.ui.component.validateActionStepEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as AutoClickApp }
    val store = app.autoTriggerStore
    val lifecycleOwner = LocalLifecycleOwner.current
    var draftTrigger by remember { mutableStateOf(store.getDraft()) }
    var savedTriggers by remember { mutableStateOf(store.getTriggers()) }
    var editorState by remember { mutableStateOf<ActionStepEditorState?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showEventPicker by remember { mutableStateOf(false) }
    var pendingDeleteTrigger by remember { mutableStateOf<AutoTriggerConfig?>(null) }
    var exportingTrigger by remember { mutableStateOf<AutoTriggerConfig?>(null) }

    fun refreshState() {
        savedTriggers = store.getTriggers()
    }

    fun syncDraft(trigger: AutoTriggerConfig) {
        draftTrigger = trigger
        store.saveDraft(trigger)
        refreshState()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val trigger = exportingTrigger
        exportingTrigger = null
        if (uri == null || trigger == null) return@rememberLauncherForActivityResult
        val success = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(store.encodeTrigger(trigger))
            }
        }.isSuccess
        AutoClickApp.showToast(if (success) "已导出触发器" else "导出失败")
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val importedTrigger = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }?.let(store::decodeTrigger)
        }.getOrNull()
        if (importedTrigger == null) {
            AutoClickApp.showToast("导入失败，JSON 格式不正确")
            return@rememberLauncherForActivityResult
        }
        store.saveTrigger(importedTrigger)
        syncDraft(importedTrigger)
        AutoClickApp.showToast("已导入触发器")
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshState()
            delay(1200)
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
                title = { Text("触发器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
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
            TriggerDraftSection(
                draftTrigger = draftTrigger,
                onDraftChange = ::syncDraft,
                onSelectApps = { showAppPicker = true },
                onSelectEvents = { showEventPicker = true },
                onCreateNew = {
                    syncDraft(AutoTriggerConfig(updatedAt = System.currentTimeMillis()))
                },
                onAddStep = { editorState = ActionStepEditorState() },
                onEditStep = { step -> editorState = ActionStepEditorState.from(step) },
                onDeleteStep = { step ->
                    syncDraft(draftTrigger.copy(steps = draftTrigger.steps.filterNot { it.id == step.id }))
                },
                onSave = {
                    validateTriggerDraft(draftTrigger)?.let { error ->
                        AutoClickApp.showToast(error)
                        return@TriggerDraftSection
                    }
                    val saved = draftTrigger.copy(updatedAt = System.currentTimeMillis())
                    store.saveTrigger(saved)
                    syncDraft(saved)
                    AutoClickApp.showToast("触发器已保存")
                }
            )
            SavedTriggerSection(
                triggers = savedTriggers,
                onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                onApply = { trigger -> syncDraft(trigger) },
                onExport = { trigger ->
                    exportingTrigger = trigger
                    exportLauncher.launch(buildTriggerFileName(trigger))
                },
                onToggleEnabled = { trigger, enabled ->
                    store.saveTrigger(trigger.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
                    refreshState()
                },
                onDelete = { trigger -> pendingDeleteTrigger = trigger }
            )
        }
    }

    editorState?.let { state ->
        ActionStepEditorDialog(
            state = state,
            onDismiss = { editorState = null },
            onStateChange = { editorState = it },
            onConfirm = {
                val current = editorState ?: return@ActionStepEditorDialog
                val error = validateActionStepEditor(current)
                if (error != null) {
                    AutoClickApp.showToast(error)
                    return@ActionStepEditorDialog
                }
                val step = current.toStep()
                val updatedSteps = if (draftTrigger.steps.any { it.id == step.id }) {
                    draftTrigger.steps.map { if (it.id == step.id) step else it }
                } else {
                    draftTrigger.steps + step
                }
                syncDraft(draftTrigger.copy(steps = updatedSteps, updatedAt = System.currentTimeMillis()))
                editorState = null
            }
        )
    }

    if (showEventPicker) {
        EventSelectionDialog(
            selectedEventTypes = draftTrigger.effectiveEventTypes,
            onDismiss = { showEventPicker = false },
            onConfirm = { selected ->
                val safeSelected = selected.ifEmpty { listOf(AutoTriggerEventType.PAGE_NAVIGATED) }
                syncDraft(
                    draftTrigger.copy(
                        eventType = safeSelected.first(),
                        eventTypes = safeSelected,
                        cooldownMs = draftTrigger.cooldownMs.takeIf { it > 0L }
                            ?: safeSelected.first().defaultCooldownMs
                    )
                )
                showEventPicker = false
            }
        )
    }

    if (showAppPicker) {
        AppSelectionDialog(
            selectedApps = draftTrigger.targetApps,
            onDismiss = { showAppPicker = false },
            onConfirm = { apps ->
                syncDraft(
                    draftTrigger.copy(
                        targetApps = apps,
                        packageName = apps.firstOrNull()?.packageName.orEmpty(),
                        appLabel = apps.firstOrNull()?.appLabel.orEmpty()
                    )
                )
                showAppPicker = false
            }
        )
    }

    pendingDeleteTrigger?.let { trigger ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTrigger = null },
            title = { Text("删除触发器") },
            text = { Text("确定要删除“${trigger.name.ifBlank { "这个触发器" }}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteTrigger = null
                        store.deleteTrigger(trigger.id)
                        refreshState()
                        AutoClickApp.showToast("触发器已删除")
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTrigger = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun TriggerDraftSection(
    draftTrigger: AutoTriggerConfig,
    onDraftChange: (AutoTriggerConfig) -> Unit,
    onSelectApps: () -> Unit,
    onSelectEvents: () -> Unit,
    onCreateNew: () -> Unit,
    onAddStep: () -> Unit,
    onEditStep: (AutoTaskStep) -> Unit,
    onDeleteStep: (AutoTaskStep) -> Unit,
    onSave: () -> Unit
) {
    SectionSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "当前编辑",
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
            value = draftTrigger.name,
            onValueChange = { onDraftChange(draftTrigger.copy(name = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("触发器名称") },
            singleLine = true
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelectFieldButton(
                label = "目标应用",
                value = buildTriggerAppSummary(draftTrigger),
                onClick = onSelectApps,
                modifier = Modifier.fillMaxWidth()
            )
            SelectFieldButton(
                label = "触发事件",
                value = buildTriggerEventSummary(draftTrigger.effectiveEventTypes),
                onClick = onSelectEvents,
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = draftTrigger.cooldownMs.toString(),
            onValueChange = {
                onDraftChange(draftTrigger.copy(cooldownMs = it.filter(Char::isDigit).toLongOrNull() ?: 0L))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("触发冷却（毫秒）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        OutlinedTextField(
            value = draftTrigger.pageKeyword,
            onValueChange = { onDraftChange(draftTrigger.copy(pageKeyword = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("可选关键词过滤") },
            singleLine = true
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("关键词精确匹配", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (draftTrigger.keywordExact) "页面文本必须完全一致" else "页面文本包含关键词即可",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            Switch(
                checked = draftTrigger.keywordExact,
                onCheckedChange = { onDraftChange(draftTrigger.copy(keywordExact = it)) }
            )
            }
        }
        StepList(
            steps = draftTrigger.steps,
            onAddStep = onAddStep,
            onEditStep = onEditStep,
            onDeleteStep = onDeleteStep
        )
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text("保存触发器")
        }
    }
}

@Composable
private fun SelectFieldButton(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StepList(
    steps: List<AutoTaskStep>,
    onAddStep: () -> Unit,
    onEditStep: (AutoTaskStep) -> Unit,
    onDeleteStep: (AutoTaskStep) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "执行步骤",
                style = MaterialTheme.typography.titleSmall,
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
                text = "还没有步骤。触发后会按顺序执行这里的动作。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            steps.forEachIndexed { index, step ->
                StepRow(
                    index = index,
                    step = step,
                    onEdit = { onEditStep(step) },
                    onDelete = { onDeleteStep(step) }
                )
            }
        }
    }
}

@Composable
private fun StepRow(
    index: Int,
    step: AutoTaskStep,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
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
            TextButton(onClick = onEdit) {
                Text("编辑")
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
private fun SavedTriggerSection(
    triggers: List<AutoTriggerConfig>,
    onImport: () -> Unit,
    onApply: (AutoTriggerConfig) -> Unit,
    onExport: (AutoTriggerConfig) -> Unit,
    onToggleEnabled: (AutoTriggerConfig, Boolean) -> Unit,
    onDelete: (AutoTriggerConfig) -> Unit
) {
    SectionSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已保存触发器",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onImport) {
                Text("导入")
            }
        }
        if (triggers.isEmpty()) {
            Text(
                text = "还没有保存过触发器。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            triggers.forEach { trigger ->
                SavedTriggerRow(
                    trigger = trigger,
                    onApply = { onApply(trigger) },
                    onExport = { onExport(trigger) },
                    onToggleEnabled = { enabled -> onToggleEnabled(trigger, enabled) },
                    onDelete = { onDelete(trigger) }
                )
            }
        }
    }
}

@Composable
private fun SavedTriggerRow(
    trigger: AutoTriggerConfig,
    onApply: () -> Unit,
    onExport: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
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
                        text = trigger.name.ifBlank { "未命名触发器" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Switch(
                    checked = trigger.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetaTag(text = buildTriggerEventSummary(trigger.effectiveEventTypes))
                MetaTag(text = buildTriggerAppSummary(trigger))
                MetaTag(text = "步骤 ${trigger.steps.size} 个")
                MetaTag(text = formatTime(trigger.updatedAt))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Text("导出")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
                Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                    Text("加载")
                }
            }
        }
    }
}

@Composable
private fun EventSelectionDialog(
    selectedEventTypes: List<AutoTriggerEventType>,
    onDismiss: () -> Unit,
    onConfirm: (List<AutoTriggerEventType>) -> Unit
) {
    var selectedSet by remember(selectedEventTypes) {
        mutableStateOf(selectedEventTypes.ifEmpty { listOf(AutoTriggerEventType.PAGE_NAVIGATED) }.toSet())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择触发事件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoTriggerEventType.entries.forEach { eventType ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = eventType in selectedSet,
                            onCheckedChange = { checked ->
                                selectedSet = if (checked) {
                                    selectedSet + eventType
                                } else {
                                    selectedSet - eventType
                                }
                            }
                        )
                        Column {
                            Text(eventType.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = eventType.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedSet.toList()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AppSelectionDialog(
    selectedApps: List<AutoTriggerApp>,
    onDismiss: () -> Unit,
    onConfirm: (List<AutoTriggerApp>) -> Unit
) {
    val context = LocalContext.current
    var keyword by rememberSaveable { mutableStateOf("") }
    val allApps = remember(context) { context.getInstalledAppInfo() }
    var selectedMap by remember(selectedApps) {
        mutableStateOf(selectedApps.associateBy { it.packageName }.toMutableMap())
    }
    val filteredApps = remember(allApps, keyword) {
        val query = keyword.trim()
        if (query.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.appLabel.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择目标应用") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索应用") },
                    singleLine = true
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppSelectionRow(
                            app = app,
                            checked = selectedMap.containsKey(app.packageName),
                            onCheckedChange = { checked ->
                                selectedMap = selectedMap.toMutableMap().apply {
                                    if (checked) {
                                        put(
                                            app.packageName,
                                            AutoTriggerApp(
                                                packageName = app.packageName,
                                                appLabel = app.appLabel
                                            )
                                        )
                                    } else {
                                        remove(app.packageName)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedMap.values.toList()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AppSelectionRow(
    app: InstalledAppInfo,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appLabel, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (app.isSystemApp) "${app.packageName} · 系统应用" else app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

private fun validateTriggerDraft(trigger: AutoTriggerConfig): String? {
    if (trigger.targetApps.isEmpty()) return "请先选择目标应用"
    if (trigger.effectiveEventTypes.isEmpty()) return "请先选择触发事件"
    if (trigger.cooldownMs < 0L) return "触发冷却不能小于 0"
    if (trigger.steps.isEmpty()) return "请先添加至少一个步骤"
    return null
}

private fun buildStepSummary(step: AutoTaskStep): String {
    return when (step.actionType) {
        AutoTaskActionType.WAIT -> "等待 ${step.durationMs}ms"
        AutoTaskActionType.TAP -> "单击 ${buildTargetSummary(step.target)}"
        AutoTaskActionType.DOUBLE_TAP -> "双击 ${buildTargetSummary(step.target)}"
        AutoTaskActionType.LONG_PRESS -> "长按 ${buildTargetSummary(step.target)}"
        AutoTaskActionType.SWIPE -> "从 ${buildTargetSummary(step.target)} 滑动到 ${buildTargetSummary(step.secondaryTarget)}"
        AutoTaskActionType.BACK -> "执行返回"
        AutoTaskActionType.HOME -> "返回主页"
        AutoTaskActionType.RECENTS -> "打开最近任务"
        AutoTaskActionType.NOTIFICATIONS -> "打开通知栏"
        AutoTaskActionType.QUICK_SETTINGS -> "打开快捷设置"
        AutoTaskActionType.LOCK_SCREEN -> "锁屏"
    }
}

private fun buildTargetSummary(target: AutoTaskTarget?): String {
    if (target == null) return "无目标"
    return when (target.type) {
        AutoTaskTargetType.COORDINATE -> "坐标(${target.x}, ${target.y})"
        AutoTaskTargetType.NODE_TEXT -> "文字“${target.text}”第${target.index}个"
    }
}

private fun buildTriggerEventSummary(eventTypes: List<AutoTriggerEventType>): String {
    return eventTypes.ifEmpty { listOf(AutoTriggerEventType.PAGE_NAVIGATED) }
        .joinToString("、") { it.title }
}

private fun buildTriggerAppSummary(trigger: AutoTriggerConfig): String {
    val apps = trigger.targetApps.ifEmpty {
        listOfNotNull(
            trigger.packageName.takeIf { it.isNotBlank() }?.let {
                AutoTriggerApp(packageName = it, appLabel = trigger.appLabel)
            }
        )
    }
    if (apps.isEmpty()) return "选择目标应用"
    val first = apps.first()
    val firstLabel = first.appLabel.ifBlank { first.packageName }
    return if (apps.size == 1) firstLabel else "$firstLabel 等 ${apps.size} 个应用"
}

private fun formatTime(timeMillis: Long): String {
    if (timeMillis <= 0L) return "刚刚保存"
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))
    }.getOrDefault("刚刚保存")
}

private fun buildTriggerFileName(trigger: AutoTriggerConfig): String {
    val safeName = trigger.name
        .ifBlank { "auto_trigger" }
        .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
        .trim('_')
        .ifBlank { "auto_trigger" }
    return "$safeName.json"
}
