package com.flashlaunch.data

import android.content.Context

/** Tiny SharedPreferences wrapper: hidden-app list + display toggles. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("flashlaunch", Context.MODE_PRIVATE)

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

    companion object {
        const val MIN_PANEL_HEIGHT = 35
        const val MAX_PANEL_HEIGHT = 95
        const val DEFAULT_PANEL_HEIGHT = 60
        const val MIN_GRID_COLUMNS = 3
        const val MAX_GRID_COLUMNS = 8
        const val DEFAULT_GRID_COLUMNS = 5
        private const val KEY_HIDDEN = "hidden_apps"
        private const val KEY_SHOW_FROZEN = "show_frozen"
        private const val KEY_SHOW_HIDDEN = "show_hidden"
        private const val KEY_PANEL_HEIGHT = "panel_height"
        private const val KEY_KEY_VIBRATION = "key_vibration"
        private const val KEY_GRID_COLUMNS = "grid_columns"
    }
}
