package com.flashlaunch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flashlaunch.data.AppEntry
import com.flashlaunch.data.AppGroup
import com.flashlaunch.search.Matcher

private data class GroupEditorState(val target: AppGroup?)

/** Hidden apps, split into toggleable groups plus individually hidden ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenScreen(viewModel: AppsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<GroupEditorState?>(null) }
    val groups = state.groups
    val current = editor

    if (current != null) {
        GroupEditor(
            viewModel = viewModel,
            initial = current.target,
            allApps = state.allApps,
            onClose = { editor = null },
        )
        return
    }

    BackHandler(onBack = onBack)

    val individual = remember(state.allApps, state.individualHidden) {
        state.allApps
            .filter { state.individualHidden.contains(it.packageName) }
            .sortedWith(compareBy({ it.sortKey }, { it.label }))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已隐藏的应用") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (individual.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearHidden) { Text("全部恢复") }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item { SectionHeader("分组") }
            if (groups.isEmpty()) {
                item {
                    Hint("把一批应用放进同一个分组，就可以一键隐藏/显示整组。")
                }
            }
            items(items = groups, key = { it.name }) { group ->
                GroupRow(
                    group = group,
                    onToggle = { viewModel.setGroupEnabled(group.name, it) },
                    onEdit = { editor = GroupEditorState(group) },
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editor = GroupEditorState(null) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "＋ 新建分组",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item { SectionHeader("单个隐藏（${individual.size}）") }
            if (individual.isEmpty()) {
                item { Hint("还没有单独隐藏的应用。在主界面长按图标即可隐藏。") }
            }
            items(items = individual, key = { it.packageName }) { entry ->
                HiddenAppRow(
                    entry = entry,
                    onOpen = { viewModel.openApp(entry) },
                    onRestore = { viewModel.toggleHiddenByPackage(entry.packageName) },
                )
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: AppGroup,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${group.packages.size} 个应用 · " + if (group.enabled) "隐藏中" else "显示中",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = group.enabled, onCheckedChange = onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupEditor(
    viewModel: AppsViewModel,
    initial: AppGroup?,
    allApps: List<AppEntry>,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var filter by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initial?.packages ?: emptySet()) }

    BackHandler(onBack = onClose)

    val list = remember(allApps, filter) {
        if (filter.isBlank()) {
            allApps.sortedWith(compareBy({ it.sortKey }, { it.label }))
        } else {
            val query = Matcher.normalize(filter)
            allApps.asSequence()
                .map { it to Matcher.score(it, query) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { it.first }
                .toList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "新建分组" else "编辑分组") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            viewModel.saveGroup(
                                original = initial,
                                group = AppGroup(
                                    name = name.trim(),
                                    enabled = initial?.enabled ?: true,
                                    packages = selected,
                                ),
                            )
                            onClose()
                        },
                    ) { Text("保存") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分组名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("筛选应用") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已选 ${selected.size} 个",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (initial != null) {
                    TextButton(
                        onClick = {
                            viewModel.deleteGroup(initial.name)
                            onClose()
                        },
                    ) {
                        Text("删除分组", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = list, key = { it.packageName }) { app ->
                    PickerRow(
                        entry = app,
                        checked = selected.contains(app.packageName),
                        onToggle = {
                            selected = if (selected.contains(app.packageName)) {
                                selected - app.packageName
                            } else {
                                selected + app.packageName
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(entry: AppEntry, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(4.dp))
        AppIcon(entry = entry, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun HiddenAppRow(entry: AppEntry, onOpen: () -> Unit, onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(entry = entry, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRestore) { Text("恢复") }
    }
}
