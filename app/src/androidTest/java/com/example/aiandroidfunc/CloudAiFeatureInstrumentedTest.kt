package com.example.aiandroidfunc

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in smoke test: uses the Firebase configuration imported into the installed app. */
@RunWith(AndroidJUnit4::class)
class CloudAiFeatureInstrumentedTest {
    @Test fun configuredFirebaseReturnsRussianSummary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = CloudConfigStore(context).load()
        assumeTrue("Import Firebase configuration before running the cloud smoke test", config != null)
        val cloud = CloudAiFeature(context)
        val result = try {
            runBlocking {
                withTimeout(90_000) {
                    cloud.summarize(checkNotNull(config),
                        "The Android club workshop is on Friday at 16:00 in room 204. " +
                            "Bring a laptop with Android Studio. Registration closes on Thursday.")
                }
            }
        } finally {
            cloud.close()
        }
        Log.i("CloudAiSmokeTest", "Model: ${config?.modelName}; actual response: $result")
        assertTrue("Response should contain the room from the source: $result", result.contains("204"))
        assertTrue("Response should be in Russian: $result", result.any { it in 'А'..'я' || it == 'ё' })
    }
}
