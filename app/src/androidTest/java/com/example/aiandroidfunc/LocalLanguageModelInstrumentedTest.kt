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

@RunWith(AndroidJUnit4::class)
class LocalLanguageModelInstrumentedTest {
    @Test fun qwenRunsLocallyOnCpu() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val feature = LocalModelFeature(context)
        // The large weights are supplied externally, not bundled into the test APK.
        assumeTrue("Copy a compatible .litertlm into files/models/user-model.litertlm", feature.modelFile.isFile)
        assertTrue(LocalModelFeature.hasValidHeader(feature.modelFile))
        val result = runBlocking {
            withTimeout(180_000) {
                feature.generate("What is the capital of France? Answer in one short sentence.") {}
            }
        }
        Log.i("LocalLlmDemoTest", "Actual local CPU response: $result")
        assertTrue("Actual response: $result", result.contains("Paris", ignoreCase = true))
    }
}
