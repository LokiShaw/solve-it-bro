package com.solveitbro.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.solveitbro.app.data.Language
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device OCR with ML Kit. The text is only a hint for the AI, which also sees the photo,
 * so any failure returns null instead of throwing.
 */
suspend fun readText(bitmap: Bitmap, language: Language): String? {
    // The Devanagari model also reads Latin text, so Hindi users get both scripts.
    val recognizer = when (language) {
        Language.HI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        Language.EN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    return try {
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(it.text.trim().ifEmpty { null }) }
                .addOnFailureListener { cont.resume(null) }
        }
    } finally {
        recognizer.close()
    }
}
