package com.flashlaunch.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flashlaunch.R
import com.flashlaunch.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(viewModel: AppsViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var sheetApp by remember { mutableStateOf<AppEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        if (settingsOpen) {
            SettingsScreen(viewModel = viewModel, onClose = { settingsOpen = false })
        } else {
            Column(Modifier.fillMaxSize()) {
                QueryBar(
                    query = state.query,
                    onClear = { viewModel.setQuery("") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.loading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }

                        state.results.isEmpty() -> EmptyState(hasQuery = state.query.isNotBlank())

                        // The grid stays a grid while searching; only the rows change.
                        else -> {
                            Column(Modifier.fillMaxSize()) {
                                AppsHeader(state)
                                AppGrid(
                                    apps = state.results,
                                    columns = state.gridColumns,
                                    onClick = viewModel::openApp,
                                    onLongClick = { sheetApp = it },
                                )
                            }
                        }
                    }
                }

                NineKeyPad(
                    vibrate = state.keyVibration,
                    canOpenFirst = state.results.isNotEmpty(),
                    onDigit = { digit -> viewModel.setQuery(state.query + digit) },
                    onBackspace = { viewModel.setQuery(state.query.dropLast(1)) },
                    onClear = { viewModel.setQuery("") },
                    onOpenFirst = { state.results.firstOrNull()?.let(viewModel::openApp) },
                    onSettings = { settingsOpen = true },
                    onClose = onClose,
                )
            }
        }
    }

    val current = sheetApp
    if (current != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetApp = null },
            sheetState = sheetState,
        ) {
            AppActions(
                entry = current,
                hidden = state.hidden.contains(current.packageName),
                canFreeze = state.canFreeze,
                onOpen = {
                    sheetApp = null
                    viewModel.openApp(current)
                },
                onAppInfo = {
                    sheetApp = null
                    openAppInfo(context, current)
                },
                onToggleHidden = {
                    sheetApp = null
                    viewModel.toggleHidden(current)
                },
                onToggleFrozen = {
                    sheetApp = null
                    viewModel.setFrozen(current, !current.isFrozen)
                },
            )
        }
    }
}

/** Read-only query display: input comes from the T9 pad, not the IME. */
@Composable
private fun QueryBar(
    query: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(23.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = query.ifEmpty { "拼音首字母 / T9 数字，键盘在下方" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (query.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private sealed interface PadKey {
    data class Digit(val value: String, val letters: String = "") : PadKey
    object Settings : PadKey
    object OpenFirst : PadKey
    object Backspace : PadKey
    object Clear : PadKey
    object Close : PadKey
}

private val PAD_ROWS: List<List<PadKey>> = listOf(
    listOf(PadKey.Settings, PadKey.OpenFirst, PadKey.Backspace),
    listOf(PadKey.Digit("1"), PadKey.Digit("2", "abc"), PadKey.Digit("3", "def")),
    listOf(PadKey.Digit("4", "ghi"), PadKey.Digit("5", "jkl"), PadKey.Digit("6", "mno")),
    listOf(PadKey.Digit("7", "pqrs"), PadKey.Digit("8", "tuv"), PadKey.Digit("9", "wxyz")),
    listOf(PadKey.Clear, PadKey.Digit("0"), PadKey.Close),
)

/** Phone-style T9 keypad with a control row for settings / launch-first / backspace. */
@Composable
private fun NineKeyPad(
    vibrate: Boolean,
    canOpenFirst: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onOpenFirst: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 8.dp),
    ) {
        PAD_ROWS.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key ->
                    PadButton(
                        key = key,
                        modifier = Modifier.weight(1f),
                        vibrate = vibrate,
                        enabled = !(key is PadKey.OpenFirst && !canOpenFirst),
                        onClick = {
                            when (key) {
                                is PadKey.Digit -> onDigit(key.value)
                                PadKey.Backspace -> onBackspace()
                                PadKey.Clear -> onClear()
                                PadKey.OpenFirst -> onOpenFirst()
                                PadKey.Settings -> onSettings()
                                PadKey.Close -> onClose()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PadButton(
    key: PadKey,
    modifier: Modifier,
    vibrate: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val view = LocalView.current

    val label = when (key) {
        is PadKey.Digit -> key.value
        PadKey.Settings -> "⚙"
        PadKey.OpenFirst -> "↵"
        PadKey.Backspace -> "⌫"
        PadKey.Clear -> "清空"
        PadKey.Close -> "✕"
    }
    val sub = when (key) {
        is PadKey.Digit -> key.letters
        PadKey.Settings -> "设置"
        PadKey.OpenFirst -> "打开"
        PadKey.Backspace -> "退格"
        PadKey.Clear -> ""
        PadKey.Close -> "关闭"
    }
    val isAction = key !is PadKey.Digit

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) {
                if (vibrate) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                onClick()
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isAction) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            )
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppsHeader(state: UiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildString {
                append(state.results.size)
                append(" 个应用")
                if (state.frozenCount > 0) append(" · 冻结 ${state.frozenCount}")
                if (state.hiddenCount > 0) append(" · 隐藏 ${state.hiddenCount}")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "长按管理",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun EmptyState(hasQuery: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasQuery) "没有匹配的应用" else "没有可显示的应用",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (hasQuery) "换个 T9 数字或首字母试试" else "试试在设置里打开「显示已冻结的应用」",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGrid(
    apps: List<AppEntry>,
    columns: Int,
    onClick: (AppEntry) -> Unit,
    onLongClick: (AppEntry) -> Unit,
) {
    val iconSize = when {
        columns <= 4 -> 56.dp
        columns == 5 -> 46.dp
        columns == 6 -> 40.dp
        else -> 34.dp
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items = apps, key = { it.packageName }) { entry ->
            AppGridItem(entry, iconSize, onClick, onLongClick)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(
    entry: AppEntry,
    iconSize: Dp,
    onClick: (AppEntry) -> Unit,
    onLongClick: (AppEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { onClick(entry) },
                onLongClick = { onLongClick(entry) },
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(entry = entry, size = iconSize)
            if (entry.isFrozen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_snowflake),
                        contentDescription = "已冻结",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}


@Composable
fun AppIcon(entry: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var image by remember(entry.packageName, entry.activityName) {
        mutableStateOf<ImageBitmap?>(IconCache.peek(entry))
    }

    LaunchedEffect(entry.packageName, entry.activityName) {
        if (image == null) {
            image = withContext(Dispatchers.IO) { IconCache.load(context, entry) }
        }
    }

    val bitmap = image
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = entry.label,
            modifier = modifier.size(size),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(percent = 25))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}


@Composable
private fun AppActions(
    entry: AppEntry,
    hidden: Boolean,
    canFreeze: Boolean,
    onOpen: () -> Unit,
    onAppInfo: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleFrozen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(entry = entry, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

        ActionRow(title = "打开", onClick = onOpen)
        ActionRow(title = "应用信息", onClick = onAppInfo)
        ActionRow(title = if (hidden) "取消隐藏" else "隐藏", onClick = onToggleHidden)
        ActionRow(
            title = if (entry.isFrozen) "解冻" else "冻结",
            trailing = if (canFreeze) null else "需要 Root / Shizuku",
            enabled = canFreeze,
            onClick = onToggleFrozen,
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    trailing: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openAppInfo(context: Context, entry: AppEntry) {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${entry.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (t: Throwable) {
        Toast.makeText(context, "无法打开应用信息", Toast.LENGTH_SHORT).show()
    }
}
