plugins {
    alias(libs.plugins.finpulse.kmp.library)
}

kotlin {
    androidLibrary {
        namespace = "com.finpulse.core.designsystem"
    }
}
