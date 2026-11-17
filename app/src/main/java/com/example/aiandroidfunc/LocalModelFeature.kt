package com.example.aiandroidfunc

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** An independent local model. This does not use Gemini Nano, AICore or Firebase. */
class LocalModelFeature(private val context: Context) {
    val modelFile = File(context.filesDir, "models/user-model.litertlm")
    private val nameFile = File(context.filesDir, "models/model-name.txt")

    fun modelName(): String? = if (modelFile.isFile) {
        runCatching { nameFile.readText() }.getOrDefault("Своя модель .litertlm")
    } else null

    suspend fun importModel(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            saveModel(input, expectedHash = null) {}
        } ?: error("Не удалось открыть файл модели")
        nameFile.writeText("Своя модель .litertlm")
    }

    suspend fun downloadExample(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val connection = URL(EXAMPLE_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.connect()
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Сервер модели вернул HTTP ${connection.responseCode}"
            }
            connection.inputStream.use { input ->
                saveModel(input, expectedHash = EXAMPLE_SHA256) { copied ->
                    onProgress(((copied * 100) / EXAMPLE_BYTES).toInt().coerceAtMost(100))
                }
            }
            nameFile.writeText("Qwen3-0.6B · INT4 · Apache 2.0")
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun saveModel(input: InputStream, expectedHash: String?, progress: (Long) -> Unit) {
        val directory = checkNotNull(modelFile.parentFile)
        check(directory.isDirectory || directory.mkdirs()) { "Не удалось создать папку моделей" }
        val staging = File(directory, "import.litertlm.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            staging.outputStream().buffered().use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= MAX_MODEL_BYTES) { "Модель слишком большая для учебного примера (максимум 4 ГиБ)" }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    progress(copied)
                }
            }
            require(hasValidHeader(staging)) { "Нужен совместимый файл LiteRT-LM (.litertlm). GGUF и safetensors напрямую не поддерживаются." }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            require(expectedHash == null || actualHash == expectedHash) { "Контрольная сумма модели не совпала. Повторите скачивание." }
            Files.move(staging.toPath(), modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            staging.delete()
        }
    }

    suspend fun generate(prompt: String, onText: (String) -> Unit): String = withContext(Dispatchers.IO) {
        require(modelFile.isFile) { "Сначала скачайте пример или выберите свою модель" }
        // A short context and a bounded output make the CPU demo manageable.
        val conversationConfig = ConversationConfig(
            maxOutputToken = 96,
            thinkingConfig = ThinkingConfig(enableThinking = false),
        )
        // slide:local-model:start
        val config = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = Backend.CPU(),
            maxNumTokens = 512,
        )
        Engine(config).use { engine ->
            engine.initialize()
            engine.createConversation(conversationConfig).use { chat ->
                val text = StringBuilder()
                chat.sendMessageAsync(prompt).collect { chunk ->
                    text.append(chunk.toString())
                    onText(text.toString())
                }
                text.toString()
            }
        }
        // slide:local-model:end
    }

    companion object {
        const val EXAMPLE_BYTES = 343_490_560L
        const val EXAMPLE_SHA256 = "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0"
        const val EXAMPLE_URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/8d42ab5f1db14cad987fac57aeb17d070f9e180d/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm?download=true"
        private const val MAX_MODEL_BYTES = 4L * 1024 * 1024 * 1024

        fun hasValidHeader(file: File): Boolean = runCatching {
            file.inputStream().use { input ->
                val magic = ByteArray(8)
                input.read(magic) == magic.size && magic.contentEquals("LITERTLM".toByteArray(Charsets.US_ASCII))
            }
        }.getOrDefault(false)
    }
}
