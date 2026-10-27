package com.finpulse.shared.umbrella

import com.finpulse.feature.portfolio.api.PortfolioRepository
import com.finpulse.feature.portfolio.api.PortfolioStreamState
import com.finpulse.feature.vault.api.VaultStatusProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * INTERVIEW HIGHLIGHT (iOS Swift/Kotlin Coroutines Interop):
 * Kotlin `StateFlow<PortfolioStreamState>` compiles to Objective-C/Swift without generic type parameters
 * on interfaces unless bridged or wrapped (or using SKIE / KMP-NativeCoroutines).
 *
 * Providing a clean, lifecycle-aware `IosPortfolioBridge` in `:shared:umbrella` (`iosMain`) lets
 * SwiftUI `ObservableObject` subscribe with a main-thread callback and cancel deterministically in `deinit`
 * or `onDisappear`, triggering `WhileSubscribed(5_000)` on iOS just like Android!
 */
interface CloseableSubscription {
    fun close()
}

class IosPortfolioBridge : KoinComponent {
    private val portfolioRepository: PortfolioRepository by inject()
    private val vaultStatusProvider: VaultStatusProvider by inject()

    fun isVaultUnlocked(): Boolean = vaultStatusProvider.isVaultUnlocked.value

    fun toggleVault() {
        val current = vaultStatusProvider.isVaultUnlocked.value
        vaultStatusProvider.setVaultUnlocked(!current)
    }

    fun observePortfolioState(onEachState: (PortfolioStreamState) -> Unit): CloseableSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        portfolioRepository.portfolioState
            .onEach { state -> onEachState(state) }
            .launchIn(scope)

        return object : CloseableSubscription {
            override fun close() {
                scope.cancel()
            }
        }
    }
}
