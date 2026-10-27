plugins {
    alias(libs.plugins.finpulse.kmp.library)
    alias(libs.plugins.finpulse.kmp.koin)
}

kotlin {
    androidLibrary {
        namespace = "com.finpulse.shared.umbrella"
    }

    // INTERVIEW HIGHLIGHT:
    // Only `:shared:umbrella` exports an iOS Framework (`FinPulseShared`).
    // Exporting multiple frameworks duplicates the Kotlin/Native runtime, bloats binary size,
    // and breaks singleton/type identity across frameworks.
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "FinPulseShared"
            isStatic = false
            export(projects.feature.portfolio.api)
            export(projects.feature.vault.api)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.feature.portfolio.api)
            api(projects.feature.vault.api)
            implementation(projects.feature.portfolio.impl)
            implementation(projects.feature.vault.impl)
            implementation(projects.core.network)
            implementation(projects.core.database)
            implementation(projects.core.security)
            implementation(projects.core.designsystem)
        }
    }
}
