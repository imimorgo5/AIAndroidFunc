package com.example.aiandroidfunc

import android.graphics.Bitmap
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class ImageTag(val name: String, val confidence: Float)

/** All three features run on-device. Only translation model delivery uses a network. */
class LocalAiFeatures : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    // slide:translation-client:start
    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.RUSSIAN)
            .build()
    )
    // slide:translation-client:end

    // slide:ocr:start
    suspend fun recognizeText(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return result.text
    }
    // slide:ocr:end

    // slide:translation:start
    suspend fun prepareTranslation() {
        translator.downloadModelIfNeeded(
            DownloadConditions.Builder().build()
        ).await()
    }

    suspend fun translate(text: String): String =
        translator.translate(text).await()
    // slide:translation:end

    // slide:labeling:start
    suspend fun labelImage(bitmap: Bitmap): List<ImageTag> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return labeler.process(image).await()
            .sortedByDescending { it.confidence }
            .take(5)
            .map { ImageTag(it.text, it.confidence) }
    }
    // slide:labeling:end

    override fun close() {
        recognizer.close()
        translator.close()
        labeler.close()
    }
}
