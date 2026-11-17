// Example for a supported physical Android device, not included in the emulator app.
// implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
// Source: https://developers.google.com/ml-kit/genai/prompt/android/get-started
package com.example.aiandroidfunc.examples

import com.google.mlkit.genai.common.FeatureStatus.AVAILABLE
import com.google.mlkit.genai.prompt.Generation

// Call from a coroutine. Handle GenAiException at the UI boundary.
suspend fun nanoPromptExample(): String {
    val model = Generation.getClient()
    try {
        // slide:nano-prompt:start
        if (model.checkStatus() != AVAILABLE) {
            return "Модель недоступна"
        }
        val response = model.generateContent(
            "Give 3 Kotlin tips."
        )
        return response.candidates
            .firstOrNull()?.text.orEmpty()
        // slide:nano-prompt:end
    } finally {
        model.close()
    }
}

// DOWNLOADABLE: collect model.download(), handle failure, then checkStatus again.
// DOWNLOADING: show progress. UNAVAILABLE: keep the feature disabled.
