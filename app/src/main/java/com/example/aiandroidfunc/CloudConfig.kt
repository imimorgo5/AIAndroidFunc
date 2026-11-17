package com.example.aiandroidfunc

import android.content.Context
import org.json.JSONObject

data class CloudConfig(
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
    val modelName: String = DEFAULT_MODEL,
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-flash"

        /** Imports public Android Firebase configuration, never service-account keys. */
        fun fromGoogleServices(json: String, packageName: String): CloudConfig {
            val root = JSONObject(json)
            val clients = root.getJSONArray("client")
            val client = (0 until clients.length()).asSequence()
                .map { clients.getJSONObject(it) }
                .firstOrNull {
                    it.getJSONObject("client_info")
                        .getJSONObject("android_client_info")
                        .getString("package_name") == packageName
                } ?: error("В файле нет Android-приложения с пакетом $packageName")
            return CloudConfig(
                projectId = root.getJSONObject("project_info").getString("project_id"),
                applicationId = client.getJSONObject("client_info").getString("mobilesdk_app_id"),
                apiKey = client.getJSONArray("api_key").getJSONObject(0).getString("current_key"),
            ).also {
                require(it.projectId.isNotBlank() && it.applicationId.isNotBlank() && it.apiKey.isNotBlank()) {
                    "В google-services.json отсутствуют обязательные поля Firebase"
                }
            }
        }
    }
}

class CloudConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("firebase_demo_config", Context.MODE_PRIVATE)

    fun load(): CloudConfig? {
        val project = prefs.getString("project", null) ?: return null
        return CloudConfig(
            project, prefs.getString("app", "").orEmpty(),
            prefs.getString("key", "").orEmpty(),
            prefs.getString("model", CloudConfig.DEFAULT_MODEL).orEmpty(),
        )
    }

    fun save(config: CloudConfig) {
        prefs.edit().putString("project", config.projectId)
            .putString("app", config.applicationId).putString("key", config.apiKey)
            .putString("model", config.modelName).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
