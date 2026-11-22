plugins { id("com.android.application") }

android {
    namespace = "io.github.debanshu.siren.validation.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.debanshu.siren.validation.android"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "1"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("io.github.debanshu777:siren:${providers.gradleProperty("sirenVersion").getOrElse("0.1.0")}")
}
