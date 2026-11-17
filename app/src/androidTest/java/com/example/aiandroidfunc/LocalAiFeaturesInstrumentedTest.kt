package com.example.aiandroidfunc

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Integration checks run real bundled models in the emulator. */
@RunWith(AndroidJUnit4::class)
class LocalAiFeaturesInstrumentedTest {
    @Test fun bundledOcrRecognizesLatinText() = runBlocking {
        LocalAiFeatures().use { ai ->
            val image = Bitmap.createBitmap(1000, 260, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            canvas.drawColor(Color.WHITE)
            canvas.drawText("ANDROID AI WORKSHOP", 40f, 150f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f })
            val text = withTimeout(60_000) { ai.recognizeText(image) }
            assertTrue("Actual OCR result: $text", text.contains("ANDROID"))
            assertTrue("Actual OCR result: $text", text.contains("WORKSHOP"))
        }
    }

    @Test fun bundledLabelerRecognizesSampleDog() = runBlocking {
        LocalAiFeatures().use { ai ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val bitmap = context.assets.open("dog.jpg").use { BitmapFactory.decodeStream(it) }
            val tags = withTimeout(60_000) { ai.labelImage(bitmap) }
            assertTrue("Actual labels: $tags", tags.any { it.name.equals("Dog", ignoreCase = true) })
            assertTrue(tags.all { it.confidence in 0f..1f })
        }
    }

    @Test fun translationDownloadsModelsAndTranslatesEnglishToRussian() = runBlocking {
        LocalAiFeatures().use { ai ->
            withTimeout(240_000) { ai.prepareTranslation() }
            val translated = withTimeout(30_000) { ai.translate("Hello world") }
            assertTrue("Actual translation: $translated", translated.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' })
            assertNotEquals("Hello world", translated)
        }
    }

    @Test fun firebaseImportSelectsMatchingAndroidApplication() {
        val json = """{"project_info":{"project_id":"test-project"},"client":[
            {"client_info":{"mobilesdk_app_id":"1:123:android:other","android_client_info":{"package_name":"other.app"}},"api_key":[{"current_key":"unused"}]},
            {"client_info":{"mobilesdk_app_id":"1:123:android:demo","android_client_info":{"package_name":"com.example.aiandroidfunc"}},"api_key":[{"current_key":"test-configuration-value"}]}
        ]}"""
        val config = CloudConfig.fromGoogleServices(json, "com.example.aiandroidfunc")
        assertEquals("test-project", config.projectId)
        assertEquals("1:123:android:demo", config.applicationId)
        assertEquals("test-configuration-value", config.apiKey)
        assertThrows(IllegalStateException::class.java) { CloudConfig.fromGoogleServices(json, "absent.app") }
    }
}
