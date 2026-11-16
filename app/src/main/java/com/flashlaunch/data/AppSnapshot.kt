package com.flashlaunch.data

import android.content.Context
import java.io.File

/**
 * Tiny on-disk snapshot of the app list so the panel can paint real content on
 * the first frame instead of an empty grid. The snapshot is only a cache: the
 * live package list always wins as soon as it is loaded.
 */
object AppSnapshot {

    private const val FILE_NAME = "apps_cache.v1"
    private const val SEP = '\u0001'

    fun load(context: Context): List<AppEntry> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        val dict = PinyinDict.get(context)
        return try {
            file.bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    if (line.isEmpty()) return@mapNotNull null
                    val parts = line.split(SEP)
                    if (parts.size < 4) return@mapNotNull null
                    AppEntry.create(
                        packageName = parts[0],
                        activityName = parts[1],
                        label = parts.subList(3, parts.size).joinToString(SEP.toString()),
                        isFrozen = parts[2] == "1",
                        isSystem = false,
                        dict = dict,
                    )
                }.toList()
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun save(context: Context, apps: List<AppEntry>) {
        try {
            val target = File(context.filesDir, FILE_NAME)
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.bufferedWriter().use { writer ->
                for (app in apps) {
                    writer.write(app.packageName)
                    writer.write(SEP.code)
                    writer.write(app.activityName)
                    writer.write(SEP.code)
                    writer.write(if (app.isFrozen) '1'.code else '0'.code)
                    writer.write(SEP.code)
                    writer.write(app.label)
                    writer.write('\n'.code)
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            // a stale cache is harmless next to a live package scan
        }
    }
}
