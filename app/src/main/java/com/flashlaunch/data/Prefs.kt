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

    fun hiddenApps(): Set<String> =
        HashSet(sp.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet())

    fun setHidden(pkg: String, hidden: Boolean) {
        val set = hiddenApps()
        if (hidden) set.add(pkg) else set.remove(pkg)
        sp.edit().putStringSet(KEY_HIDDEN, set).apply()
    }

    private companion object {
        const val KEY_HIDDEN = "hidden_apps"
        const val KEY_SHOW_FROZEN = "show_frozen"
        const val KEY_SHOW_HIDDEN = "show_hidden"
    }
}
