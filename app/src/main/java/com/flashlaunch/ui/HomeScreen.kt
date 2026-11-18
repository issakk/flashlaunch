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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flashlaunch.R
import com.flashlaunch.data.AppEntry
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(viewModel: AppsViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var sheetApp by remember { mutableStateOf<AppEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gridState = rememberLazyGridState()

    // every new keystroke should start from the best match, not from wherever
    // the previous result set happened to be scrolled to
    LaunchedEffect(state.query) {
        gridState.scrollToItem(0)
    }

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
        when (screen) {
            AppScreen.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                onClose = { screen = AppScreen.HOME },
                onOpenHidden = { screen = AppScreen.HIDDEN },
            )

            AppScreen.HIDDEN -> HiddenScreen(
                viewModel = viewModel,
                onBack = { screen = AppScreen.SETTINGS },
            )

            AppScreen.HOME -> Column(Modifier.fillMaxSize()) {

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.loading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }

                        state.results.isEmpty() -> EmptyState(query = state.query)

                        // The grid stays a grid while searching; only the rows change.
                        else -> {
                            Column(Modifier.fillMaxSize()) {
                                AppsHeader(state)
                                AppGrid(
                                    apps = state.results,
                                    columns = state.gridColumns,
                                    iconScale = state.iconScalePercent,
                                    gridState = gridState,
                                    onClick = viewModel::openApp,
                                    onLongClick = { sheetApp = it },
                                )
                            }
                        }
                    }
                }

                NineKeyPad(
                    modifier = Modifier.fillMaxHeight(state.keypadPercent / 100f),
                    vibrate = state.keyVibration,
                    canOpenFirst = state.results.isNotEmpty(),
                    onDigit = { digit -> viewModel.setQuery(state.query + digit) },
                    onBackspace = { viewModel.setQuery(state.query.dropLast(1)) },
                    onClear = { viewModel.setQuery("") },
                    onOpenFirst = { state.results.firstOrNull()?.let(viewModel::openApp) },
                    onSettings = { screen = AppScreen.SETTINGS },
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


private enum class AppScreen { HOME, SETTINGS, HIDDEN }

/** Long lists get a scrubber; short ones would just have a useless sliver. */
private const val FAST_SCROLL_MIN_ITEMS = 40

/** Rows decoded ahead of the visible ones. */
private const val PREFETCH_ROWS = 3

private sealed interface PadKey {
    data class Digit(val value: String, val letters: String = "") : PadKey
    object Settings : PadKey
    object OpenFirst : PadKey
    object Backspace : PadKey
    object Clear : PadKey
    object Close : PadKey
}

private val PAD_ROWS: List<List<PadKey>> = listOf(
    listOf(PadKey.Settings, PadKey.Close, PadKey.Backspace),
    listOf(PadKey.Digit("1", "-"), PadKey.Digit("2", "ABC"), PadKey.Digit("3", "DEF")),
    listOf(PadKey.Digit("4", "GHI"), PadKey.Digit("5", "JKL"), PadKey.Digit("6", "MNO")),
    listOf(PadKey.Digit("7", "PQRS"), PadKey.Digit("8", "TUV"), PadKey.Digit("9", "WXYZ")),
    listOf(PadKey.Clear, PadKey.Digit("0"), PadKey.OpenFirst),
)

/** Phone-style T9 keypad with a control row for settings / launch-first / backspace. */
@Composable
private fun NineKeyPad(
    modifier: Modifier = Modifier,
    vibrate: Boolean,
    canOpenFirst: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onOpenFirst: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val rows = PAD_ROWS.size
    val gap = 5.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 8.dp),
    ) {
        // font sizes follow the actual key height, so nothing gets clipped
        // at any keypad percentage
        val keyHeight = ((maxHeight - gap * (rows - 1)) / rows).coerceAtLeast(18.dp)
        val mainSize = (keyHeight.value * 0.34f).coerceIn(11f, 24f).sp
        val subSize = (keyHeight.value * 0.22f).coerceIn(7f, 13f).sp

        Column(modifier = Modifier.fillMaxSize()) {
            PAD_ROWS.forEachIndexed { index, row ->
                if (index > 0) Spacer(Modifier.height(gap))
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { key ->
                        PadButton(
                            key = key,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            vibrate = vibrate,
                            enabled = !(key is PadKey.OpenFirst && !canOpenFirst),
                            mainSize = mainSize,
                            subSize = subSize,
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
}

@Composable
private fun PadButton(
    key: PadKey,
    modifier: Modifier,
    vibrate: Boolean,
    enabled: Boolean = true,
    mainSize: TextUnit,
    subSize: TextUnit,
    onClick: () -> Unit,
) {
    val view = LocalView.current

    val label = when (key) {
        is PadKey.Digit -> key.value
        PadKey.Settings -> "⚙"
        PadKey.OpenFirst -> "➤"
        PadKey.Backspace -> "⌫"
        PadKey.Clear -> "✕"
        PadKey.Close -> "⌄"
    }
    val sub = when (key) {
        is PadKey.Digit -> key.letters
        PadKey.Settings -> "设置"
        PadKey.OpenFirst -> "打开"
        PadKey.Backspace -> "退格"
        PadKey.Clear -> "清空"
        PadKey.Close -> "收起"
    }
    val isAction = key !is PadKey.Digit

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f))
            .clickable(enabled = enabled) {
                if (vibrate) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                fontSize = mainSize,
                lineHeight = mainSize * 1.15f,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    isAction -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    fontSize = subSize,
                    lineHeight = subSize * 1.15f,
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
        if (state.query.isBlank()) {
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
        } else {
            Text(
                text = state.query,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${state.results.size} 个匹配",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "长按管理",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun EmptyState(query: String) {
    val hasQuery = query.isNotBlank()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasQuery) "没有匹配「$query」的应用" else "没有可显示的应用",
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
    iconScale: Int,
    gridState: LazyGridState,
    onClick: (AppEntry) -> Unit,
    onLongClick: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cellWidth = maxWidth / columns
        val baseSize = when {
            columns <= 4 -> 56.dp
            columns == 5 -> 46.dp
            columns == 6 -> 40.dp
            else -> 34.dp
        }
        // never let a scaled icon overflow its cell
        val iconSize = (baseSize * (iconScale / 100f))
            .coerceAtMost(cellWidth - 6.dp)
            .coerceAtLeast(18.dp)
        val iconPx = with(LocalDensity.current) { iconSize.roundToPx() }
        val showScroller = apps.size >= FAST_SCROLL_MIN_ITEMS

        // warm the next screens once the icons on screen have had their turn
        LaunchedEffect(apps, iconPx) {
            IconCache.prefetch(context, apps.take(columns * PREFETCH_ROWS), iconPx)
        }

        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 6.dp,
                    end = if (showScroller) 18.dp else 6.dp,
                    top = 2.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items = apps, key = { it.packageName }) { entry ->
                    AppGridItem(entry, iconSize, onClick, onLongClick)
                }
            }
            if (showScroller) {
                FastScroller(
                    itemCount = apps.size,
                    state = gridState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Thumb-style scrubber for long grids: drag anywhere on the rail to jump.
 * Drawn only when the list is long enough that repeated flinging gets tedious.
 */
@Composable
private fun FastScroller(
    itemCount: Int,
    state: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val thumbHeight = 44.dp
    val thumbPx = with(LocalDensity.current) { thumbHeight.roundToPx() }
    var railPx by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }

    val progress by remember(state, itemCount) {
        derivedStateOf {
            val last = (itemCount - 1).coerceAtLeast(1)
            (state.firstVisibleItemIndex.toFloat() / last).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = modifier
            .width(18.dp)
            .onSizeChanged { railPx = it.height }
            .pointerInput(itemCount, railPx, thumbPx) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        scrollToFraction(offset.y, railPx, thumbPx, itemCount, state)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    scrollToFraction(change.position.y, railPx, thumbPx, itemCount, state)
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .width(if (dragging) 5.dp else 3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(
                    MaterialTheme.colorScheme.onSurface
                        .copy(alpha = if (dragging) 0.24f else 0.10f)
                ),
        )
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(0, ((railPx - thumbPx) * progress).roundToInt().coerceAtLeast(0))
                }
                .width(if (dragging) 8.dp else 5.dp)
                .height(thumbHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    MaterialTheme.colorScheme.primary
                        .copy(alpha = if (dragging) 0.9f else 0.5f)
                ),
        )
    }
}

private fun scrollToFraction(
    y: Float,
    railPx: Int,
    thumbPx: Int,
    itemCount: Int,
    state: LazyGridState,
) {
    if (railPx <= 0 || itemCount <= 0) return
    val travel = (railPx - thumbPx).coerceAtLeast(1)
    val fraction = ((y - thumbPx / 2f) / travel).coerceIn(0f, 1f)
    state.requestScrollToItem((fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1))
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
    val px = with(LocalDensity.current) { size.roundToPx() }
    var image by remember(entry.packageName, entry.activityName, px) {
        mutableStateOf<ImageBitmap?>(IconCache.peek(entry, px))
    }

    LaunchedEffect(entry.packageName, entry.activityName, px) {
        if (image == null) {
            image = withContext(IconCache.decodeDispatcher) { IconCache.load(context, entry, px) }
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
        LetterTile(entry = entry, size = size, modifier = modifier)
    }
}

/** Stand-in while the icon decodes: a per-app tinted tile with the first letter. */
@Composable
private fun LetterTile(entry: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val hue = ((entry.packageName.hashCode() % 360 + 360) % 360).toFloat()
    val tile = if (dark) Color.hsl(hue, 0.30f, 0.26f) else Color.hsl(hue, 0.45f, 0.88f)
    val ink = if (dark) Color.hsl(hue, 0.25f, 0.80f) else Color.hsl(hue, 0.40f, 0.32f)
    val letter = entry.label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 25))
            .background(tile),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = ink,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
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
