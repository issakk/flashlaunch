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

    /** Grid icon size in percent of the column-based default (70..150). */
    var iconScalePercent: Int
        get() = sp.getInt(KEY_ICON_SCALE, DEFAULT_ICON_SCALE).coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE)
        set(value) {
            sp.edit().putInt(KEY_ICON_SCALE, value.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE)).apply()
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

    fun clearHidden() {
        sp.edit().remove(KEY_HIDDEN).apply()
    }

    // ---- groups ----

    /** Named sets of apps; an enabled group hides every member at once. */
    fun groups(): List<AppGroup> {
        val raw = sp.getStringSet(KEY_GROUPS, emptySet()) ?: emptySet()
        return raw.mapNotNull { entry ->
            val parts = entry.split(GROUP_SEP)
            if (parts.size < 3) return@mapNotNull null
            AppGroup(
                name = parts[0],
                enabled = parts[1] == "1",
                packages = parts[2].split(',').filter { it.isNotEmpty() }.toSet(),
            )
        }.sortedBy { it.name }
    }

    fun saveGroups(groups: List<AppGroup>) {
        val encoded = groups.map { group ->
            val name = group.name.replace(GROUP_SEP.toString(), "")
            val flag = if (group.enabled) "1" else "0"
            "$name$GROUP_SEP$flag$GROUP_SEP${group.packages.joinToString(",")}"
        }.toSet()
        sp.edit().putStringSet(KEY_GROUPS, encoded).apply()
    }

    /** Individually hidden apps plus the members of every enabled group. */
    fun effectiveHidden(): Set<String> {
        val out = HashSet(hiddenApps())
        for (group in groups()) {
            if (group.enabled) out.addAll(group.packages)
        }
        return out
    }

    companion object {
        const val MIN_PANEL_HEIGHT = 35
        const val MAX_PANEL_HEIGHT = 95
        const val DEFAULT_PANEL_HEIGHT = 60
        const val MIN_ICON_SCALE = 70
        const val MAX_ICON_SCALE = 150
        const val DEFAULT_ICON_SCALE = 100
        const val MIN_GRID_COLUMNS = 3
        const val MAX_GRID_COLUMNS = 8
        const val DEFAULT_GRID_COLUMNS = 5
        const val MIN_KEYPAD_PERCENT = 35
        const val MAX_KEYPAD_PERCENT = 80
        const val DEFAULT_KEYPAD_PERCENT = 45
        private const val KEY_HIDDEN = "hidden_apps"
        private const val KEY_GROUPS = "app_groups"
        private const val GROUP_SEP = '\u0001'
        private const val KEY_SHOW_FROZEN = "show_frozen"
        private const val KEY_PANEL_HEIGHT = "panel_height"
        private const val KEY_KEY_VIBRATION = "key_vibration"
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_ICON_SCALE = "icon_scale"
        private const val KEY_KEYPAD_PERCENT = "keypad_percent"
        private const val KEY_KEYPAD_MIGRATED = "keypad_percent_migrated"
        private const val KEY_SORT_BY_USAGE = "sort_by_usage"
        private const val KEY_USAGE = "launch_counts"
        private const val MAX_LAUNCH_COUNT = 9999
    }
}
