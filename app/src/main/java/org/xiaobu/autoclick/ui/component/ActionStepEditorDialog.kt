package org.xiaobu.autoclick.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID
import org.xiaobu.autoclick.data.app.getInstalledAppInfo
import org.xiaobu.autoclick.data.task.AutoTaskActionType
import org.xiaobu.autoclick.data.task.AutoTaskFailureStrategy
import org.xiaobu.autoclick.data.task.AutoTaskStep
import org.xiaobu.autoclick.data.task.AutoTaskTarget
import org.xiaobu.autoclick.data.task.AutoTaskTargetType

enum class ActionStepCoordinateSlot {
    TARGET,
    SECONDARY
}

enum class ActionStepTargetPickerSlot {
    TARGET,
    SECONDARY
}

private const val DEFAULT_FAILURE_RETRY_COUNT = 1
private const val MAX_FAILURE_RETRY_COUNT = 10

data class ActionStepEditorState(
    val stepId: String? = null,
    val actionType: AutoTaskActionType = AutoTaskActionType.TAP,
    val title: String = "",
    val durationMsText: String = defaultActionDurationMs(AutoTaskActionType.TAP).toString(),
    val delayAfterMsText: String = defaultActionDelayAfterMs(AutoTaskActionType.TAP).toString(),
    val target: AutoTaskTarget = AutoTaskTarget(),
    val secondaryTarget: AutoTaskTarget = AutoTaskTarget(),
    val appPackageName: String = "",
    val appLabel: String = "",
    val failureStrategy: AutoTaskFailureStrategy = AutoTaskFailureStrategy.STOP,
    val failureRetryCountText: String = DEFAULT_FAILURE_RETRY_COUNT.toString()
) {
    fun toStep(): AutoTaskStep {
        return AutoTaskStep(
            id = stepId ?: UUID.randomUUID().toString(),
            actionType = actionType,
            title = title.trim(),
            durationMs = durationMsText.toLongOrNull()?.coerceAtLeast(20L)
                ?: defaultActionDurationMs(actionType),
            delayAfterMs = delayAfterMsText.toLongOrNull()?.coerceAtLeast(0L)
                ?: defaultActionDelayAfterMs(actionType),
            target = if (actionType.requiresTarget) target.withAllowedTypeFor(actionType).normalizeForEditor() else null,
            secondaryTarget = if (actionType.requiresSecondaryTarget) {
                secondaryTarget.normalizeForEditor()
            } else {
                null
            },
            appPackageName = if (actionType.requiresAppTarget) appPackageName.trim() else "",
            appLabel = if (actionType.requiresAppTarget) appLabel.trim() else "",
            failureStrategy = failureStrategy,
            failureRetryCount = if (failureStrategy == AutoTaskFailureStrategy.RETRY) {
                failureRetryCountText.toIntOrNull()?.coerceIn(1, MAX_FAILURE_RETRY_COUNT)
                    ?: DEFAULT_FAILURE_RETRY_COUNT
            } else {
                DEFAULT_FAILURE_RETRY_COUNT
            }
        )
    }

    companion object {
        fun from(step: AutoTaskStep): ActionStepEditorState {
            return ActionStepEditorState(
                stepId = step.id,
                actionType = step.actionType,
                title = step.title,
                durationMsText = step.durationMs.toString(),
                delayAfterMsText = step.delayAfterMs.toString(),
                target = step.target ?: AutoTaskTarget(),
                secondaryTarget = step.secondaryTarget ?: AutoTaskTarget(),
                appPackageName = step.appPackageName,
                appLabel = step.appLabel,
                failureStrategy = step.safeFailureStrategy(),
                failureRetryCountText = step.safeFailureRetryCount().toString()
            )
        }
    }
}

fun validateActionStepEditor(state: ActionStepEditorState): String? {
    val durationMs = state.durationMsText.toLongOrNull()
    if (durationMs == null || durationMs < 20L) return "动作时长至少 20ms"

    val delayAfterMs = state.delayAfterMsText.toLongOrNull()
    if (delayAfterMs == null || delayAfterMs < 0L) return "后续延迟不能小于 0"

    if (state.failureStrategy == AutoTaskFailureStrategy.RETRY) {
        val retryCount = state.failureRetryCountText.toIntOrNull()
        if (retryCount == null || retryCount !in 1..MAX_FAILURE_RETRY_COUNT) {
            return "重试次数必须是 1 到 $MAX_FAILURE_RETRY_COUNT"
        }
    }

    if (state.actionType.requiresAppTarget && state.appPackageName.isBlank()) {
        return "请选择或输入要打开的应用"
    }
    if (state.actionType.requiresTarget) {
        val targetTitle = if (state.actionType.isWaitTargetAction()) {
            state.actionType.title
        } else {
            "执行目标"
        }
        if (
            state.actionType.isWaitTargetAction() &&
            state.target.type == AutoTaskTargetType.COORDINATE
        ) {
            return "${state.actionType.title}请使用文字查找、文字识别或图片识别"
        }
        validateEditorTarget(state.target, targetTitle)?.let { return it }
    }
    if (state.actionType.requiresSecondaryTarget) {
        validateEditorTarget(state.secondaryTarget, "结束目标")?.let { return it }
    }
    return null
}

@Composable
fun ActionStepEditorDialog(
    state: ActionStepEditorState,
    onDismiss: () -> Unit,
    onStateChange: (ActionStepEditorState) -> Unit,
    onPickCoordinate: (ActionStepCoordinateSlot, AutoTaskTarget) -> Unit,
    onPickImage: (ActionStepTargetPickerSlot) -> Unit,
    onConfirm: () -> Unit
) {
    var showActionDialog by remember { mutableStateOf(false) }
    var showFailureStrategyDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var targetTypeSlot by remember { mutableStateOf<ActionStepTargetPickerSlot?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = maxHeight)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (state.stepId == null) "添加步骤" else "编辑步骤",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(
                            onClick = { showActionDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("动作：${state.actionType.title}")
                        }
                        OutlinedTextField(
                            value = state.title,
                            onValueChange = { onStateChange(state.copy(title = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("步骤名称") },
                            placeholder = { Text("可选，不填则显示动作名称") },
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = state.durationMsText,
                                onValueChange = {
                                    onStateChange(state.copy(durationMsText = it.filter(Char::isDigit)))
                                },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(buildActionDurationLabel(state.actionType))
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = state.delayAfterMsText,
                                onValueChange = {
                                    onStateChange(state.copy(delayAfterMsText = it.filter(Char::isDigit)))
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text("后续延迟(ms)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                        OutlinedButton(
                            onClick = { showFailureStrategyDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("策略：${state.failureStrategy.title}")
                        }
                        if (state.failureStrategy == AutoTaskFailureStrategy.RETRY) {
                            OutlinedTextField(
                                value = state.failureRetryCountText,
                                onValueChange = {
                                    onStateChange(state.copy(failureRetryCountText = it.filter(Char::isDigit)))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("重试次数") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                        if (state.actionType.requiresAppTarget) {
                            ActionStepAppTargetEditor(
                                appPackageName = state.appPackageName,
                                appLabel = state.appLabel,
                                onPackageNameChange = { packageName ->
                                    onStateChange(
                                        state.copy(
                                            appPackageName = packageName,
                                            appLabel = if (packageName == state.appPackageName) state.appLabel else ""
                                        )
                                    )
                                },
                                onSelectApp = { showAppPicker = true }
                            )
                        }
                        if (state.actionType.requiresTarget) {
                            val primaryTarget = state.target.withAllowedTypeFor(state.actionType)
                            ActionStepTargetEditor(
                                title = if (state.actionType.isWaitTargetAction()) {
                                    state.actionType.title
                                } else {
                                    "执行目标"
                                },
                                target = primaryTarget,
                                onTargetChange = { onStateChange(state.copy(target = it)) },
                                onSelectTargetType = { targetTypeSlot = ActionStepTargetPickerSlot.TARGET },
                                onPickCoordinate = {
                                    onPickCoordinate(ActionStepCoordinateSlot.TARGET, primaryTarget)
                                },
                                onPickImage = { onPickImage(ActionStepTargetPickerSlot.TARGET) }
                            )
                        }
                        if (state.actionType.requiresSecondaryTarget) {
                            ActionStepTargetEditor(
                                title = "结束目标",
                                target = state.secondaryTarget,
                                onTargetChange = { onStateChange(state.copy(secondaryTarget = it)) },
                                onSelectTargetType = { targetTypeSlot = ActionStepTargetPickerSlot.SECONDARY },
                                onPickCoordinate = {
                                    onPickCoordinate(ActionStepCoordinateSlot.SECONDARY, state.secondaryTarget)
                                },
                                onPickImage = { onPickImage(ActionStepTargetPickerSlot.SECONDARY) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }

    if (showActionDialog) {
        ActionStepSelectionDialog(
            title = "选择动作",
            options = AutoTaskActionType.entries.map { it.title },
            selectedIndex = AutoTaskActionType.entries.indexOf(state.actionType),
            onDismiss = { showActionDialog = false },
            onSelect = { index ->
                val actionType = AutoTaskActionType.entries[index]
                onStateChange(
                    state.copy(
                        actionType = actionType,
                        durationMsText = defaultActionDurationMs(actionType).toString(),
                        delayAfterMsText = defaultActionDelayAfterMs(actionType).toString(),
                        target = state.target.withAllowedTypeFor(actionType)
                    )
                )
                showActionDialog = false
            }
        )
    }

    if (showFailureStrategyDialog) {
        ActionStepSelectionDialog(
            title = "策略",
            options = AutoTaskFailureStrategy.entries.map { it.title },
            selectedIndex = AutoTaskFailureStrategy.entries.indexOf(state.failureStrategy),
            onDismiss = { showFailureStrategyDialog = false },
            onSelect = { index ->
                onStateChange(state.copy(failureStrategy = AutoTaskFailureStrategy.entries[index]))
                showFailureStrategyDialog = false
            }
        )
    }

    targetTypeSlot?.let { slot ->
        val currentTarget = if (slot == ActionStepTargetPickerSlot.TARGET) {
            state.target
        } else {
            state.secondaryTarget
        }
        val targetTypeOptions = if (slot == ActionStepTargetPickerSlot.TARGET) {
            allowedTargetTypesFor(state.actionType)
        } else {
            AutoTaskTargetType.entries
        }
        ActionStepSelectionDialog(
            title = "选择目标类型",
            options = targetTypeOptions.map { it.title },
            selectedIndex = targetTypeOptions.indexOf(currentTarget.type).coerceAtLeast(0),
            onDismiss = { targetTypeSlot = null },
            onSelect = { index ->
                val updatedTarget = currentTarget.copy(type = targetTypeOptions[index])
                if (slot == ActionStepTargetPickerSlot.TARGET) {
                    onStateChange(state.copy(target = updatedTarget))
                } else {
                    onStateChange(state.copy(secondaryTarget = updatedTarget))
                }
                targetTypeSlot = null
            }
        )
    }

    if (showAppPicker) {
        val context = LocalContext.current
        AppPickerDialog(
            apps = remember(context) { context.getInstalledAppInfo() },
            selectedPackageNames = setOf(state.appPackageName),
            selectionMode = AppPickerSelectionMode.SINGLE,
            onDismiss = { showAppPicker = false },
            onConfirm = { apps ->
                val app = apps.firstOrNull() ?: return@AppPickerDialog
                onStateChange(
                    state.copy(
                        appPackageName = app.packageName,
                        appLabel = app.appLabel
                    )
                )
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun ActionStepTargetEditor(
    title: String,
    target: AutoTaskTarget,
    onTargetChange: (AutoTaskTarget) -> Unit,
    onSelectTargetType: () -> Unit,
    onPickCoordinate: () -> Unit,
    onPickImage: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(onClick = onSelectTargetType) {
                    Text(target.type.title)
                }
            }

            when (target.type) {
                AutoTaskTargetType.COORDINATE -> {
                    OutlinedButton(
                        onClick = onPickCoordinate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Place,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (target.x > 0 || target.y > 0) {
                                "启动选点悬浮窗 (${target.x}, ${target.y})"
                            } else {
                                "启动选点悬浮窗"
                            },
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    Text(
                        text = "先打开悬浮选点，再到目标页面单击记录坐标。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AutoTaskTargetType.NODE_TEXT,
                AutoTaskTargetType.OCR_TEXT -> {
                    OutlinedTextField(
                        value = target.text,
                        onValueChange = { onTargetChange(target.copy(text = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(if (target.type == AutoTaskTargetType.NODE_TEXT) "目标文字" else "文字识别")
                        },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = target.index.toString(),
                            onValueChange = {
                                onTargetChange(
                                    target.copy(index = it.filter(Char::isDigit).toIntOrNull() ?: 1)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("第几个") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("精确匹配", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = target.exact,
                                onCheckedChange = { onTargetChange(target.copy(exact = it)) }
                            )
                        }
                    }
                }

                AutoTaskTargetType.IMAGE -> {
                    OutlinedButton(
                        onClick = onPickImage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (target.imageUri.isBlank()) "选择图片" else "更换图片",
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    if (target.imageUri.isNotBlank()) {
                        Text(
                            text = "已选择：${buildEditorTargetSummary(target)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionStepAppTargetEditor(
    appPackageName: String,
    appLabel: String,
    onPackageNameChange: (String) -> Unit,
    onSelectApp: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "目标应用",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(onClick = onSelectApp) {
                    Text(if (appPackageName.isBlank()) "选择应用" else "重新选择")
                }
            }
            if (appPackageName.isNotBlank()) {
                Text(
                    text = buildAppTargetSummary(appPackageName, appLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedTextField(
                value = appPackageName,
                onValueChange = { onPackageNameChange(it.trim()) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("应用包名") },
                placeholder = { Text("例如 com.example.app") },
                singleLine = true
            )
            Text(
                text = "启动时会根据包名查找应用入口，手动输入包名也可以使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionStepSelectionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .heightIn(max = maxHeight)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        options.forEachIndexed { index, option ->
                            Surface(
                                onClick = { onSelect(index) },
                                color = if (index == selectedIndex) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = option,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    color = if (index == selectedIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

private fun validateEditorTarget(target: AutoTaskTarget, title: String): String? {
    return when (target.type) {
        AutoTaskTargetType.COORDINATE -> {
            if (target.x <= 0 && target.y <= 0) "${title}还没有选择坐标" else null
        }

        AutoTaskTargetType.NODE_TEXT,
        AutoTaskTargetType.OCR_TEXT -> when {
            target.text.isBlank() -> "请输入${title}的文字"
            target.index < 1 -> "${title}的第几个必须大于 0"
            else -> null
        }

        AutoTaskTargetType.IMAGE -> {
            if (target.imageUri.isBlank()) "请选择${title}的图片" else null
        }
    }
}

private fun buildEditorTargetSummary(target: AutoTaskTarget): String {
    return when (target.type) {
        AutoTaskTargetType.COORDINATE -> "坐标(${target.x}, ${target.y})"
        AutoTaskTargetType.NODE_TEXT -> "文字“${target.text}”第${target.index}个"
        AutoTaskTargetType.OCR_TEXT -> "OCR“${target.text}”第${target.index}个"
        AutoTaskTargetType.IMAGE -> "图片识别"
    }
}

private fun buildAppTargetSummary(packageName: String, appLabel: String): String {
    return if (appLabel.isBlank() || appLabel == packageName) {
        packageName
    } else {
        "$appLabel · $packageName"
    }
}

private fun AutoTaskActionType.isWaitTargetAction(): Boolean {
    return this == AutoTaskActionType.WAIT_FOR_TARGET ||
        this == AutoTaskActionType.WAIT_FOR_TARGET_DISAPPEAR
}

@Suppress("SENSELESS_COMPARISON")
private fun AutoTaskStep.safeFailureStrategy(): AutoTaskFailureStrategy {
    return if (failureStrategy == null) AutoTaskFailureStrategy.STOP else failureStrategy
}

private fun AutoTaskStep.safeFailureRetryCount(): Int {
    return if (safeFailureStrategy() == AutoTaskFailureStrategy.RETRY) {
        failureRetryCount.coerceIn(1, MAX_FAILURE_RETRY_COUNT)
    } else {
        DEFAULT_FAILURE_RETRY_COUNT
    }
}

private fun allowedTargetTypesFor(actionType: AutoTaskActionType): List<AutoTaskTargetType> {
    return if (actionType.isWaitTargetAction()) {
        AutoTaskTargetType.entries.filterNot { it == AutoTaskTargetType.COORDINATE }
    } else {
        AutoTaskTargetType.entries
    }
}

private fun AutoTaskTarget.withAllowedTypeFor(actionType: AutoTaskActionType): AutoTaskTarget {
    val allowedTypes = allowedTargetTypesFor(actionType)
    return if (type in allowedTypes) {
        this
    } else {
        copy(type = allowedTypes.first())
    }
}

private fun AutoTaskTarget.normalizeForEditor(): AutoTaskTarget {
    return copy(
        text = text.trim(),
        index = index.coerceAtLeast(1),
        imageUri = imageUri.trim()
    )
}

private fun buildActionDurationLabel(actionType: AutoTaskActionType): String {
    return when (actionType) {
        AutoTaskActionType.WAIT -> "等待时长(ms)"
        AutoTaskActionType.WAIT_FOR_TARGET,
        AutoTaskActionType.WAIT_FOR_TARGET_DISAPPEAR -> "超时时长(ms)"
        else -> "动作时长(ms)"
    }
}

fun defaultActionDurationMs(actionType: AutoTaskActionType): Long {
    return when (actionType) {
        AutoTaskActionType.WAIT -> 1000L
        AutoTaskActionType.WAIT_FOR_TARGET,
        AutoTaskActionType.WAIT_FOR_TARGET_DISAPPEAR -> 5000L
        AutoTaskActionType.TAP -> 80L
        AutoTaskActionType.DOUBLE_TAP -> 80L
        AutoTaskActionType.LONG_PRESS -> 500L
        AutoTaskActionType.SWIPE -> 600L
        AutoTaskActionType.SWIPE_UP,
        AutoTaskActionType.SWIPE_DOWN,
        AutoTaskActionType.SWIPE_LEFT,
        AutoTaskActionType.SWIPE_RIGHT -> 600L
        AutoTaskActionType.OPEN_APP -> 120L
        AutoTaskActionType.BACK,
        AutoTaskActionType.HOME,
        AutoTaskActionType.RECENTS,
        AutoTaskActionType.NOTIFICATIONS,
        AutoTaskActionType.QUICK_SETTINGS,
        AutoTaskActionType.LOCK_SCREEN -> 120L
    }
}

fun defaultActionDelayAfterMs(actionType: AutoTaskActionType): Long {
    return when (actionType) {
        AutoTaskActionType.WAIT -> 0L
        AutoTaskActionType.WAIT_FOR_TARGET,
        AutoTaskActionType.WAIT_FOR_TARGET_DISAPPEAR -> 100L
        AutoTaskActionType.TAP -> 200L
        AutoTaskActionType.DOUBLE_TAP -> 260L
        AutoTaskActionType.LONG_PRESS -> 320L
        AutoTaskActionType.SWIPE -> 320L
        AutoTaskActionType.SWIPE_UP,
        AutoTaskActionType.SWIPE_DOWN,
        AutoTaskActionType.SWIPE_LEFT,
        AutoTaskActionType.SWIPE_RIGHT -> 320L
        AutoTaskActionType.OPEN_APP -> 800L
        AutoTaskActionType.BACK,
        AutoTaskActionType.HOME,
        AutoTaskActionType.RECENTS,
        AutoTaskActionType.NOTIFICATIONS,
        AutoTaskActionType.QUICK_SETTINGS,
        AutoTaskActionType.LOCK_SCREEN -> 450L
    }
}
