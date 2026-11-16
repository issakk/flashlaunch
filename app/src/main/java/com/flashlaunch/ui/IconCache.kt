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

/** Small LRU of app icons so grid scrolling does not re-decode drawables. */
object IconCache {

    /** Decoded icon edge in pixels; sized from the screen density on first use. */
    @Volatile
    private var iconPx = 0

    private val cache = LruCache<String, ImageBitmap>(128)

    private fun keyOf(entry: AppEntry) = entry.packageName + "/" + entry.activityName

    fun peek(entry: AppEntry): ImageBitmap? = cache.get(keyOf(entry))

    private fun pxOf(context: Context): Int {
        var px = iconPx
        if (px == 0) {
            px = (52 * context.resources.displayMetrics.density).toInt().coerceIn(96, 208)
            iconPx = px
        }
        return px
    }

    fun load(context: Context, entry: AppEntry): ImageBitmap? {
        val key = keyOf(entry)
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

        val px = pxOf(context)
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
