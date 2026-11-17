// LiteRT-LM 0.19.x publishes Kotlin 2.4 metadata. Keep built-in Kotlin and
// explicitly select a compatible KGP instead of suppressing metadata checks.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.android.library") version "9.4.1" apply false
}
