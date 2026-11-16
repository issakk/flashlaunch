package com.flashlaunch.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flashlaunch.data.AppEntry
import com.flashlaunch.data.AppRepository
import com.flashlaunch.data.Prefs
import com.flashlaunch.search.Matcher
import com.flashlaunch.system.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val allApps: List<AppEntry> = emptyList(),
    val results: List<AppEntry> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val query: String = "",
    val loading: Boolean = true,
    val showFrozen: Boolean = true,
    val showHidden: Boolean = false,
    val rootAvailable: Boolean = false,
    val message: String? = null,
) {
    val hiddenCount: Int get() = hidden.size
    val frozenCount: Int get() = allApps.count { it.isFrozen }
}

class AppsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AppRepository(app)
    private val prefs = Prefs(app)
    private var loaded: List<AppEntry> = emptyList()

    private val _state = MutableStateFlow(
        UiState(
            showFrozen = prefs.showFrozen,
            showHidden = prefs.showHidden,
            hidden = prefs.hiddenApps(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh(silent = true)
        }
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
        refresh(silent = false)
        viewModelScope.launch {
            val rooted = withContext(Dispatchers.IO) { RootShell.isRooted() }
            _state.update { it.copy(rootAvailable = rooted) }
        }
    }

    override fun onCleared() {
        try {
            getApplication<Application>().unregisterReceiver(packageReceiver)
        } catch (t: Throwable) {
            // already gone
        }
        super.onCleared()
    }

    fun onResume() {
        if (loaded.isNotEmpty()) refresh(silent = true)
    }

    fun refresh(silent: Boolean = true) {
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(loading = true) }
            val apps = withContext(Dispatchers.IO) {
                runCatching { repository.load() }.getOrElse { emptyList() }
            }
            loaded = apps
            _state.update {
                it.copy(
                    allApps = apps,
                    loading = false,
                    showFrozen = prefs.showFrozen,
                    showHidden = prefs.showHidden,
                    hidden = prefs.hiddenApps(),
                )
            }
            recompute()
        }
    }

    fun setQuery(query: String) {
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
                val unfrozen = withContext(Dispatchers.IO) { RootShell.unfreeze(entry.packageName) }
                if (!unfrozen) {
                    _state.update { it.copy(message = "解冻 ${entry.label} 需要 Root 权限") }
                    return@launch
                }
                delay(250)
                refresh(silent = true)
            }
            val launched = startEntry(ctx, entry)
            if (!launched) {
                _state.update { it.copy(message = "无法打开 ${entry.label}") }
            }
        }
    }

    fun setFrozen(entry: AppEntry, frozen: Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (frozen) RootShell.freeze(entry.packageName) else RootShell.unfreeze(entry.packageName)
            }
            _state.update {
                it.copy(
                    message = when {
                        !ok -> "操作 ${entry.label} 需要 Root 权限"
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

    fun checkRoot() {
        viewModelScope.launch {
            val rooted = withContext(Dispatchers.IO) { RootShell.isRooted() }
            _state.update {
                it.copy(
                    rootAvailable = rooted,
                    message = if (rooted) "Root 可用" else "未检测到 Root",
                )
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun recompute() {
        val snapshot = _state.value
        val visible = loaded.filter { entry ->
            (snapshot.showFrozen || !entry.isFrozen) &&
                (snapshot.showHidden || !snapshot.hidden.contains(entry.packageName))
        }
        val query = Matcher.normalize(snapshot.query)
        val ordered = if (query.isEmpty()) {
            visible.sortedWith(compareBy({ it.sortKey }, { it.label }))
        } else {
            visible.asSequence()
                .map { it to Matcher.score(it, query) }
                .filter { it.second > 0 }
                .sortedWith(
                    compareByDescending<Pair<AppEntry, Int>> { it.second }
                        .thenBy { it.first.label }
                )
                .map { it.first }
                .take(MAX_RESULTS)
                .toList()
        }
        _state.update { it.copy(results = ordered) }
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
