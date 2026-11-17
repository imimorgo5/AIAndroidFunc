plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.aiandroidfunc"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.aiandroidfunc"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("androidx.activity:activity-ktx:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    // Bundled vision models are ready without a first-run download.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.firebase:firebase-ai:18.1.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.1")
    debugImplementation("com.google.firebase:firebase-appcheck-debug:20.0.0")
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity:20.0.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
