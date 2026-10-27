plugins {
    alias(libs.plugins.finpulse.kmp.library)
    alias(libs.plugins.finpulse.kmp.koin)
}

kotlin {
    androidLibrary {
        namespace = "com.finpulse.core.database"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.room.runtime)
        }
    }
}
