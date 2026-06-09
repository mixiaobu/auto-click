package org.xiaobu.autoclick.ui.component

import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.xiaobu.autoclick.data.app.InstalledAppInfo
import org.xiaobu.autoclick.data.app.getAppIcon

enum class AppPickerSelectionMode {
    SINGLE,
    MULTIPLE
}

private enum class AppPickerFilter(val title: String) {
    ALL("全部应用"),
    USER("用户应用"),
    SYSTEM("系统应用")
}

@Composable
fun AppPickerDialog(
    apps: List<InstalledAppInfo>,
    selectedPackageNames: Set<String>,
    selectionMode: AppPickerSelectionMode,
    onDismiss: () -> Unit,
    onConfirm: (List<InstalledAppInfo>) -> Unit,
    title: String = "选择应用"
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppPickerFilter.USER) }
    var filterExpanded by remember { mutableStateOf(false) }
    val initialSelectedPackageSet = remember(selectedPackageNames, selectionMode) {
        val selected = selectedPackageNames.filter { it.isNotBlank() }
        if (selectionMode == AppPickerSelectionMode.SINGLE) {
            selected.take(1).toSet()
        } else {
            selected.toSet()
        }
    }
    var selectedPackageSet by remember(initialSelectedPackageSet) {
        mutableStateOf(initialSelectedPackageSet)
    }
    val filteredApps = remember(apps, keyword, filter, initialSelectedPackageSet) {
        val trimmed = keyword.trim()
        apps
            .filter { app ->
                app.packageName in initialSelectedPackageSet || when (filter) {
                    AppPickerFilter.ALL -> true
                    AppPickerFilter.USER -> !app.isSystemApp
                    AppPickerFilter.SYSTEM -> app.isSystemApp
                }
            }
            .filter { app ->
                trimmed.isBlank() ||
                    app.appLabel.contains(trimmed, ignoreCase = true) ||
                    app.packageName.contains(trimmed, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { it.packageName in initialSelectedPackageSet }
                    .thenBy { it.appLabel.lowercase() }
                    .thenBy { it.packageName }
            )
    }
    val allFilteredSelected = selectionMode == AppPickerSelectionMode.MULTIPLE &&
        filteredApps.isNotEmpty() &&
        filteredApps.all { it.packageName in selectedPackageSet }
    val selectedResult = remember(apps, selectedPackageSet) {
        apps.filter { it.packageName in selectedPackageSet }
    }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Box {
                            OutlinedButton(onClick = { filterExpanded = true }) {
                                Text(filter.title)
                            }
                            DropdownMenu(
                                expanded = filterExpanded,
                                onDismissRequest = { filterExpanded = false }
                            ) {
                                AppPickerFilter.entries.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.title) },
                                        onClick = {
                                            filter = item
                                            filterExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜索应用") },
                        placeholder = { Text("输入应用名或包名") },
                        singleLine = true
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 420.dp)
                    ) {
                        if (filteredApps.isEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "没有找到匹配的应用",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(filteredApps, key = { it.packageName }) { app ->
                                    val selected = app.packageName in selectedPackageSet
                                    Surface(
                                        onClick = {
                                            selectedPackageSet = if (selectionMode == AppPickerSelectionMode.SINGLE) {
                                                setOf(app.packageName)
                                            } else if (selected) {
                                                selectedPackageSet - app.packageName
                                            } else {
                                                selectedPackageSet + app.packageName
                                            }
                                        },
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        } else {
                                            MaterialTheme.colorScheme.background
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (selectionMode == AppPickerSelectionMode.SINGLE) {
                                                RadioButton(selected = selected, onClick = null)
                                            } else {
                                                Checkbox(checked = selected, onCheckedChange = null)
                                            }
                                            AppPickerIcon(packageName = app.packageName)
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = app.appLabel,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = buildAppMetaText(app),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (selectionMode == AppPickerSelectionMode.MULTIPLE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = allFilteredSelected,
                                onCheckedChange = { checked ->
                                    selectedPackageSet = if (checked) {
                                        selectedPackageSet + filteredApps.map { it.packageName }
                                    } else {
                                        selectedPackageSet - filteredApps.map { it.packageName }.toSet()
                                    }
                                }
                            )
                            Text("全选", style = MaterialTheme.typography.bodyMedium)
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
                            onClick = { onConfirm(selectedResult) },
                            enabled = selectionMode == AppPickerSelectionMode.MULTIPLE ||
                                selectedResult.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerIcon(packageName: String) {
    val context = LocalContext.current
    AndroidView(
        factory = { viewContext ->
            ImageView(viewContext).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        },
        update = { imageView -> imageView.setImageDrawable(context.getAppIcon(packageName)) },
        modifier = Modifier.size(30.dp)
    )
}

private fun buildAppMetaText(app: InstalledAppInfo): String {
    val typeText = if (app.isSystemApp) "系统应用" else "用户应用"
    return "$typeText · ${app.packageName}"
}
