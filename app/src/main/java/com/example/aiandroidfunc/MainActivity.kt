package com.example.aiandroidfunc

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var model: DemoViewModel
    private lateinit var scroll: ScrollView
    private lateinit var root: LinearLayout
    private lateinit var ocrImage: ImageView
    private lateinit var labelImage: ImageView
    private lateinit var translationInput: EditText
    private lateinit var cloudInput: EditText
    private lateinit var localModelInput: EditText
    private lateinit var localModelInfo: TextView
    private lateinit var cloudInfo: TextView
    private lateinit var ocrViews: FeatureViews
    private lateinit var translationViews: FeatureViews
    private lateinit var labelViews: FeatureViews
    private lateinit var cloudViews: FeatureViews
    private lateinit var localModelViews: FeatureViews
    private lateinit var localModelGenerate: MaterialButton
    private lateinit var prepareButton: MaterialButton
    private lateinit var translateButton: MaterialButton
    private lateinit var cloudSettings: MaterialButton
    private lateinit var copyOcr: MaterialButton
    private val green = Color.rgb(20, 107, 69)
    private val ink = Color.rgb(24, 39, 31)
    private val muted = Color.rgb(87, 105, 95)

    private val chooseOcr = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadImage(uri, true)
    }
    private val chooseLabel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadImage(uri, false)
    }
    private val chooseLocalModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importLocalModel(uri)
    }
    private val chooseFirebase = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                val config = withContext(Dispatchers.IO) {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                        val chars = CharArray(1_048_577)
                        var total = 0
                        while (total < chars.size) {
                            val count = it.read(chars, total, chars.size - total)
                            if (count < 0) break
                            total += count
                        }
                        require(total < chars.size) { "Слишком большой JSON-файл" }
                        String(chars, 0, total)
                    } ?: error("Не удалось прочитать файл")
                    CloudConfig.fromGoogleServices(json, packageName)
                }
                model.saveCloudConfig(config)
                showCloudSettings()
            } catch (error: Exception) {
                showError("Не удалось импортировать Firebase", error)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[DemoViewModel::class.java]
        buildScreen()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    ocrViews.render(state.ocr)
                    translationViews.render(state.translation)
                    labelViews.render(state.labeling)
                    cloudViews.render(state.cloud)
                    localModelViews.render(state.localModel)
                    localModelInfo.text = state.localModelName ?: "Модель ещё не выбрана"
                    localModelGenerate.isEnabled = state.localModelName != null && !state.localModel.busy
                    translateButton.isEnabled = state.translationReady && !state.translation.busy
                    prepareButton.text = if (state.translationReady) "Модели готовы ✓" else "Подготовить модели"
                    cloudSettings.isEnabled = !state.cloud.busy
                    copyOcr.isEnabled = !state.ocr.busy && state.ocr.result.isNotBlank()
                    cloudInfo.text = state.cloudConfig?.let { "Облако · ${it.modelName}\nПроект: ${it.projectId}" }
                        ?: "Облако · Gemini Flash\nFirebase ещё не подключён"
                }
            }
        }
    }

    private fun buildScreen() {
        scroll = ScrollView(this).apply { isFillViewport = true; id = R.id.demo_scroll }
        root = column(20).apply {
            setBackgroundColor(Color.rgb(244, 246, 242))
            isFocusableInTouchMode = true
        }
        scroll.addView(root)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        text(root, "ANDROID / KOTLIN", 12, green, true)
        text(root, "Android AI Lab", 30, ink, true)
        text(root, "Пять AI-фич в одном приложении", 16, muted)

        val ocrSection = section("01  Текст с изображения", "ML Kit OCR · на устройстве")
        text(ocrSection, "Пример на английском: латинская модель OCR. Можно выбрать своё изображение.", 14, muted)
        ocrImage = image(ocrSection, 180, "Изображение с английским текстом для распознавания")
        if (model.ocrBitmap == null) model.ocrBitmap = sampleTextBitmap()
        ocrImage.setImageBitmap(model.ocrBitmap)
        val ocrButtons = buttons(ocrSection)
        val recognize = button(ocrButtons, "Распознать") { hideKeyboard(); model.ocrBitmap?.let(model::recognize) }
        val pickOcr = button(ocrButtons, "Своя картинка", true) { chooseOcr.launch(arrayOf("image/*")) }
        ocrViews = results(ocrSection, recognize, pickOcr)
        copyOcr = button(ocrSection, "Передать текст в перевод", true) {
            translationInput.setText(model.state.value.ocr.result)
            hideKeyboard()
            scroll.smoothScrollTo(0, translationInput.parent.let { (it as View).top } + (translationInput.parent.parent as View).top)
        }

        val translationSection = section("02  Перевод EN → RU", "ML Kit Translation · на устройстве")
        text(translationSection, "Один раз загрузите языковые модели через интернет. Затем перевод работает офлайн.", 14, muted)
        translationInput = editor(translationSection, "Английский текст", R.id.translation_input, 3).apply { setText(SAMPLE_TEXT) }
        prepareButton = button(translationSection, "Подготовить модели", true) { hideKeyboard(); model.prepareTranslation() }
        translateButton = button(translationSection, "Перевести на русский") { hideKeyboard(); model.translate(translationInput.text.toString()) }
        translationViews = results(translationSection, prepareButton, translateButton)

        val labelingSection = section("03  Что на фотографии", "ML Kit Image Labeling · на устройстве")
        text(labelingSection, "Одна фотография может получить несколько категорий: например, «собака» и «трава». Confidence — оценка модели, она может ошибаться даже при высоком значении.", 14, muted)
        labelImage = image(labelingSection, 200, "Фотография собаки на траве для классификации")
        if (model.labelBitmap == null) model.labelBitmap = assets.open("dog.jpg").use { BitmapFactory.decodeStream(it) }
        labelImage.setImageBitmap(model.labelBitmap)
        val labelButtons = buttons(labelingSection)
        val label = button(labelButtons, "Определить") { hideKeyboard(); model.labelBitmap?.let(model::label) }
        val pickLabel = button(labelButtons, "Своё фото", true) { chooseLabel.launch(arrayOf("image/*")) }
        labelViews = results(labelingSection, label, pickLabel)

        val cloudSection = section("04  Краткое содержание", "Firebase AI Logic · генерация в облаке")
        cloudInfo = text(cloudSection, "Firebase ещё не подключён", 14, green, true)
        text(cloudSection, "Текст этого блока отправляется в Google. Для учебного примера используйте открытые данные.", 14, muted)
        cloudInput = editor(cloudSection, "Текст для краткого содержания", R.id.cloud_input, 5).apply { setText(SAMPLE_ARTICLE) }
        cloudSettings = button(cloudSection, "Настроить Firebase", true) { showCloudSettings() }
        val generate = button(cloudSection, "Выделить 3 главные мысли") { hideKeyboard(); model.summarize(cloudInput.text.toString()) }
        cloudViews = results(cloudSection, generate)

        val localModelSection = section("05  Своя локальная модель", "LiteRT-LM · генерация на CPU")
        localModelInfo = text(localModelSection, "Модель ещё не выбрана", 14, green, true)
        text(localModelSection, "Пример: Qwen3-0.6B, 328 МиБ, Apache 2.0. После загрузки работает без интернета, Firebase и AICore. Можно выбрать другую совместимую модель .litertlm. GGUF и safetensors напрямую не подходят.", 14, muted)
        localModelInput = editor(localModelSection, "Короткий запрос к локальной модели", R.id.local_model_input, 2).apply {
            setText("Name the capital of France. Answer in one short sentence.")
        }
        val downloadModel = button(localModelSection, "Скачать Qwen3 · 328 МиБ", true) { hideKeyboard(); model.downloadLocalModel() }
        val importModel = button(localModelSection, "Выбрать свой файл .litertlm", true) { chooseLocalModel.launch(arrayOf("*/*")) }
        localModelGenerate = button(localModelSection, "Выполнить локально") { hideKeyboard(); model.generateLocal(localModelInput.text.toString()) }
        localModelViews = results(localModelSection, downloadModel, importModel, localModelGenerate)
        text(localModelSection, "Первый запуск включает загрузку модели в память. Для демонстрации ограничены контекст (512 токенов) и ответ (96 токенов). Скорость и совместимость зависят от модели и устройства.", 13, muted)

        val nanoSection = section("Gemini Nano", "Обзор в презентации")
        text(nanoSection, "Gemini Nano через AICore официально не поддерживает обычный Android Emulator. Для живого запуска нужен совместимый физический аппарат. В докладе показываем обзор API и примеры кода.", 15, ink)
        text(nanoSection, "Облачный Gemini выше выполняет запросы на сервере и требует интернет.", 14, muted)
        text(root, "Фотография: Razvan Antonescu / Unsplash, CC0 через Wikimedia Commons.\nПриложение не использует подставные AI-ответы.", 12, muted)
    }

    private fun showCloudSettings() {
        val current = model.state.value.cloudConfig
        val content = column(20)
        text(content, "1. Создайте проект в Firebase Console.\n2. Включите Firebase AI Logic с Gemini Developer API.\n3. Добавьте Android-приложение:\n$packageName\n4. Скачайте google-services.json и импортируйте его сюда.", 15, ink)
        text(content, "Для эмулятора Play Integrity настраивать не нужно. После первого запроса зарегистрируйте debug token из Logcat: Firebase Console → App Check → Apps → ⋮ → Manage debug tokens. Подробно: docs/FIREBASE_SETUP.md.", 13, muted)
        val modelField = editor(content, "Идентификатор модели", R.id.model_input, 1).apply {
            setText(current?.modelName ?: CloudConfig.DEFAULT_MODEL)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val importButton = button(content, "Импорт google-services.json") { chooseFirebase.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        importButton.contentDescription = "Выбрать конфигурацию Firebase Android"
        val panel = ScrollView(this).apply { addView(content) }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Подключение облачного Gemini")
            .setView(panel)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Закрыть", null)
            .apply { if (current != null) setNeutralButton("Сбросить") { _, _ -> model.clearCloudConfig() } }
            .create()
        importButton.setOnClickListener {
            dialog.dismiss()
            chooseFirebase.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val config = model.state.value.cloudConfig
                if (config == null) {
                    modelField.error = "Сначала импортируйте google-services.json"
                } else if (modelField.text.isBlank()) {
                    modelField.error = "Укажите идентификатор модели"
                } else {
                    model.saveCloudConfig(config.copy(modelName = modelField.text.toString().trim()))
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun loadImage(uri: Uri, forOcr: Boolean) = lifecycleScope.launch {
        try {
            val bitmap = withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                        val ratio = max(info.size.width, info.size.height).toFloat() / 1600f
                        if (ratio > 1) decoder.setTargetSize((info.size.width / ratio).toInt(), (info.size.height / ratio).toInt())
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
                    contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: error("Не удалось прочитать изображение")
                }
            }
            if (forOcr) { model.setOcrImage(bitmap); ocrImage.setImageBitmap(bitmap) }
            else { model.setLabelImage(bitmap); labelImage.setImageBitmap(bitmap) }
        } catch (error: Exception) {
            showError("Не удалось открыть изображение", error)
        }
    }

    private fun sampleTextBitmap(): Bitmap = Bitmap.createBitmap(1200, 660, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 52f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        listOf("ANDROID AI LAB", "On-device AI works without the internet.", "Download the translation models first.", "Then translate English text into Russian.").forEachIndexed { index, line ->
            paint.typeface = Typeface.create("sans-serif", if (index == 0) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(line, 60f, 110f + index * 120f, paint)
        }
    }

    private fun section(title: String, description: String): LinearLayout {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat(); cardElevation = 0f; strokeWidth = dp(1)
            strokeColor = Color.rgb(220, 227, 220); setCardBackgroundColor(Color.WHITE)
        }
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        return column(16).also { card.addView(it); text(it, title, 21, ink, true); text(it, description, 13, green, true) }
    }

    private fun column(padding: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

    private fun text(parent: LinearLayout, value: String, size: Int, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun editor(parent: LinearLayout, label: String, viewId: Int, lines: Int) = EditText(this).apply {
        id = viewId; hint = label; contentDescription = label; textSize = 16f
        setTextColor(ink); setHintTextColor(muted); minLines = lines; maxLines = max(lines, 8)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setPadding(dp(8), dp(8), dp(8), dp(8))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun image(parent: LinearLayout, height: Int, label: String) = ImageView(this).apply {
        contentDescription = label; scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundColor(Color.rgb(245, 247, 244))
        parent.addView(this, LinearLayout.LayoutParams(-1, dp(height)).apply { topMargin = dp(4); bottomMargin = dp(10) })
    }

    private fun buttons(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    private fun button(parent: LinearLayout, label: String, outlined: Boolean = false, action: () -> Unit): MaterialButton {
        val button = MaterialButton(this, null, if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
            text = label; isAllCaps = false; textSize = 14f; cornerRadius = dp(10)
            minHeight = dp(48); setOnClickListener { action() }
        }
        val horizontal = parent.orientation == LinearLayout.HORIZONTAL
        parent.addView(button, LinearLayout.LayoutParams(if (horizontal) 0 else -1, -2, if (horizontal) 1f else 0f).apply {
            if (horizontal && parent.childCount > 0) marginStart = dp(8)
            bottomMargin = dp(4)
        })
        return button
    }

    private fun results(parent: LinearLayout, vararg controls: View): FeatureViews {
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true; visibility = View.GONE
            parent.addView(this, LinearLayout.LayoutParams(-1, dp(4)).apply { bottomMargin = dp(8) })
        }
        val status = text(parent, "Готово к запуску", 13, muted)
        val result = text(parent, "", 16, ink).apply { setTextIsSelectable(true); visibility = View.GONE; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        return FeatureViews(progress, status, result, controls)
    }

    private inner class FeatureViews(val progress: ProgressBar, val status: TextView, val result: TextView, val controls: Array<out View>) {
        fun render(state: FeatureState) {
            progress.visibility = if (state.busy) View.VISIBLE else View.GONE
            status.text = state.message; status.setTextColor(if (state.failed) Color.rgb(170, 40, 40) else muted)
            result.text = state.result; result.visibility = if (state.result.isBlank()) View.GONE else View.VISIBLE
            controls.forEach { it.isEnabled = !state.busy }
        }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
        root.requestFocus()
    }

    private fun showError(title: String, error: Exception) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(error.localizedMessage ?: "Повторите попытку").setPositiveButton("Понятно", null).show()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()

    companion object {
        const val SAMPLE_TEXT = "On-device AI works without the internet. Download the translation models first. Then translate English text into Russian."
        const val SAMPLE_ARTICLE = """The university Android club is organizing a workshop on artificial intelligence in mobile apps. The workshop will take place on Friday at 16:00 in room 204. Students should bring a laptop with Android Studio and an Android emulator installed. The first part covers text recognition and offline translation using ML Kit. In the second part, participants will connect a cloud Gemini model to summarize text. No prior machine learning experience is required. Before the workshop, download the English and Russian translation models. The organizers will provide sample images and a Kotlin starter project. Registration closes on Thursday evening."""
    }
}
