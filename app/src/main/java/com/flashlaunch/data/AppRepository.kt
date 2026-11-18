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
        val seen = HashSet<String>(256)
        val out = ArrayList<AppEntry>(256)

        // MATCH_DISABLED_COMPONENTS surfaces frozen apps on most ROMs. The plain
        // query is only a safety net for ROMs that behave differently, and
        // running it unconditionally meant scanning every package twice.
        collect(PackageManager.MATCH_DISABLED_COMPONENTS, seen, out)
        if (out.isEmpty()) collect(0, seen, out)
        return out
    }

    private fun collect(flags: Int, seen: MutableSet<String>, out: MutableList<AppEntry>) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(launcherIntent, flags)
        } catch (t: Throwable) {
            null
        } ?: return

        for (ri in resolved) {
            val ai = ri.activityInfo ?: continue
            val pkg = ai.packageName ?: continue
            if (!seen.add(pkg)) continue

            val label = try {
                ri.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
            } catch (t: Throwable) {
                pkg
            }

            // ResolveInfo already carries the ApplicationInfo, so the extra
            // getApplicationInfo() round trip per app is only a fallback.
            val appInfo: ApplicationInfo? = ai.applicationInfo ?: try {
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
}
