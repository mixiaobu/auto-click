package org.xiaobu.autoclick.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID
import org.xiaobu.autoclick.data.task.AutoTaskActionType
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

data class ActionStepEditorState(
    val stepId: String? = null,
    val actionType: AutoTaskActionType = AutoTaskActionType.TAP,
    val title: String = "",
    val durationMsText: String = defaultActionDurationMs(AutoTaskActionType.TAP).toString(),
    val delayAfterMsText: String = defaultActionDelayAfterMs(AutoTaskActionType.TAP).toString(),
    val target: AutoTaskTarget = AutoTaskTarget(),
    val secondaryTarget: AutoTaskTarget = AutoTaskTarget()
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
            target = if (actionType.requiresTarget) target.normalizeForEditor() else null,
            secondaryTarget = if (actionType.requiresSecondaryTarget) {
                secondaryTarget.normalizeForEditor()
            } else {
                null
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
                secondaryTarget = step.secondaryTarget ?: AutoTaskTarget()
            )
        }
    }
}

fun validateActionStepEditor(state: ActionStepEditorState): String? {
    val durationMs = state.durationMsText.toLongOrNull()
    if (durationMs == null || durationMs < 20L) return "动作时长至少 20ms"

    val delayAfterMs = state.delayAfterMsText.toLongOrNull()
    if (delayAfterMs == null || delayAfterMs < 0L) return "后续延迟不能小于 0"

    if (state.actionType.requiresTarget) {
        validateEditorTarget(state.target, "执行目标")?.let { return it }
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
    var targetTypeSlot by remember { mutableStateOf<ActionStepTargetPickerSlot?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
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
                            Text(if (state.actionType == AutoTaskActionType.WAIT) "等待时长(ms)" else "动作时长(ms)")
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
                if (state.actionType.requiresTarget) {
                    ActionStepTargetEditor(
                        title = "执行目标",
                        target = state.target,
                        onTargetChange = { onStateChange(state.copy(target = it)) },
                        onSelectTargetType = { targetTypeSlot = ActionStepTargetPickerSlot.TARGET },
                        onPickCoordinate = {
                            onPickCoordinate(ActionStepCoordinateSlot.TARGET, state.target)
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
                        delayAfterMsText = defaultActionDelayAfterMs(actionType).toString()
                    )
                )
                showActionDialog = false
            }
        )
    }

    targetTypeSlot?.let { slot ->
        val currentTarget = if (slot == ActionStepTargetPickerSlot.TARGET) {
            state.target
        } else {
            state.secondaryTarget
        }
        ActionStepSelectionDialog(
            title = "选择目标类型",
            options = AutoTaskTargetType.entries.map { it.title },
            selectedIndex = AutoTaskTargetType.entries.indexOf(currentTarget.type),
            onDismiss = { targetTypeSlot = null },
            onSelect = { index ->
                val updatedTarget = currentTarget.copy(type = AutoTaskTargetType.entries[index])
                if (slot == ActionStepTargetPickerSlot.TARGET) {
                    onStateChange(state.copy(target = updatedTarget))
                } else {
                    onStateChange(state.copy(secondaryTarget = updatedTarget))
                }
                targetTypeSlot = null
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
private fun ActionStepSelectionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

private fun AutoTaskTarget.normalizeForEditor(): AutoTaskTarget {
    return copy(
        text = text.trim(),
        index = index.coerceAtLeast(1),
        imageUri = imageUri.trim()
    )
}

fun defaultActionDurationMs(actionType: AutoTaskActionType): Long {
    return when (actionType) {
        AutoTaskActionType.WAIT -> 1000L
        AutoTaskActionType.TAP -> 80L
        AutoTaskActionType.DOUBLE_TAP -> 80L
        AutoTaskActionType.LONG_PRESS -> 500L
        AutoTaskActionType.SWIPE -> 600L
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
        AutoTaskActionType.TAP -> 200L
        AutoTaskActionType.DOUBLE_TAP -> 260L
        AutoTaskActionType.LONG_PRESS -> 320L
        AutoTaskActionType.SWIPE -> 320L
        AutoTaskActionType.BACK,
        AutoTaskActionType.HOME,
        AutoTaskActionType.RECENTS,
        AutoTaskActionType.NOTIFICATIONS,
        AutoTaskActionType.QUICK_SETTINGS,
        AutoTaskActionType.LOCK_SCREEN -> 450L
    }
}
