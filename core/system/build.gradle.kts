plugins {
    alias(libs.plugins.wiis.android.library)
}

android {
    namespace = "app.wiis.system"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
