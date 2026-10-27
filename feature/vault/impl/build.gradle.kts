plugins {
    alias(libs.plugins.finpulse.kmp.feature.impl)
}

kotlin {
    androidLibrary {
        namespace = "com.finpulse.feature.vault.impl"
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.feature.vault.api)
            implementation(projects.core.security)
        }
    }
}
