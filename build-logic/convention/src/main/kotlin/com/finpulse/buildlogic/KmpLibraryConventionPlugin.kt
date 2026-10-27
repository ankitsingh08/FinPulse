package com.finpulse.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * INTERVIEW HIGHLIGHT:
 * Centralized KMP Library Convention Plugin using the AGP 9+ `com.android.kotlin.multiplatform.library` plugin.
 * Every KMP module automatically gets:
 * - Android target (compileSdk 36, minSdk 26, JVM 17)
 * - iOS targets (iosArm64 for real devices, iosSimulatorArm64 for Apple Silicon Macs)
 * - Explicit API mode / strict compiler flags option
 * - Coroutines in commonMain and commonTest
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.kotlin.multiplatform.library")
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<KotlinMultiplatformExtension> {
                // AGP 9+ dedicated KMP Android library target
                targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
                    compileSdk = 36
                    minSdk = 26
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }

                // iOS Targets (Device + Apple Silicon Simulator)
                iosArm64()
                iosSimulatorArm64()

                sourceSets.apply {
                    commonMain.dependencies {
                        implementation(libs.findLibrary("kotlinx-coroutines-core").get())
                    }
                    commonTest.dependencies {
                        implementation(kotlin("test"))
                        implementation(libs.findLibrary("kotlinx-coroutines-test").get())
                        implementation(libs.findLibrary("turbine").get())
                    }
                }
            }
        }
    }
}
