import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.wiis.android.compose)
}

android {
    namespace = "app.wiis.launcher"
    compileSdk = WiisSdk.COMPILE

    defaultConfig {
        applicationId = "app.wiis.launcher"
        minSdk = WiisSdk.MIN
        targetSdk = WiisSdk.TARGET
        versionCode = 5
        versionName = "0.2.0-i2"
    }

    signingConfigs {
        // Development key committed on purpose so every CI/GitHub build installs over the last
        // one. NOT a release key: replace with a secret-backed key before any public release.
        create("dev") {
            storeFile = file("dev.keystore")
            storePassword = "wiisdev"
            keyAlias = "wiisdev"
            keyPassword = "wiisdev"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("dev")
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:system"))
    implementation(project(":emulator"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.jankstats)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
