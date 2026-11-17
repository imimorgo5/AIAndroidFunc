plugins {
    id("com.android.library")
}

android {
    namespace = "com.example.aiandroidfunc.examples"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    implementation("com.google.mlkit:genai-summarization:1.0.0-beta1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
}
