// Example for a supported physical Android device, not included in the emulator app.
// implementation("com.google.mlkit:genai-summarization:1.0.0-beta1")
// Source: https://developers.google.com/ml-kit/genai/summarization/android
package com.example.aiandroidfunc.examples

import android.content.Context
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizerOptions
import com.google.mlkit.genai.summarization.SummarizerOptions.InputType.ARTICLE
import com.google.mlkit.genai.summarization.SummarizerOptions.OutputType.THREE_BULLETS
import com.google.mlkit.genai.summarization.SummarizerOptions.Language.ENGLISH
import kotlinx.coroutines.guava.await

// Input: English article over 400 characters, preferably at least 300 words.
suspend fun nanoSummaryExample(context: Context, article: String): String {
    // slide:nano-summary:start
    val options = SummarizerOptions
        .builder(context)
        .setInputType(ARTICLE)
        .setOutputType(THREE_BULLETS)
        .setLanguage(ENGLISH)
        .build()
    val summarizer =
        Summarization.getClient(options)
    // slide:nano-summary:end
    try {
        if (summarizer.checkFeatureStatus().await() != FeatureStatus.AVAILABLE) {
            return "Сначала подготовьте модель и адаптер суммаризации"
        }
        val request = SummarizationRequest.builder(article).build()
        return summarizer.runInference(request).await().summary
    } finally {
        summarizer.close()
    }
}
