plugins {
    alias(libs.plugins.finpulse.kmp.library)
    alias(libs.plugins.finpulse.kmp.koin)
}

kotlin {
    androidLibrary {
        namespace = "com.finpulse.core.security"
    }
}
