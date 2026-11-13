plugins {
    alias(libs.plugins.wiis.android.library)
    alias(libs.plugins.wiis.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.wiis.designsystem"
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
