package com.sodogku.devfeedback

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.sodogku.libraries.core.Catching
import java.io.ByteArrayOutputStream

actual fun ImageBitmap.encodeToJpeg(quality: Int): ByteArray? = Catching {
    val stream = ByteArrayOutputStream()
    // A hardware-backed bitmap has no pixel data on the CPU side, so compress
    // would fail; copying to ARGB_8888 first is the documented way round it and
    // is what a Compose graphics-layer capture hands back on some devices.
    val source = asAndroidBitmap()
    val compressible = if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        source
    }
    compressible.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    stream.toByteArray()
}.getOrNull()
