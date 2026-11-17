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

/**
 * Small LRU of app icons. Bitmaps are decoded per size bucket so large icons
 * stay sharp and small ones do not waste memory.
 */
object IconCache {

    private val cache = LruCache<String, ImageBitmap>(128)

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
        return image
    }
}
