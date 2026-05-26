package com.idun.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.IOException

/**
 * Bundled-image loader for the ingredients-pedagogy corpus. Reads JPEG/WEBP
 * blobs from `assets/foods/images/<filename>` and pushes them into an
 * ImageView with a tiny LRU cache so list scrolling doesn't re-decode the
 * same bitmap repeatedly.
 *
 * Missing or unreadable files cause the ImageView to be hidden — every
 * caller treats "no image" as the normal degraded state. Idun ships without
 * a network image stack on purpose (no cloud, no auth, no sync).
 */
object AssetImages {

    private const val BASE_PATH = "foods/images"
    private val cache = LruCache<String, Bitmap>(48)

    fun applyFoodImage(view: ImageView, filename: String?) {
        if (filename.isNullOrBlank()) {
            view.visibility = ImageView.GONE
            return
        }
        val bitmap = loadCached(view.context, filename)
        if (bitmap == null) {
            view.visibility = ImageView.GONE
            return
        }
        view.setImageBitmap(bitmap)
        view.visibility = ImageView.VISIBLE
    }

    private fun loadCached(context: Context, filename: String): Bitmap? {
        cache.get(filename)?.let { return it }
        val bitmap = decode(context, filename) ?: return null
        cache.put(filename, bitmap)
        return bitmap
    }

    private fun decode(context: Context, filename: String): Bitmap? {
        return try {
            context.assets.open("$BASE_PATH/$filename").use(BitmapFactory::decodeStream)
        } catch (_: IOException) {
            null
        }
    }
}
