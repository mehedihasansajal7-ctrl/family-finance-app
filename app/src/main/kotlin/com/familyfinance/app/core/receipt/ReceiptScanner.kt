package com.familyfinance.app.core.receipt

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device receipt text extraction using ML Kit.
 *
 * The image never leaves the device: recognition runs locally against the bundled Latin model, so a
 * receipt photo is not uploaded anywhere just to read a number from it.
 */
@Singleton
class ReceiptScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun recognize(uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            recognizer.process(image).await().text
        } finally {
            recognizer.close()
        }
    }
}
