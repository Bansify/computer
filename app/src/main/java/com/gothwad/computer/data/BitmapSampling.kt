package com.gothwad.computer.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.res.Resources

/** Bounds decoding before allocation; a corrupt image returns null. */
object BitmapSampling {
    fun sampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        if (width <= 0 || height <= 0 || maxWidth <= 0 || maxHeight <= 0) return 1
        var sample = 1
        while (width / sample > maxWidth || height / sample > maxHeight) {
            if (sample > Int.MAX_VALUE / 2) break
            sample *= 2
        }
        return sample
    }

    fun decodeFile(path: String, maxWidth: Int, maxHeight: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
            inPreferredConfig = config
        }
        return BitmapFactory.decodeFile(path, options)
    }

    fun decodeResource(resources: Resources, id: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, id, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
        }
        return BitmapFactory.decodeResource(resources, id, options)
    }
}
