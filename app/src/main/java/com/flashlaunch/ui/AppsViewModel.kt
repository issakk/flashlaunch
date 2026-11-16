package com.flashlaunch.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flashlaunch.data.AppEntry
import com.flashlaunch.data.AppRepository
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
    val hidden: Set<String> = emptySet(),
    val query: String = "",
    val loading: Boolean = true,
    val showFrozen: Boolean = true,
    val showHidden: Boolean = false,
    val panelHeight: Int = 60,
    val gridColumns: Int = 5,
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

    /** Filtered + sorted list used for an empty query (cached). */
    private var base: List<AppEntry> = emptyList()
    private var baseKey = ""

    /** In-flight scoring job, cancelled when a newer keystroke arrives. */
    private var searchJob: Job? = null

    private val _state = MutableStateFlow(
        UiState(
            showFrozen = prefs.showFrozen,
            showHidden = prefs.showHidden,
            hidden = prefs.hiddenApps(),
            panelHeight = prefs.panelHeight,
            gridColumns = prefs.gridColumns,
            keyVibration = prefs.keyVibration,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
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
        refresh(silent = false)
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

    fun onResume() {
        if (loaded.isNotEmpty()) refresh(silent = true)
        refreshPrivilege()
    }

    fun refresh(silent: Boolean = true) {
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(loading = true) }
            val apps = withContext(Dispatchers.IO) {
                runCatching { repository.load() }.getOrElse { emptyList() }
            }
            val hidden = prefs.hiddenApps()
            val current = _state.value

            // Nothing moved: skip the state update so the grid is not recomposed.
            if (sameContent(apps, loaded) &&
                current.hidden == hidden &&
                current.showFrozen == prefs.showFrozen &&
                current.showHidden == prefs.showHidden
            ) {
                if (current.loading) _state.update { it.copy(loading = false) }
                return@launch
            }

            if (!sameContent(apps, loaded)) {
                loaded = apps
                loadedVersion++
            }
            _state.update {
                it.copy(
                    allApps = apps,
                    loading = false,
                    showFrozen = prefs.showFrozen,
                    showHidden = prefs.showHidden,
                    hidden = hidden,
                )
            }
            recompute()
        }
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

    fun setShowHidden(value: Boolean) {
        prefs.showHidden = value
        _state.update { it.copy(showHidden = value) }
        recompute()
    }

    fun setPanelHeight(percent: Int) {
        prefs.panelHeight = percent
        _state.update { it.copy(panelHeight = prefs.panelHeight) }
    }

    fun setGridColumns(columns: Int) {
        prefs.gridColumns = columns
        _state.update { it.copy(gridColumns = prefs.gridColumns) }
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
                hidden = prefs.hiddenApps(),
                message = if (nowHidden) "已隐藏 $label" else "已恢复 $label",
            )
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
                delay(250)
                refresh(silent = true)
            }
            val launched = startEntry(ctx, entry)
            if (launched) {
                _state.update { it.copy(lastLaunchedAt = System.currentTimeMillis()) }
            } else {
                _state.update { it.copy(message = "无法打开 ${entry.label}") }
            }
        }
    }

    fun setFrozen(entry: AppEntry, frozen: Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (frozen) Privileged.freeze(entry.packageName) else Privileged.unfreeze(entry.packageName)
            }
            _state.update {
                it.copy(
                    message = when {
                        !ok && frozen -> "冻结 ${entry.label} 需要 Root 或 Shizuku 授权"
                        !ok -> "解冻 ${entry.label} 需要 Root 或 Shizuku 授权"
                        frozen -> "已冻结 ${entry.label}"
                        else -> "已解冻 ${entry.label}"
                    }
                )
            }
            if (ok) {
                delay(300)
                refresh(silent = true)
            }
        }
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
    private fun ensureBase() {
        val snapshot = _state.value
        val key = "${snapshot.showFrozen}|${snapshot.showHidden}|${snapshot.hidden.hashCode()}|$loadedVersion"
        if (key == baseKey) return
        baseKey = key
        base = loaded
            .filter { entry ->
                (snapshot.showFrozen || !entry.isFrozen) &&
                    (snapshot.showHidden || !snapshot.hidden.contains(entry.packageName))
            }
            .sortedWith(compareBy({ it.sortKey }, { it.label }))
    }

    private fun recompute() {
        ensureBase()
        val query = Matcher.normalize(_state.value.query)

        searchJob?.cancel()
        if (query.isEmpty()) {
            _state.update { it.copy(results = base) }
            return
        }

        val source = base
        searchJob = viewModelScope.launch {
            // scoring runs off the main thread; a newer keystroke cancels it
            val found = withContext(Dispatchers.Default) { search(source, query) }
            _state.update { state ->
                if (Matcher.normalize(state.query) == query) state.copy(results = found) else state
            }
        }
    }

    private fun search(source: List<AppEntry>, query: String): List<AppEntry> =
        source.asSequence()
            .map { it to Matcher.score(it, query) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<AppEntry, Int>> { it.second }
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

    private companion object {
        const val MAX_RESULTS = 200
    }
}
