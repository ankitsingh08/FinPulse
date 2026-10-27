pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

// INTERVIEW HIGHLIGHT: Enables compile-time safe module references like `projects.core.network`
// instead of string-based `project(":core:network")`.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "FinPulse"

// 1. Lightweight App Entry Shell
include(":androidApp")

// 2. Single iOS XCFramework Umbrella & Root Di Graph
include(":shared:umbrella")

// 3. Core Multiplatform Infrastructure Modules
include(":core:network")
include(":core:database")
include(":core:security")
include(":core:designsystem")

// 4. Feature Modules (:api and :impl split for ABI isolation & parallel compilation)
include(":feature:portfolio:api")
include(":feature:portfolio:impl")
include(":feature:vault:api")
include(":feature:vault:impl")
