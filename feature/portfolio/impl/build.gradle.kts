plugins {
    alias(libs.plugins.finpulse.kmp.feature.impl)
}

kotlin {
    androidLibrary {
        namespace = "com.finpulse.feature.portfolio.impl"
    }

    sourceSets {
        commonMain.dependencies {
            // INTERVIEW HIGHLIGHT:
            // `api` exposes the `:api` contract to consumers, while internal dependencies
            // (`:core:network`, `:core:database`, `:feature:vault:api`) use `implementation`
            // so changes inside `:impl` NEVER invalidate downstream module ABI!
            api(projects.feature.portfolio.api)
            implementation(projects.feature.vault.api)
            implementation(projects.core.network)
            implementation(projects.core.database)
            implementation(projects.core.designsystem)
        }
    }
}
