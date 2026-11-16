package com.flashlaunch.search

import com.flashlaunch.data.AppEntry

/**
 * Scoring matcher: label, full pinyin, pinyin initials, T9 digits and a
 * subsequence fallback ("wxtb" -> 微信淘宝).
 */
object Matcher {

    private const val NO_MATCH = 0

    /** Maps a latin letter to its T9 keypad digit. */
    fun digitOf(c: Char): Char = when (c) {
        in 'a'..'c' -> '2'
        in 'd'..'f' -> '3'
        in 'g'..'i' -> '4'
        in 'j'..'l' -> '5'
        in 'm'..'o' -> '6'
        in 'p'..'s' -> '7'
        in 't'..'v' -> '8'
        in 'w'..'z' -> '9'
        else -> c
    }

    fun toT9(s: String): String {
        if (s.isEmpty()) return s
        val out = StringBuilder(s.length)
        for (c in s) out.append(digitOf(c))
        return out.toString()
    }

    /** Keeps letters/digits only, lowercased. */
    fun normalize(query: String): String {
        val out = StringBuilder(query.length)
        for (c in query) {
            val lc = c.lowercaseChar()
            if (lc.isLetterOrDigit()) out.append(lc)
        }
        return out.toString()
    }

    fun score(entry: AppEntry, q: String): Int {
        if (q.isEmpty()) return NO_MATCH
        // nothing can match a query longer than the longest haystack
        if (q.length > entry.maxLength) return NO_MATCH
        var best = NO_MATCH

        fun consider(v: Int) {
            if (v > best) best = v
        }

        val label = entry.searchLabel
        when {
            label == q -> consider(1200)
            label.startsWith(q) -> consider(1000 - label.length)
            else -> {
                val i = label.indexOf(q)
                if (i >= 0) {
                    val boundary = i > 0 && !label[i - 1].isLetterOrDigit()
                    consider((if (boundary) 950 else 850) - i)
                }
            }
        }

        val full = entry.pinyinFull
        if (full.isNotEmpty()) {
            when {
                full == q -> consider(1180)
                full.startsWith(q) -> consider(990 - full.length)
                else -> {
                    val i = full.indexOf(q)
                    if (i >= 0) consider(840 - i)
                }
            }
        }

        val initials = entry.pinyinInitials
        if (initials.isNotEmpty()) {
            when {
                initials == q -> consider(1160)
                initials.startsWith(q) -> consider(980 - initials.length)
                else -> {
                    val i = initials.indexOf(q)
                    if (i >= 0) consider(830 - i)
                }
            }
        }

        if (q[0].isDigit()) {
            val t9Initials = entry.t9Initials
            if (t9Initials.isNotEmpty()) {
                when {
                    t9Initials == q -> consider(1150)
                    t9Initials.startsWith(q) -> consider(970 - t9Initials.length)
                    else -> {
                        val i = t9Initials.indexOf(q)
                        if (i >= 0) consider(820 - i)
                    }
                }
            }
            val t9Full = entry.t9Full
            if (t9Full.isNotEmpty()) {
                when {
                    t9Full == q -> consider(1140)
                    t9Full.startsWith(q) -> consider(960 - t9Full.length)
                    else -> {
                        val i = t9Full.indexOf(q)
                        if (i >= 0) consider(810 - i)
                    }
                }
            }
        }

        if (best == NO_MATCH) {
            val s1 = subsequence(q, initials)
            if (s1 > 0) consider(600 + s1)
            val s2 = subsequence(q, full)
            if (s2 > 0) consider(500 + s2)
            if (q[0].isDigit()) {
                val s3 = subsequence(q, entry.t9Initials)
                if (s3 > 0) consider(560 + s3)
            }
        }

        if (best == NO_MATCH) return NO_MATCH
        // shorter labels win ties
        return best + (60 - minOf(label.length, 40))
    }

    /** Matches characters in order; returns 0 when not a subsequence. */
    private fun subsequence(needle: String, haystack: String): Int {
        if (haystack.isEmpty()) return 0
        var cursor = 0
        var gaps = 0
        for (c in needle) {
            val found = haystack.indexOf(c, cursor)
            if (found < 0) return 0
            gaps += found - cursor
            cursor = found + 1
        }
        return 100 - minOf(gaps, 90)
    }
}
