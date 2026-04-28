package org.xiaobu.autoclick.ui.component

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun AutoClickOverlayPanel(
    statusText: String,
    clicking: Boolean,
    addingMode: Boolean,
    onStartStop: () -> Unit,
    onAdd: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onMove: (offsetX: Int, offsetY: Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(min = 184.dp, max = 220.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onMove(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "连点器",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "拖动标题移动",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OverlayStepItem(index = "1", title = "添加指针")
                OverlayStepItem(index = "2", title = "拖动定位，长按删除")
                OverlayStepItem(index = "3", title = if (clicking) "停止连点" else "开始连点")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (addingMode) {
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("完成")
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                } else {
                    Button(
                        onClick = onStartStop,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (clicking) "停止" else "开始")
                    }
                    OutlinedButton(
                        onClick = onAdd,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("添加")
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayStepItem(
    index: String,
    title: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
