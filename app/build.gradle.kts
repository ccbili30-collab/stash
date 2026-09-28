plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "io.github.ccbili30.stash"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.ccbili30.stash"
        minSdk = 30
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.2"
    }

    signingConfigs {
        // 自用 app：固定签名进仓库，保证任何机器构建出的 APK 签名一致、可覆盖安装
        create("release") {
            storeFile = file("stash.keystore")
            storePassword = "stash2026"
            keyAlias = "stash"
            keyPassword = "stash2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }
}

dependencies {
    // 钉死版本组合：material3 1.4（MaterialExpressiveTheme/MotionScheme）与 compose 1.9 同周期，
    // 兼容 compileSdk 36 + AGP 8.13；勿盲目升 BOM（新 compose 要求 AGP 9）
    val compose = "1.9.0"
    implementation("androidx.compose.animation:animation:$compose")
    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.ui:ui-tooling-preview:$compose")
    implementation("androidx.compose.foundation:foundation:$compose")
    implementation("androidx.compose.material3:material3:1.5.0-alpha08")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling:$compose")
}
