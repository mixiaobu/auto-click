package org.xiaobu.autoclick.ui.component

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
fun AutoTaskCoordinatePickerOverlay(
    selecting: Boolean,
    selectedX: Int?,
    selectedY: Int?,
    onStartSelecting: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onMove: (offsetX: Int, offsetY: Int) -> Unit
) {
    val hasSelection = selectedX != null && selectedY != null
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "坐标选点",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when {
                            selecting -> "请切到目标页面后，单击一次要记录的位置。"
                            hasSelection -> "已记录坐标：$selectedX, $selectedY，确认后会写回步骤。"
                            else -> "先点开始选择，再去目标页面单击添加坐标。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (hasSelection) {
                    OutlinedButton(
                        onClick = onStartSelecting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重新选择")
                    }
                } else {
                    Button(
                        onClick = onStartSelecting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("开始选择")
                    }
                }

                Button(
                    onClick = onConfirm,
                    enabled = hasSelection && !selecting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("确认坐标")
                }
            }
        }
    }
}
