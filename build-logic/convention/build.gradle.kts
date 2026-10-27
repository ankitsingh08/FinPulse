plugins {
    `kotlin-dsl`
}

group = "com.finpulse.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "finpulse.kmp.library"
            implementationClass = "com.finpulse.buildlogic.KmpLibraryConventionPlugin"
        }
        register("kmpKoin") {
            id = "finpulse.kmp.koin"
            implementationClass = "com.finpulse.buildlogic.KmpKoinConventionPlugin"
        }
        register("kmpFeatureImpl") {
            id = "finpulse.kmp.feature.impl"
            implementationClass = "com.finpulse.buildlogic.KmpFeatureImplConventionPlugin"
        }
    }
}
