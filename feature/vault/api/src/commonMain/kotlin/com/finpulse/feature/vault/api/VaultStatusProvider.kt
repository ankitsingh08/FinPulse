package com.finpulse.feature.vault.api

import kotlinx.coroutines.flow.StateFlow

/**
 * INTERVIEW HIGHLIGHT (Dependency Inversion via :api module):
 * `:feature:portfolio:impl` needs to know if the user's hardware vault is unlocked
 * before showing sensitive portfolio balances.
 * Instead of `:feature:portfolio` depending on `:feature:vault:impl` (which would couple the two
 * features and ruin parallel Gradle compilation), `:feature:portfolio:impl` depends ONLY on
 * `:feature:vault:api` and injects this lightweight interface.
 */
interface VaultStatusProvider {
    val isVaultUnlocked: StateFlow<Boolean>
    fun setVaultUnlocked(unlocked: Boolean)
}
