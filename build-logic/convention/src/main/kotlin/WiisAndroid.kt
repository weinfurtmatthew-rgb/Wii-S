import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** Single source of truth for SDK levels. The launcher targets the AYN Thor only (Android 13 / API 33). */
object WiisSdk {
    const val COMPILE = 36
    const val MIN = 33
    const val TARGET = 35
}

internal fun Project.configureAndroidCommon(extension: CommonExtension<*, *, *, *, *, *>) {
    extension.apply {
        compileSdk = WiisSdk.COMPILE
        defaultConfig.minSdk = WiisSdk.MIN
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    extensions.configure(KotlinAndroidProjectExtension::class.java) {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}
