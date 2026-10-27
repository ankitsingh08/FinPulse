package com.finpulse.buildlogic

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * INTERVIEW HIGHLIGHT:
 * Configures Koin + Koin Annotations (KSP) across Multiplatform targets AND enables
 * `KOIN_CONFIG_CHECK = true` so missing dependencies fail at compile time (just like Dagger/Hilt)
 * instead of crashing at runtime!
 */
class KmpKoinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.google.devtools.ksp")
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.commonMain {
                    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
                    dependencies {
                        implementation(libs.findLibrary("koin-core").get())
                        api(libs.findLibrary("koin-annotations").get())
                    }
                }
                sourceSets.commonTest.dependencies {
                    implementation(libs.findLibrary("koin-test").get())
                }
            }

            extensions.configure<KspExtension> {
                // Compile-time DI graph safety check!
                arg("KOIN_CONFIG_CHECK", "true")
            }

            dependencies {
                add("kspCommonMainMetadata", libs.findLibrary("koin-ksp-compiler").get())
            }

            tasks.withType<KotlinCompilationTask<*>>().configureEach {
                if (name != "compileCommonMainKotlinMetadata") {
                    dependsOn("kspCommonMainKotlinMetadata")
                }
            }
        }
    }
}
