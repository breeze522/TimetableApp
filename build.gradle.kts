// Top-level build file where you can add configuration options common to all sub-projects/modules.
// AGP 9.x 内置 Kotlin 支持，无需再声明 kotlin-android 插件。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
