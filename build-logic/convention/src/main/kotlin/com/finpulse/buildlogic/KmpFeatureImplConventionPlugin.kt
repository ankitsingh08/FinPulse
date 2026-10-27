package com.finpulse.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * INTERVIEW HIGHLIGHT:
 * Standardizes `:feature:x:impl` modules by composing `finpulse.kmp.library` + `finpulse.kmp.koin`.
 * Note how `:feature:x:api` modules ONLY apply `finpulse.kmp.library` (staying ultra-lightweight
 * with zero KSP or heavy framework overhead).
 */
class KmpFeatureImplConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("finpulse.kmp.library")
                apply("finpulse.kmp.koin")
            }
        }
    }
}
