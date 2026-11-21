plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.debanshu.speech.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.debanshu.speech.sample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":composeApp"))
    implementation("androidx.activity:activity-compose:1.13.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    androidTestImplementation(files(rootProject.file("composeApp/libs/sherpa-onnx-2.0.1.aar"))
        .builtBy(rootProject.tasks.named("prepareAndroidSpeech")))
}
