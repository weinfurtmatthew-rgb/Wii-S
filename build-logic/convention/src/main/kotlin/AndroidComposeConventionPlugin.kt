import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

/** Adds the Compose compiler and the BOM-aligned core Compose libraries to an Android module. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.withPlugin("com.android.library") {
            extensions.configure(LibraryExtension::class.java) { buildFeatures.compose = true }
        }
        pluginManager.withPlugin("com.android.application") {
            extensions.configure(ApplicationExtension::class.java) { buildFeatures.compose = true }
        }
        val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
        dependencies.apply {
            val bom = dependencies.platform(libs.findLibrary("compose-bom").get())
            add("implementation", bom)
            add("implementation", libs.findLibrary("compose-ui").get())
            add("implementation", libs.findLibrary("compose-foundation").get())
            add("implementation", libs.findLibrary("compose-material3").get())
            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
        }
    }
}
