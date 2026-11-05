plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.eyeskeychain.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eyeskeychain.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "1.6"
    }

    // İsteğe bağlı sabit imza: app/goz.keystore dosyası VARSA onunla imzalanır (aynı anahtar = yeni APK eskisinin
    // üstüne kurulur). Dosya repoda yok (gizli). Yoksa Android'in normal debug anahtarı kullanılır.
    // Şifreler dosyada değil, ortam değişkeninde: GOZ_KEYSTORE_PASSWORD (anahtar şifresi aynı kabul edilir), GOZ_KEY_ALIAS (varsayılan "goz").
    val gozKeystore = file("goz.keystore")
    val gozPassword = System.getenv("GOZ_KEYSTORE_PASSWORD")
    val useGozKey = gozKeystore.exists() && !gozPassword.isNullOrEmpty()
    if (useGozKey) {
        signingConfigs {
            create("goz") {
                storeFile = gozKeystore
                storePassword = gozPassword
                keyAlias = System.getenv("GOZ_KEY_ALIAS") ?: "goz"
                keyPassword = gozPassword
            }
        }
    }

    buildTypes {
        debug {
            if (useGozKey) signingConfig = signingConfigs.getByName("goz")
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (useGozKey) signingConfigs.getByName("goz") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
