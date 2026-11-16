package com.flashlaunch.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** Enumerates every app that has a launcher entry, including frozen/disabled ones. */
class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    // built lazily so asset parsing happens on the loader thread, not on the UI thread
    private val dict by lazy { PinyinDict.get(appContext) }

    fun load(): List<AppEntry> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>(256)
        val out = ArrayList<AppEntry>(256)

        // MATCH_DISABLED_COMPONENTS surfaces frozen apps on most ROMs; the plain
        // query is kept as a safety net for ROMs that behave differently.
        val flagsList = intArrayOf(PackageManager.MATCH_DISABLED_COMPONENTS, 0)
        for (flags in flagsList) {
            val resolved = try {
                pm.queryIntentActivities(launcherIntent, flags)
            } catch (t: Throwable) {
                null
            } ?: continue
            for (ri in resolved) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName ?: continue
                if (!seen.add(pkg)) continue

                val label = try {
                    ri.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
                } catch (t: Throwable) {
                    pkg
                }

                val appInfo = try {
                    pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
                } catch (t: Throwable) {
                    null
                }

                val frozen = !ai.enabled || appInfo?.enabled == false
                val system = appInfo != null &&
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                out.add(
                    AppEntry.create(
                        packageName = pkg,
                        activityName = ai.name ?: "",
                        label = label,
                        isFrozen = frozen,
                        isSystem = system,
                        dict = dict,
                    )
                )
            }
        }
        return out
    }
}
