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
        versionCode = 12
        versionName = "0.5.1-i5"
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Screenshot tests render real screens with Robolectric. They are slow and download an
                // Android image, so they only run on request:
                //   ./gradlew :app:testDebugUnitTest -Pscreenshots [-ProbolectricRepo=<maven mirror>] [-PwiisFonts=<dir of TTFs>]
                if (!project.hasProperty("screenshots")) {
                    test.exclude("**/screenshots/**")
                } else {
                    test.maxHeapSize = "2g"
                    test.systemProperty("roborazzi.test.record", "true")
                    test.systemProperty("wiis.screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.path)
                    project.findProperty("robolectricRepo")?.let {
                        test.systemProperty("robolectric.dependency.repo.url", it)
                        test.systemProperty("robolectric.dependency.repo.id", "mirror")
                    }
                    project.findProperty("wiisFonts")?.let { test.systemProperty("wiis.fonts", it) }
                }
            }
        }
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
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    // Host activity for Compose tests; debug builds only.
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
