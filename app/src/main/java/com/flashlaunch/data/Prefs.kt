package com.flashlaunch.data

import android.content.Context

/** Tiny SharedPreferences wrapper: hidden-app list + display toggles. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("flashlaunch", Context.MODE_PRIVATE)

    init {
        // v0.5.2 lowered the keypad default; move untouched installs along once
        if (!sp.getBoolean(KEY_KEYPAD_MIGRATED, false)) {
            if (sp.getInt(KEY_KEYPAD_PERCENT, 0) == 52) {
                sp.edit().putInt(KEY_KEYPAD_PERCENT, DEFAULT_KEYPAD_PERCENT).apply()
            }
            sp.edit().putBoolean(KEY_KEYPAD_MIGRATED, true).apply()
        }
    }

    var showFrozen: Boolean
        get() = sp.getBoolean(KEY_SHOW_FROZEN, true)
        set(value) {
            sp.edit().putBoolean(KEY_SHOW_FROZEN, value).apply()
        }

    var showHidden: Boolean
        get() = sp.getBoolean(KEY_SHOW_HIDDEN, false)
        set(value) {
            sp.edit().putBoolean(KEY_SHOW_HIDDEN, value).apply()
        }

    /** Panel height in percent of the screen (35..95). */
    var panelHeight: Int
        get() = sp.getInt(KEY_PANEL_HEIGHT, DEFAULT_PANEL_HEIGHT).coerceIn(MIN_PANEL_HEIGHT, MAX_PANEL_HEIGHT)
        set(value) {
            sp.edit().putInt(KEY_PANEL_HEIGHT, value.coerceIn(MIN_PANEL_HEIGHT, MAX_PANEL_HEIGHT)).apply()
        }

    /** Icons per row in the grid (3..8). */
    var gridColumns: Int
        get() = sp.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS).coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
        set(value) {
            sp.edit().putInt(KEY_GRID_COLUMNS, value.coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)).apply()
        }

    /** Keypad height as a percentage of the panel height. */
    var keypadPercent: Int
        get() = sp.getInt(KEY_KEYPAD_PERCENT, DEFAULT_KEYPAD_PERCENT).coerceIn(MIN_KEYPAD_PERCENT, MAX_KEYPAD_PERCENT)
        set(value) {
            sp.edit().putInt(KEY_KEYPAD_PERCENT, value.coerceIn(MIN_KEYPAD_PERCENT, MAX_KEYPAD_PERCENT)).apply()
        }

    /** Haptic feedback when a keypad key is pressed. */
    var keyVibration: Boolean
        get() = sp.getBoolean(KEY_KEY_VIBRATION, true)
        set(value) {
            sp.edit().putBoolean(KEY_KEY_VIBRATION, value).apply()
        }

    fun hiddenApps(): Set<String> =
        HashSet(sp.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet())

    fun setHidden(pkg: String, hidden: Boolean) {
        val set = HashSet(hiddenApps())
        if (hidden) set.add(pkg) else set.remove(pkg)
        sp.edit().putStringSet(KEY_HIDDEN, set).apply()
    }

    /** Sort the idle grid by how often an app was launched. */
    var sortByUsage: Boolean
        get() = sp.getBoolean(KEY_SORT_BY_USAGE, true)
        set(value) {
            sp.edit().putBoolean(KEY_SORT_BY_USAGE, value).apply()
        }

    /** package -> launch count, stored as one compact `pkg=count;pkg=count` string. */
    fun launchCounts(): Map<String, Int> {
        val raw = sp.getString(KEY_USAGE, "") ?: ""
        if (raw.isEmpty()) return emptyMap()
        val out = HashMap<String, Int>()
        for (part in raw.split(';')) {
            if (part.isEmpty()) continue
            val split = part.lastIndexOf('=')
            if (split <= 0) continue
            val count = part.substring(split + 1).toIntOrNull() ?: continue
            out[part.substring(0, split)] = count
        }
        return out
    }

    fun recordLaunch(packageName: String) {
        val counts = HashMap(launchCounts())
        counts[packageName] = ((counts[packageName] ?: 0) + 1).coerceAtMost(MAX_LAUNCH_COUNT)
        sp.edit().putString(
            KEY_USAGE,
            counts.entries.joinToString(";") { "${it.key}=${it.value}" },
        ).apply()
    }

    fun clearLaunchCounts() {
        sp.edit().remove(KEY_USAGE).apply()
    }

    companion object {
        const val MIN_PANEL_HEIGHT = 35
        const val MAX_PANEL_HEIGHT = 95
        const val DEFAULT_PANEL_HEIGHT = 60
        const val MIN_GRID_COLUMNS = 3
        const val MAX_GRID_COLUMNS = 8
        const val DEFAULT_GRID_COLUMNS = 5
        const val MIN_KEYPAD_PERCENT = 35
        const val MAX_KEYPAD_PERCENT = 80
        const val DEFAULT_KEYPAD_PERCENT = 45
        private const val KEY_HIDDEN = "hidden_apps"
        private const val KEY_SHOW_FROZEN = "show_frozen"
        private const val KEY_SHOW_HIDDEN = "show_hidden"
        private const val KEY_PANEL_HEIGHT = "panel_height"
        private const val KEY_KEY_VIBRATION = "key_vibration"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_KEYPAD_PERCENT = "keypad_percent"
        private const val KEY_KEYPAD_MIGRATED = "keypad_percent_migrated"
        private const val KEY_SORT_BY_USAGE = "sort_by_usage"
        private const val KEY_USAGE = "launch_counts"
        private const val MAX_LAUNCH_COUNT = 9999
    }
}
