package com.finpulse.feature.vault.impl

import com.finpulse.feature.vault.api.VaultStatusProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

/**
 * INTERVIEW HIGHLIGHT:
 * Notice the `internal` visibility modifier!
 * Because this class lives in `:feature:vault:impl` and is marked `internal`, no other module
 * can accidentally instantiate or reference it directly—consumers are forced to program against
 * `VaultStatusProvider` from `:feature:vault:api`.
 */
@Single(binds = [VaultStatusProvider::class])
internal class DefaultVaultStatusProvider : VaultStatusProvider {
    private val _isVaultUnlocked = MutableStateFlow(true)
    override val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    override fun setVaultUnlocked(unlocked: Boolean) {
        _isVaultUnlocked.value = unlocked
    }
}
