package com.flashlaunch.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.flashlaunch.data.AppEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.Collections

/**
 * Small LRU of app icons, accounted in **bytes**: an adaptive icon at a 320px
 * bucket is ~400KB, so a 128-entry cap could hold 50MB of bitmaps.
 *
 * Decodes run on two threads, so flinging through the grid cannot start a
 * hundred decodes at once, and the grid can prefetch the next screens ahead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object IconCache {

    /** Rough ceiling for icon bitmaps: an eighth of the heap, clamped to 4..24MB. */
    private val maxBytes: Int = (Runtime.getRuntime().maxMemory() / 8L)
        .coerceIn(4L * 1024 * 1024, 24L * 1024 * 1024)
        .toInt()

    /** Keys currently cached, so a package update can drop just that app's icons. */
    private val keys: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private val cache = object : LruCache<String, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: ImageBitmap?,
            newValue: ImageBitmap?,
        ) {
            keys.remove(key)
        }
    }

    private val decoders: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    /** Decode jobs (visible icons and prefetch) share these two threads. */
    val decodeDispatcher: CoroutineDispatcher get() = decoders

    /** Rounds the requested pixel size up to a 32px bucket. */
    private fun bucket(targetPx: Int): Int =
        ((targetPx.coerceIn(48, 320) + 31) / 32) * 32

    private fun keyOf(entry: AppEntry, px: Int): String =
        entry.packageName + "/" + entry.activityName + "#" + px

    fun peek(entry: AppEntry, targetPx: Int): ImageBitmap? =
        cache.get(keyOf(entry, bucket(targetPx)))

    fun load(context: Context, entry: AppEntry, targetPx: Int): ImageBitmap? {
        val px = bucket(targetPx)
        val key = keyOf(entry, px)
        cache.get(key)?.let { return it }
        return decode(context, entry, px, key)
    }

    /**
     * Warms the cache for the screens below the fold. Delayed and yielding so
     * icons that are on screen always decode first.
     */
    suspend fun prefetch(context: Context, entries: List<AppEntry>, targetPx: Int) {
        if (entries.isEmpty()) return
        delay(120)
        val px = bucket(targetPx)
        withContext(decoders) {
            for (entry in entries) {
                val key = keyOf(entry, px)
                if (cache.get(key) == null) {
                    decode(context, entry, px, key)
                    yield()
                }
            }
        }
    }

    /** Drops every cached size of one app after it was installed, updated or removed. */
    fun invalidate(packageName: String) {
        val prefix = "$packageName/"
        val stale = synchronized(keys) { keys.filter { it.startsWith(prefix) } }
        for (key in stale) {
            cache.remove(key)
            keys.remove(key)
        }
    }

    private fun decode(context: Context, entry: AppEntry, px: Int, key: String): ImageBitmap? {
        val pm = context.packageManager
        val drawable: Drawable? = try {
            pm.getActivityIcon(ComponentName(entry.packageName, entry.activityName))
        } catch (t: Throwable) {
            try {
                pm.getApplicationIcon(entry.packageName)
            } catch (t2: Throwable) {
                null
            }
        }
        if (drawable == null) return null

        val bitmap: Bitmap = try {
            val source = (drawable as? BitmapDrawable)?.bitmap
            if (source != null) {
                Bitmap.createScaledBitmap(source, px, px, true)
            } else {
                drawable.toBitmap(px, px)
            }
        } catch (t: Throwable) {
            return null
        }

        val image = bitmap.asImageBitmap()
        cache.put(key, image)
        keys.add(key)
        return image
    }
}
