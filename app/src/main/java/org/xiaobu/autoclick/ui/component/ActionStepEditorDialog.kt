package org.xiaobu.autoclick.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID
import org.xiaobu.autoclick.data.task.AutoTaskActionType
import org.xiaobu.autoclick.data.task.AutoTaskStep
import org.xiaobu.autoclick.data.task.AutoTaskTarget
import org.xiaobu.autoclick.data.task.AutoTaskTargetType

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
            target = if (actionType.requiresTarget) target.normalize() else null,
            secondaryTarget = if (actionType.requiresSecondaryTarget) secondaryTarget.normalize() else null
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
        validateTarget(state.target, "执行目标")?.let { return it }
    }
    if (state.actionType.requiresSecondaryTarget) {
        validateTarget(state.secondaryTarget, "结束目标")?.let { return it }
    }
    return null
}

@Composable
fun ActionStepEditorDialog(
    state: ActionStepEditorState,
    onDismiss: () -> Unit,
    onStateChange: (ActionStepEditorState) -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 28.dp)
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
                ActionTypePicker(
                    actionType = state.actionType,
                    onActionTypeChange = { actionType ->
                        onStateChange(
                            state.copy(
                                actionType = actionType,
                                durationMsText = defaultActionDurationMs(actionType).toString(),
                                delayAfterMsText = defaultActionDelayAfterMs(actionType).toString()
                            )
                        )
                    }
                )
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
                    TargetEditor(
                        title = "执行目标",
                        target = state.target,
                        onTargetChange = { onStateChange(state.copy(target = it)) }
                    )
                }
                if (state.actionType.requiresSecondaryTarget) {
                    TargetEditor(
                        title = "结束目标",
                        target = state.secondaryTarget,
                        onTargetChange = { onStateChange(state.copy(secondaryTarget = it)) }
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
}

@Composable
private fun ActionTypePicker(
    actionType: AutoTaskActionType,
    onActionTypeChange: (AutoTaskActionType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("动作：${actionType.title}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AutoTaskActionType.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.title) },
                    onClick = {
                        expanded = false
                        onActionTypeChange(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun TargetEditor(
    title: String,
    target: AutoTaskTarget,
    onTargetChange: (AutoTaskTarget) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                TargetTypePicker(
                    targetType = target.type,
                    onTargetTypeChange = { onTargetChange(target.copy(type = it)) }
                )
            }
            when (target.type) {
                AutoTaskTargetType.COORDINATE -> CoordinateTargetEditor(
                    target = target,
                    onTargetChange = onTargetChange
                )

                AutoTaskTargetType.NODE_TEXT -> TextTargetEditor(
                    target = target,
                    onTargetChange = onTargetChange
                )
            }
        }
    }
}

@Composable
private fun TargetTypePicker(
    targetType: AutoTaskTargetType,
    onTargetTypeChange: (AutoTaskTargetType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) {
        Text(targetType.title)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AutoTaskTargetType.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.title) },
                    onClick = {
                        expanded = false
                        onTargetTypeChange(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun CoordinateTargetEditor(
    target: AutoTaskTarget,
    onTargetChange: (AutoTaskTarget) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = target.x.toString(),
            onValueChange = {
                onTargetChange(target.copy(x = it.filter(Char::isDigit).toIntOrNull() ?: 0))
            },
            modifier = Modifier.weight(1f),
            label = { Text("X") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        OutlinedTextField(
            value = target.y.toString(),
            onValueChange = {
                onTargetChange(target.copy(y = it.filter(Char::isDigit).toIntOrNull() ?: 0))
            },
            modifier = Modifier.weight(1f),
            label = { Text("Y") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
    }
}

@Composable
private fun TextTargetEditor(
    target: AutoTaskTarget,
    onTargetChange: (AutoTaskTarget) -> Unit
) {
    OutlinedTextField(
        value = target.text,
        onValueChange = { onTargetChange(target.copy(text = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("目标文字") },
        singleLine = true
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = target.index.toString(),
            onValueChange = {
                onTargetChange(target.copy(index = it.filter(Char::isDigit).toIntOrNull() ?: 1))
            },
            modifier = Modifier.weight(1f),
            label = { Text("第几个") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
}

private fun validateTarget(target: AutoTaskTarget, title: String): String? {
    return when (target.type) {
        AutoTaskTargetType.COORDINATE -> {
            if (target.x <= 0 && target.y <= 0) "${title}还没有填写坐标" else null
        }

        AutoTaskTargetType.NODE_TEXT -> when {
            target.text.isBlank() -> "请输入${title}文字"
            target.index < 1 -> "${title}的第几个必须大于 0"
            else -> null
        }
    }
}

private fun AutoTaskTarget.normalize(): AutoTaskTarget {
    return copy(
        text = text.trim(),
        index = index.coerceAtLeast(1)
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
