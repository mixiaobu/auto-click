package org.xiaobu.autoclick.ui.screen

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.xiaobu.autoclick.AutoClickApp
import org.xiaobu.autoclick.service.AutoClickAccessibilityService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenAutoClick: () -> Unit,
    onOpenTrigger: () -> Unit
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as AutoClickApp }
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(false) }
    var accessibilityGranted by remember { mutableStateOf(false) }
    var autoClickPresetCount by remember { mutableIntStateOf(0) }
    var triggerCount by remember { mutableIntStateOf(0) }
    var enabledTriggerCount by remember { mutableIntStateOf(0) }

    fun refreshState() {
        overlayGranted = Settings.canDrawOverlays(context)
        accessibilityGranted = AutoClickAccessibilityService.isServiceEnabled(context)
        autoClickPresetCount = app.autoClickStore.getPresets().size
        val triggers = app.autoTriggerStore.getTriggers()
        triggerCount = triggers.size
        enabledTriggerCount = triggers.count { it.enabled }
    }

    LaunchedEffect(Unit) {
        refreshState()
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
            TopAppBar(title = { Text("自动点击") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionStatusSection(
                overlayGranted = overlayGranted,
                accessibilityGranted = accessibilityGranted,
                onOverlayPermission = { openOverlayPermission(context) },
                onAccessibilityPermission = { openAccessibilitySettings(context) }
            )
            ToolEntry(
                title = "连点器",
                description = "多点顺序点击，支持间隔、持续时长和历史配置",
                countLabel = "$autoClickPresetCount 个历史配置",
                onClick = onOpenAutoClick
            )
            ToolEntry(
                title = "触发器",
                description = "监听目标应用事件，自动执行点击、滑动和系统动作",
                countLabel = "$triggerCount 条规则 · $enabledTriggerCount 条启用",
                onClick = onOpenTrigger
            )
        }
    }
}

@Composable
private fun PermissionStatusSection(
    overlayGranted: Boolean,
    accessibilityGranted: Boolean,
    onOverlayPermission: () -> Unit,
    onAccessibilityPermission: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "权限状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PermissionStatusCard(
                    label = "悬浮窗权限",
                    value = if (overlayGranted) "已授权" else "点击授权",
                    highlighted = overlayGranted,
                    onClick = onOverlayPermission,
                    modifier = Modifier.weight(1f)
                )
                PermissionStatusCard(
                    label = "无障碍权限",
                    value = if (accessibilityGranted) "已开启" else "点击开启",
                    highlighted = accessibilityGranted,
                    onClick = onAccessibilityPermission,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    label: String,
    value: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
private fun ToolEntry(
    title: String,
    description: String,
    countLabel: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = countLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
