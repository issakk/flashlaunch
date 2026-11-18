package com.flashlaunch.data

import com.flashlaunch.search.Matcher

/** One launchable app, with everything the matcher needs precomputed. */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isFrozen: Boolean,
    val isSystem: Boolean,
    val searchLabel: String,
    val pinyinFull: String,
    val pinyinInitials: String,
    val t9Full: String,
    val t9Initials: String,
) {
    val sortKey: String = pinyinFull.ifEmpty { searchLabel }

    /** Longest precomputed haystack; lets the matcher reject impossible queries instantly. */
    val maxLength: Int =
        maxOf(searchLabel.length, pinyinFull.length, pinyinInitials.length, t9Full.length, t9Initials.length)

    companion object {
        fun create(
            packageName: String,
            activityName: String,
            label: String,
            isFrozen: Boolean,
            isSystem: Boolean,
            dict: PinyinDict,
        ): AppEntry {
            val tr = dict.translate(label)
            return AppEntry(
                packageName = packageName,
                activityName = activityName,
                label = label,
                isFrozen = isFrozen,
                isSystem = isSystem,
                searchLabel = label.lowercase(),
                pinyinFull = tr.full,
                pinyinInitials = tr.initials,
                t9Full = Matcher.toT9(tr.full),
                t9Initials = Matcher.toT9(tr.initials),
            )
        }
    }
}
