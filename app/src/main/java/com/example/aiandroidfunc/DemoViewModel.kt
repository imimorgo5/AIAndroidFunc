package com.example.aiandroidfunc

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.time.TimeSource

data class FeatureState(
    val busy: Boolean = false,
    val message: String = "Готово к запуску",
    val result: String = "",
    val failed: Boolean = false,
)

data class DemoState(
    val ocr: FeatureState = FeatureState(),
    val translation: FeatureState = FeatureState(message = "Сначала загрузите языковые модели"),
    val labeling: FeatureState = FeatureState(),
    val cloud: FeatureState = FeatureState(message = "Нужен проект Firebase AI Logic"),
    val localModel: FeatureState = FeatureState(message = "Скачайте пример или выберите .litertlm"),
    val localModelName: String? = null,
    val translationReady: Boolean = false,
    val cloudConfig: CloudConfig? = null,
)

enum class DemoFeature { OCR, TRANSLATION, LABELING, CLOUD, LOCAL_MODEL }

class DemoViewModel(application: Application) : AndroidViewModel(application) {
    var ocrBitmap: Bitmap? = null
    var labelBitmap: Bitmap? = null
    private val local = LocalAiFeatures()
    private val cloud = CloudAiFeature(application)
    private val configStore = CloudConfigStore(application)
    private val localModel = LocalModelFeature(application)
    private val mutableState = MutableStateFlow(
        DemoState(
            cloudConfig = configStore.load(),
            localModelName = localModel.modelName(),
            localModel = FeatureState(message = if (localModel.modelFile.isFile) {
                "Модель сохранена. Готово к локальному запуску"
            } else {
                "Скачайте пример или выберите .litertlm"
            }),
        ),
    )
    val state = mutableState.asStateFlow()

    fun setOcrImage(bitmap: Bitmap) {
        ocrBitmap = bitmap
        update(DemoFeature.OCR, FeatureState())
    }

    fun setLabelImage(bitmap: Bitmap) {
        labelBitmap = bitmap
        update(DemoFeature.LABELING, FeatureState())
    }

    fun saveCloudConfig(config: CloudConfig) {
        require(config.modelName.isNotBlank()) { "Укажите идентификатор модели" }
        configStore.save(config)
        mutableState.update { it.copy(cloudConfig = config, cloud = FeatureState(message = "Firebase настроен. Требуется интернет")) }
    }

    fun clearCloudConfig() {
        cloud.close()
        configStore.clear()
        mutableState.update { it.copy(cloudConfig = null, cloud = FeatureState(message = "Нужен проект Firebase AI Logic")) }
    }

    fun recognize(bitmap: Bitmap) = run(DemoFeature.OCR, "Распознаём на устройстве…") {
        local.recognizeText(bitmap).ifBlank { "Текст не найден. Попробуйте чёткое изображение с латиницей." }
    }

    fun prepareTranslation() = run(DemoFeature.TRANSLATION, "Проверяем и загружаем модели EN / RU…", 240_000) {
        local.prepareTranslation()
        mutableState.update { it.copy(translationReady = true) }
        "Модели готовы. Перевод теперь работает без интернета."
    }

    fun translate(text: String) {
        if (text.isBlank()) return fail(DemoFeature.TRANSLATION, "Введите английский текст")
        if (!state.value.translationReady) return fail(DemoFeature.TRANSLATION, "Сначала нажмите «Подготовить модели»")
        run(DemoFeature.TRANSLATION, "Переводим на устройстве…") { local.translate(text) }
    }

    fun label(bitmap: Bitmap) = run(DemoFeature.LABELING, "Анализируем на устройстве…") {
        local.labelImage(bitmap).joinToString("\n") {
            "${it.name}  ·  ${String.format(Locale.US, "%.1f", it.confidence * 100)}%"
        }.ifBlank { "Метки не найдены. Попробуйте другую фотографию." }
    }

    fun summarize(text: String) {
        if (text.isBlank()) return fail(DemoFeature.CLOUD, "Введите текст для краткого содержания")
        if (text.length > 12_000) return fail(DemoFeature.CLOUD, "Для демонстрации ограничьте текст 12 000 символами")
        val config = state.value.cloudConfig ?: return fail(DemoFeature.CLOUD, "Сначала подключите Firebase через кнопку «Настроить»")
        run(DemoFeature.CLOUD, "Запрос к ${config.modelName} в облаке…", 90_000) { cloud.summarize(config, text) }
    }

    fun importLocalModel(uri: Uri) = run(DemoFeature.LOCAL_MODEL, "Копируем модель в приложение…", 300_000) {
        localModel.importModel(uri)
        mutableState.update { it.copy(localModelName = localModel.modelName()) }
        "Модель сохранена. Запустите короткий запрос для проверки совместимости."
    }

    fun downloadLocalModel() = run(DemoFeature.LOCAL_MODEL, "Скачиваем Qwen3: 0% (328 МиБ)…", 600_000) {
        var lastPercent = -1
        localModel.downloadExample { percent ->
            if (percent != lastPercent) {
                lastPercent = percent
                update(DemoFeature.LOCAL_MODEL, FeatureState(busy = true, message = "Скачиваем Qwen3: $percent% (328 МиБ)…"))
            }
        }
        mutableState.update { it.copy(localModelName = localModel.modelName()) }
        "Qwen3-0.6B готова. Генерация теперь работает без сети."
    }

    fun generateLocal(prompt: String) {
        if (state.value.localModel.busy) return
        if (prompt.isBlank()) return fail(DemoFeature.LOCAL_MODEL, "Введите запрос")
        if (prompt.length > 500) return fail(DemoFeature.LOCAL_MODEL, "Для CPU-демо ограничьте запрос 500 символами")
        if (state.value.localModelName == null) return fail(DemoFeature.LOCAL_MODEL, "Сначала выберите или скачайте модель")
        run(DemoFeature.LOCAL_MODEL, "Загружаем модель и выполняем запрос на CPU…", 180_000) {
            localModel.generate(prompt) { text ->
                update(DemoFeature.LOCAL_MODEL, FeatureState(busy = true, message = "Генерация на CPU…", result = text))
            }.ifBlank { "Модель вернула пустой ответ. Попробуйте другой короткий запрос." }
        }
    }

    private fun DemoState.feature(id: DemoFeature) = when (id) {
        DemoFeature.OCR -> ocr
        DemoFeature.TRANSLATION -> translation
        DemoFeature.LABELING -> labeling
        DemoFeature.CLOUD -> cloud
        DemoFeature.LOCAL_MODEL -> localModel
    }

    private fun update(id: DemoFeature, value: FeatureState) = mutableState.update {
        when (id) {
            DemoFeature.OCR -> it.copy(ocr = value)
            DemoFeature.TRANSLATION -> it.copy(translation = value)
            DemoFeature.LABELING -> it.copy(labeling = value)
            DemoFeature.CLOUD -> it.copy(cloud = value)
            DemoFeature.LOCAL_MODEL -> it.copy(localModel = value)
        }
    }

    private fun fail(id: DemoFeature, message: String) = update(id, FeatureState(message = message, failed = true))

    private fun run(id: DemoFeature, message: String, timeout: Long = 30_000, block: suspend () -> String) {
        if (state.value.feature(id).busy) return
        update(id, FeatureState(busy = true, message = message))
        viewModelScope.launch {
            val start = TimeSource.Monotonic.markNow()
            try {
                val result = withTimeout(timeout) { block() }
                update(id, FeatureState(message = "Готово за ${start.elapsedNow().inWholeMilliseconds} мс", result = result))
            } catch (_: TimeoutCancellationException) {
                fail(id, "Превышено время ожидания. Проверьте сеть или повторите запуск.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val hint = when (id) {
                    DemoFeature.CLOUD -> "\nПроверьте подключение, AI Logic, App Check и квоты проекта."
                    DemoFeature.LOCAL_MODEL -> "\nДля загрузки нужна сеть, для генерации — совместимая .litertlm и достаточно памяти."
                    else -> "\nПопробуйте ещё раз. Для загрузки моделей нужен интернет."
                }
                fail(id, (error.localizedMessage ?: "Не удалось выполнить запрос") + hint)
            }
        }
    }

    override fun onCleared() {
        local.close()
        cloud.close()
        super.onCleared()
    }
}
