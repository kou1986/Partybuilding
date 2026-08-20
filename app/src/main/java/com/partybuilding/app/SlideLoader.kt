package com.partybuilding.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches assets (images, videos, animated GIFs) loaded from APK assets/.
 *
 * Videos are not loaded eagerly: callers ask [videoFile] for the actual file path
 * to hand to VideoView. Since Android assets cannot be opened with random access
 * by MediaPlayer, we copy them to the cache directory on first access.
 */
object SlideLoader {
    private val imageCache = ConcurrentHashMap<String, Drawable>()
    private val videoCache = ConcurrentHashMap<String, File>()

    fun getImage(context: Context, src: String): Drawable? {
        val rel = src.removePrefix("../").removePrefix("./")
        // image5.GIF is referenced multiple times for different positions.
        // We must return a new instance each time so bounds don't conflict.
        if (rel == "media/image5.GIF") {
            return loadImage(context, rel)
        }
        return imageCache.getOrPut(rel) { loadImage(context, rel) }
    }

    private fun loadImage(context: Context, rel: String): Drawable? {
        return try {
            context.assets.open(rel).use { input ->
                val bytes = input.readBytes()
                if (rel.endsWith(".GIF", ignoreCase = true) || rel.endsWith(".webp", ignoreCase = true)) {
                    loadAnimated(context, bytes, rel)
                } else {
                    BitmapDrawable(context.resources, BitmapFactoryCompat.decode(bytes))
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SlideLoader", "Failed to load image $rel: ${e.message}")
            null
        }
    }

    private fun loadAnimated(context: Context, bytes: ByteArray, rel: String): Drawable? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(bytes)
                val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    // Don't allocate mutable backing unless needed (saves memory).
                    decoder.isMutableRequired = false
                }
                if (drawable is Animatable) (drawable as Animatable).start()
                drawable
            } else {
                val bitmap = BitmapFactoryCompat.decode(bytes)
                BitmapDrawable(context.resources, bitmap)
            }
        } catch (e: Exception) {
            android.util.Log.w("SlideLoader", "Animated decode failed for $rel: ${e.message}")
            val bitmap = BitmapFactoryCompat.decode(bytes)
            BitmapDrawable(context.resources, bitmap)
        }
    }

    fun videoFile(context: Context, src: String): File? {
        val rel = src.removePrefix("../").removePrefix("./")
        return videoCache.getOrPut(rel) { extractVideo(context, rel) }
    }

    private fun extractVideo(context: Context, rel: String): File? {
        return try {
            val cacheFile = File(context.cacheDir, rel.substringAfterLast('/'))
            if (!cacheFile.exists()) {
                context.assets.open(rel).use { input ->
                    FileOutputStream(cacheFile).use { out ->
                        input.copyTo(out)
                    }
                }
            }
            cacheFile
        } catch (e: Exception) {
            android.util.Log.w("SlideLoader", "Failed to extract video $rel: ${e.message}")
            null
        }
    }

    fun clear() {
        imageCache.clear()
        videoCache.values.forEach { it.delete() }
        videoCache.clear()
    }
}

object BitmapFactoryCompat {
    fun decode(bytes: ByteArray): Bitmap =
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("Failed to decode bitmap")
}

