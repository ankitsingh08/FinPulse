package com.finpulse.shared.umbrella

import com.finpulse.core.network.di.NetworkModule
import com.finpulse.feature.portfolio.impl.di.PortfolioModule
import com.finpulse.feature.vault.impl.di.VaultModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.ksp.generated.module

/**
 * INTERVIEW HIGHLIGHT:
 * Unified entry point for initializing Koin across both Android (`FinPulseApplication.onCreate`)
 * and iOS (`iOSApp.init()`).
 * Platform-specific bindings (like Android Context or iOS Keychain) can be passed via `platformModules`.
 */
fun initKoin(
    platformModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
) = startKoin {
    appDeclaration()
    modules(
        platformModules + listOf(
            NetworkModule().module,
            VaultModule().module,
            PortfolioModule().module,
        )
    )
}
