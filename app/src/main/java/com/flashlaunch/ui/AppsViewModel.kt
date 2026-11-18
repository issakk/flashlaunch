package com.flashlaunch.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flashlaunch.data.AppEntry
import com.flashlaunch.data.AppGroup
import com.flashlaunch.data.AppRepository
import com.flashlaunch.data.AppSnapshot
import com.flashlaunch.data.Prefs
import com.flashlaunch.search.Matcher
import com.flashlaunch.system.PrivilegeMode
import com.flashlaunch.system.PrivilegeStatus
import com.flashlaunch.system.Privileged
import com.flashlaunch.system.RootShell
import com.flashlaunch.system.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class UiState(
    val allApps: List<AppEntry> = emptyList(),
    val results: List<AppEntry> = emptyList(),
    /** effective: individually hidden + members of enabled groups */
    val hidden: Set<String> = emptySet(),
    val individualHidden: Set<String> = emptySet(),
    val groups: List<AppGroup> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val showFrozen: Boolean = true,
    val panelHeight: Int = 60,
    val gridColumns: Int = 5,
    val iconScalePercent: Int = 100,
    val keypadPercent: Int = 45,
    /** Rank the idle grid by launch frequency, decayed by how long ago it was used. */
    val sortByUsage: Boolean = true,
    val keyVibration: Boolean = true,
    val lastLaunchedAt: Long = 0L,
    val privilege: PrivilegeStatus = PrivilegeStatus(),
    val message: String? = null,
) {
    val hiddenCount: Int get() = hidden.size
    val frozenCount: Int get() = allApps.count { it.isFrozen }
    val canFreeze: Boolean get() = privilege.canFreeze
}

class AppsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AppRepository(app)
    private val prefs = Prefs(app)

    /** Last loaded app list; only replaced when something actually changed. */
    private var loaded: List<AppEntry> = emptyList()
    private var loadedVersion = 0
    private var usageVersion = 0

    /** Filtered + sorted list used for an empty query (cached). */
    private var base: List<AppEntry> = emptyList()
    private var baseKey: BaseKey? = null

    /** In-flight scoring job, cancelled when a newer keystroke arrives. */
    private var searchJob: Job? = null

    /** Serialised refresh: package broadcasts like to arrive in bursts. */
    private var refreshJob: Job? = null
    private var refreshPending = false

    /** When the last full scan finished, so a cold start does not scan twice. */
    private var lastLoadAt = 0L

    /** Cached list from the previous run, so the first frame already has icons. */
    private val cachedApps: List<AppEntry> =
        runCatching { AppSnapshot.load(app) }.getOrElse { emptyList() }

    private val _state = MutableStateFlow(
        UiState(
            allApps = cachedApps,
            results = cachedApps,
            loading = cachedApps.isEmpty(),
            showFrozen = prefs.showFrozen,
            hidden = prefs.effectiveHidden(),
            individualHidden = prefs.hiddenApps(),
            groups = prefs.groups(),
            panelHeight = prefs.panelHeight,
            gridColumns = prefs.gridColumns,
            iconScalePercent = prefs.iconScalePercent,
            keypadPercent = prefs.keypadPercent,
            sortByUsage = prefs.sortByUsage,
            keyVibration = prefs.keyVibration,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // An install/update changes the icon itself, not just the label.
            intent?.data?.schemeSpecificPart?.takeIf { it.isNotEmpty() }?.let(IconCache::invalidate)
            refresh(silent = true)
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        _state.update {
            it.copy(message = if (granted) "Shizuku 已授权" else "Shizuku 授权被拒绝")
        }
        refreshPrivilege()
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshPrivilege()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshPrivilege()
    }

    init {
        if (cachedApps.isNotEmpty()) {
            loaded = cachedApps
            recompute()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        try {
            ContextCompat.registerReceiver(
                app,
                packageReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (t: Throwable) {
            // ignore: package broadcasts are a convenience, not a requirement
        }
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        } catch (t: Throwable) {
            // Shizuku not installed: every other Shizuku call is guarded anyway
        }
        // with a cached list there is nothing to wait for: keep the grid visible
        refresh(silent = cachedApps.isNotEmpty())
        refreshPrivilege()
    }

    override fun onCleared() {
        try {
            getApplication<Application>().unregisterReceiver(packageReceiver)
        } catch (t: Throwable) {
            // already gone
        }
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (t: Throwable) {
            // nothing registered
        }
        super.onCleared()
    }

    /** Called every time the panel opens: a launcher should not remember the last query. */
    fun onPanelOpened() {
        val hadQuery = _state.value.query.isNotEmpty()
        // a message queued as the previous panel closed should not pop up later
        _state.update { it.copy(query = "", message = null) }
        if (hadQuery) recompute()
    }

    fun onResume() {
        // A cold start already scanned, so do not scan twice in the same breath.
        val fresh = SystemClock.elapsedRealtime() - lastLoadAt < FRESH_SCAN_MS
        if (loaded.isNotEmpty() && !fresh) refresh(silent = true)
        refreshPrivilege()
    }

    fun refresh(silent: Boolean = true) {
        // Coalesce bursts: one scan runs at a time, a second pass is queued
        // instead of two PackageManager scans racing each other.
        if (refreshJob?.isActive == true) {
            refreshPending = true
            return
        }
        refreshJob = viewModelScope.launch {
            do {
                refreshPending = false
                loadAndApply(silent)
            } while (refreshPending)
        }
    }

    private suspend fun loadAndApply(silent: Boolean) {
        lastLoadAt = SystemClock.elapsedRealtime()
        if (!silent) _state.update { it.copy(loading = true) }
        val apps = withContext(Dispatchers.IO) {
            runCatching { repository.load() }.getOrElse { emptyList() }
        }
        // A failed scan must not wipe a grid we already have.
        if (apps.isEmpty() && loaded.isNotEmpty()) {
            _state.update { it.copy(loading = false) }
            return
        }
        val hidden = prefs.effectiveHidden()
        val individual = prefs.hiddenApps()
        val groups = prefs.groups()
        val current = _state.value
        val changed = !sameContent(apps, loaded)

        // Nothing moved: skip the state update so the grid is not recomposed.
        if (!changed &&
            current.hidden == hidden &&
            current.individualHidden == individual &&
            current.groups == groups &&
            current.showFrozen == prefs.showFrozen
        ) {
            if (current.loading) _state.update { it.copy(loading = false) }
            return
        }

        if (changed) {
            loaded = apps
            loadedVersion++
            withContext(Dispatchers.IO) { AppSnapshot.save(getApplication(), apps) }
        }
        _state.update {
            it.copy(
                allApps = apps,
                loading = false,
                showFrozen = prefs.showFrozen,
                hidden = hidden,
                individualHidden = individual,
                groups = groups,
            )
        }
        recompute()
    }

    fun refreshPrivilege(force: Boolean = false) {
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) {
                if (force) RootShell.resetCache()
                Privileged.status(getApplication())
            }
            _state.update { it.copy(privilege = status) }
        }
    }

    fun setQuery(query: String) {
        if (query == _state.value.query) return
        _state.update { it.copy(query = query) }
        recompute()
    }

    fun setShowFrozen(value: Boolean) {
        prefs.showFrozen = value
        _state.update { it.copy(showFrozen = value) }
        recompute()
    }

    fun clearHidden() {
        prefs.clearHidden()
        _state.update {
            it.copy(
                hidden = prefs.effectiveHidden(),
                individualHidden = emptySet(),
                message = "已恢复全部隐藏应用",
            )
        }
        recompute()
    }

    // ---- display settings ----
    // Sliders call these with persist = false while dragging, so a drag does not
    // write SharedPreferences on every frame; one write lands on release.

    fun setPanelHeight(percent: Int, persist: Boolean = true) {
        val value = percent.coerceIn(Prefs.MIN_PANEL_HEIGHT, Prefs.MAX_PANEL_HEIGHT)
        if (persist) prefs.panelHeight = value
        _state.update { it.copy(panelHeight = value) }
    }

    fun setGridColumns(columns: Int, persist: Boolean = true) {
        val value = columns.coerceIn(Prefs.MIN_GRID_COLUMNS, Prefs.MAX_GRID_COLUMNS)
        if (persist) prefs.gridColumns = value
        _state.update { it.copy(gridColumns = value) }
    }

    fun setIconScalePercent(percent: Int, persist: Boolean = true) {
        val value = percent.coerceIn(Prefs.MIN_ICON_SCALE, Prefs.MAX_ICON_SCALE)
        if (persist) prefs.iconScalePercent = value
        _state.update { it.copy(iconScalePercent = value) }
    }

    fun setKeypadPercent(percent: Int, persist: Boolean = true) {
        val value = percent.coerceIn(Prefs.MIN_KEYPAD_PERCENT, Prefs.MAX_KEYPAD_PERCENT)
        if (persist) prefs.keypadPercent = value
        _state.update { it.copy(keypadPercent = value) }
    }

    /** Called when a slider drag ends: persists whatever was previewed. */
    fun persistDisplaySettings() {
        val snapshot = _state.value
        prefs.panelHeight = snapshot.panelHeight
        prefs.gridColumns = snapshot.gridColumns
        prefs.iconScalePercent = snapshot.iconScalePercent
        prefs.keypadPercent = snapshot.keypadPercent
    }

    fun setSortByUsage(value: Boolean) {
        prefs.sortByUsage = value
        _state.update { it.copy(sortByUsage = value) }
        recompute()
    }

    fun clearUsage() {
        prefs.clearLaunchCounts()
        usageVersion++
        _state.update { it.copy(message = "使用记录已清空") }
        recompute()
    }

    fun setKeyVibration(value: Boolean) {
        prefs.keyVibration = value
        _state.update { it.copy(keyVibration = value) }
    }

    fun consumeLaunchSignal() {
        _state.update { it.copy(lastLaunchedAt = 0L) }
    }

    fun toggleHidden(entry: AppEntry) {
        toggleHiddenByPackage(entry.packageName)
    }

    fun toggleHiddenByPackage(pkg: String) {
        val hidden = prefs.hiddenApps()
        val nowHidden = !hidden.contains(pkg)
        prefs.setHidden(pkg, nowHidden)
        val label = loaded.firstOrNull { it.packageName == pkg }?.label ?: pkg
        _state.update {
            it.copy(
                hidden = prefs.effectiveHidden(),
                individualHidden = prefs.hiddenApps(),
                message = if (nowHidden) "已隐藏 $label" else "已恢复 $label",
            )
        }
        recompute()
    }

    fun setGroupEnabled(name: String, enabled: Boolean) {
        val groups = prefs.groups().map { if (it.name == name) it.copy(enabled = enabled) else it }
        prefs.saveGroups(groups)
        applyGroups(groups, message = if (enabled) "分组「$name」已隐藏" else "分组「$name」已显示")
    }

    fun saveGroup(original: AppGroup?, group: AppGroup) {
        val list = prefs.groups().toMutableList()
        val index = if (original != null) list.indexOfFirst { it.name == original.name } else -1
        if (index >= 0) list[index] = group else list.add(group)
        prefs.saveGroups(list)
        applyGroups(list, message = "分组「${group.name}」已保存")
    }

    fun deleteGroup(name: String) {
        val groups = prefs.groups().filterNot { it.name == name }
        prefs.saveGroups(groups)
        applyGroups(groups, message = "分组「$name」已删除")
    }

    private fun applyGroups(groups: List<AppGroup>, message: String) {
        _state.update {
            it.copy(groups = groups, hidden = prefs.effectiveHidden(), message = message)
        }
        recompute()
    }

    fun openApp(entry: AppEntry) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            if (entry.isFrozen) {
                val unfrozen = withContext(Dispatchers.IO) { Privileged.unfreeze(entry.packageName) }
                if (!unfrozen) {
                    _state.update {
                        it.copy(message = "解冻 ${entry.label} 需要 Root 或 Shizuku 授权")
                    }
                    return@launch
                }
                setFrozenLocally(entry.packageName, frozen = false)
                // give PackageManager a beat to publish the enabled component
                delay(250)
            }
            val launched = startEntry(ctx, entry)
            if (launched) {
                // usage stat drives the default ordering next time the panel opens
                prefs.recordLaunch(entry.packageName)
                usageVersion++
                recompute()
                _state.update { it.copy(lastLaunchedAt = System.currentTimeMillis()) }
            } else {
                _state.update { it.copy(message = "无法打开 ${entry.label}") }
            }
        }
    }

    fun setFrozen(entry: AppEntry, frozen: Boolean) {
        if (entry.isFrozen == frozen) return
        // Optimistic: flip the grid immediately, run `pm` afterwards. A package
        // broadcast (or the delayed refresh below) reconciles with reality.
        setFrozenLocally(entry.packageName, frozen)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (frozen) {
                    Privileged.freeze(entry.packageName)
                } else {
                    Privileged.unfreeze(entry.packageName)
                }
            }
            if (!ok) {
                setFrozenLocally(entry.packageName, !frozen)
                _state.update {
                    it.copy(
                        message = if (frozen) {
                            "冻结 ${entry.label} 需要 Root 或 Shizuku 授权"
                        } else {
                            "解冻 ${entry.label} 需要 Root 或 Shizuku 授权"
                        }
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(message = if (frozen) "已冻结 ${entry.label}" else "已解冻 ${entry.label}")
            }
            delay(400)
            refresh(silent = true)
        }
    }

    /** Flips one entry in place so freeze/unfreeze shows up without a rescan. */
    private fun setFrozenLocally(packageName: String, frozen: Boolean) {
        if (loaded.none { it.packageName == packageName }) return
        val updated = loaded.map {
            if (it.packageName == packageName) it.copy(isFrozen = frozen) else it
        }
        loaded = updated
        loadedVersion++
        _state.update { it.copy(allApps = updated) }
        recompute()
        viewModelScope.launch(Dispatchers.IO) { AppSnapshot.save(getApplication(), updated) }
    }

    /** Asks Shizuku for its permission (shows the Shizuku grant dialog). */
    fun requestShizukuPermission() {
        val privilege = _state.value.privilege
        when {
            privilege.shizukuGranted -> _state.update { it.copy(message = "Shizuku 已授权") }
            !privilege.shizukuRunning -> _state.update {
                it.copy(message = "Shizuku 服务未运行，请先打开 Shizuku")
            }
            else -> ShizukuShell.requestPermission()
        }
    }

    fun checkPrivilege() {
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) {
                RootShell.resetCache()
                Privileged.status(getApplication())
            }
            _state.update {
                it.copy(
                    privilege = status,
                    message = when (status.mode) {
                        PrivilegeMode.SHIZUKU -> "已通过 Shizuku 获得权限"
                        PrivilegeMode.ROOT -> "已通过 Root 获得权限"
                        PrivilegeMode.NONE -> "未获得 Root 或 Shizuku 授权"
                    }
                )
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /** Rebuilds the cached filtered/sorted list only when its inputs changed. */
    private fun ensureBase(snapshot: UiState, scores: Map<String, Double>) {
        val key = BaseKey(
            showFrozen = snapshot.showFrozen,
            hidden = snapshot.hidden,
            loadedVersion = loadedVersion,
            sortByUsage = snapshot.sortByUsage,
            usageVersion = usageVersion,
        )
        if (key == baseKey) return
        baseKey = key

        val visible = loaded.filter { entry ->
            (snapshot.showFrozen || !entry.isFrozen) &&
                !snapshot.hidden.contains(entry.packageName)
        }
        base = if (snapshot.sortByUsage) {
            visible.sortedWith(
                compareByDescending<AppEntry> { scores[it.packageName] ?: 0.0 }
                    .thenBy { it.sortKey }
                    .thenBy { it.label }
            )
        } else {
            visible.sortedWith(compareBy<AppEntry> { it.sortKey }.thenBy { it.label })
        }
    }

    private fun recompute() {
        val snapshot = _state.value
        val query = Matcher.normalize(snapshot.query)
        val scores = if (snapshot.sortByUsage) prefs.usageScores() else emptyMap()
        ensureBase(snapshot, scores)

        searchJob?.cancel()
        if (query.isEmpty()) {
            _state.update { it.copy(results = base) }
            return
        }

        val source = base
        searchJob = viewModelScope.launch {
            // scoring runs off the main thread; a newer keystroke cancels it
            val found = withContext(Dispatchers.Default) { search(source, query, scores) }
            _state.update { state ->
                if (Matcher.normalize(state.query) == query) state.copy(results = found) else state
            }
        }
    }

    private fun search(
        source: List<AppEntry>,
        query: String,
        scores: Map<String, Double>,
    ): List<AppEntry> =
        source.asSequence()
            .map { it to Matcher.score(it, query) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<AppEntry, Int>> { it.second }
                    .thenByDescending { scores[it.first.packageName] ?: 0.0 }
                    .thenBy { it.first.label }
            )
            .map { it.first }
            .take(MAX_RESULTS)
            .toList()

    private fun sameContent(a: List<AppEntry>, b: List<AppEntry>): Boolean {
        if (a === b) return true
        if (a.size != b.size) return false
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x.packageName != y.packageName ||
                x.activityName != y.activityName ||
                x.label != y.label ||
                x.isFrozen != y.isFrozen
            ) {
                return false
            }
        }
        return true
    }

    private fun startEntry(ctx: Context, entry: AppEntry): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(entry.packageName, entry.activityName))
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
        try {
            ctx.startActivity(intent)
            return true
        } catch (t: Throwable) {
            // fall through to the package's default launch intent
        }
        val fallback = try {
            ctx.packageManager.getLaunchIntentForPackage(entry.packageName)
        } catch (t: Throwable) {
            null
        } ?: return false
        return try {
            ctx.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** Everything the idle grid's sort order depends on. */
    private data class BaseKey(
        val showFrozen: Boolean,
        val hidden: Set<String>,
        val loadedVersion: Int,
        val sortByUsage: Boolean,
        val usageVersion: Int,
    )

    private companion object {
        const val MAX_RESULTS = 200
        const val FRESH_SCAN_MS = 800L
    }
}
