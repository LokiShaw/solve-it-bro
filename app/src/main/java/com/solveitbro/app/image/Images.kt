package com.solveitbro.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.solveitbro.app.review.CropRect
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Decodes [uri] downsampled to at most [maxEdge] px and rotated per its EXIF tag. */
fun Context.loadUpright(uri: Uri, maxEdge: Int = 2048): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2

    val decoded = contentResolver.openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Unreadable image: $uri")

    val degrees = contentResolver.openInputStream(uri).use { stream ->
        if (stream == null) 0 else ExifInterface(stream).rotationDegrees
    }
    if (degrees == 0) return decoded
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}

fun Bitmap.crop(rect: CropRect): Bitmap {
    val px = rect.toPixels(width, height)
    return Bitmap.createBitmap(this, px.x, px.y, px.width, px.height)
}

fun Bitmap.scaledToMaxEdge(maxEdge: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxEdge) return this
    val scale = maxEdge.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * scale).roundToInt(), (height * scale).roundToInt(), true)
}

fun Bitmap.toJpeg(quality: Int = 85): ByteArray =
    ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }
