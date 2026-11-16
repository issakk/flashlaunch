package com.flashlaunch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flashlaunch.data.Prefs
import com.flashlaunch.system.PrivilegeStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppsViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item { SectionTitle("显示") }
            item {
                SettingSwitch(
                    title = "显示已冻结的应用",
                    subtitle = "冻结项带圆点标记，点击会先解冻再打开",
                    checked = state.showFrozen,
                    onCheckedChange = viewModel::setShowFrozen,
                )
            }
            item {
                SettingSwitch(
                    title = "显示已隐藏的应用",
                    subtitle = "临时显示隐藏项，方便恢复",
                    checked = state.showHidden,
                    onCheckedChange = viewModel::setShowHidden,
                )
            }

            item { SectionTitle("面板") }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "启动面板高度", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${state.panelHeight}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = state.panelHeight.toFloat(),
                        onValueChange = { viewModel.setPanelHeight(it.toInt()) },
                        valueRange = Prefs.MIN_PANEL_HEIGHT.toFloat()..Prefs.MAX_PANEL_HEIGHT.toFloat(),
                        steps = 11,
                    )
                    Text(
                        text = "改完立即生效；面板外的区域仍属于桌面，按返回键退出面板",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingSwitch(
                    title = "按键振动",
                    subtitle = "按九宫格按键时给出触感反馈",
                    checked = state.keyVibration,
                    onCheckedChange = viewModel::setKeyVibration,
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "每行图标数", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${state.gridColumns} 列",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = state.gridColumns.toFloat(),
                        onValueChange = { viewModel.setGridColumns(it.toInt()) },
                        valueRange = Prefs.MIN_GRID_COLUMNS.toFloat()..Prefs.MAX_GRID_COLUMNS.toFloat(),
                        steps = 4,
                    )
                    Text(
                        text = "3~8 列，图标会随列数自动缩放",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionTitle("权限") }
            item {
                ShizukuRow(
                    privilege = state.privilege,
                    onRequest = viewModel::requestShizukuPermission,
                    onRefresh = { viewModel.refreshPrivilege(force = true) },
                )
            }
            item {
                SettingRow(
                    title = "Root",
                    subtitle = if (state.privilege.rootAvailable) "su 可用" else "未检测到 su",
                    action = "重新检测",
                    onAction = viewModel::checkPrivilege,
                )
            }
            item { HintRow("冻结 / 解冻优先走 Shizuku，没有 Shizuku 授权时回退到 Root。") }

            item { SectionTitle("已隐藏的应用（${state.hiddenCount}）") }
            if (state.hidden.isEmpty()) {
                item { HintRow("还没有隐藏任何应用。长按应用图标即可隐藏。") }
            } else {
                val byPackage = state.allApps.associateBy { it.packageName }
                items(items = state.hidden.sorted(), key = { it }) { pkg ->
                    val entry = byPackage[pkg]
                    HiddenRow(
                        label = entry?.label ?: pkg,
                        packageName = pkg,
                        onRestore = { viewModel.toggleHiddenByPackage(pkg) },
                    )
                }
            }

            item { SectionTitle("关于") }
            item {
                HintRow("闪启 FlashLaunch · 本地极速应用启动器\n不联网、不上传，所有数据保存在本机。")
            }
        }
    }
}

@Composable
private fun ShizukuRow(
    privilege: PrivilegeStatus,
    onRequest: () -> Unit,
    onRefresh: () -> Unit,
) {
    val (subtitle, action) = when {
        privilege.shizukuGranted -> "已授权，冻结 / 解冻走 Shizuku" to "重新检测"
        privilege.shizukuRunning -> "服务已运行，尚未授权本应用" to "授权"
        privilege.shizukuInstalled -> "已安装，但服务未运行" to "重新检测"
        else -> "未安装 Shizuku（需先安装并启动服务）" to "重新检测"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = "Shizuku", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = if (privilege.shizukuRunning && !privilege.shizukuGranted) onRequest else onRefresh) {
            Text(action)
        }
    }
}


@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun HiddenRow(label: String, packageName: String, onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onRestore) { Text("恢复") }
    }
}

@Composable
private fun HintRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
