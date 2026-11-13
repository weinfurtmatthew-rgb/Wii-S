plugins {
    alias(libs.plugins.wiis.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.wiis.emulator"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
