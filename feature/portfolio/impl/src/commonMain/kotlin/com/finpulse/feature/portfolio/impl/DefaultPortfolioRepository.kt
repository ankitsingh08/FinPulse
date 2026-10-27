package com.finpulse.feature.portfolio.impl

import com.finpulse.core.network.RawTickerTickDto
import com.finpulse.core.network.TickerSocketDataSource
import com.finpulse.core.network.di.APP_COROUTINE_SCOPE
import com.finpulse.core.network.di.DEFAULT_DISPATCHER
import com.finpulse.feature.portfolio.api.AssetHolding
import com.finpulse.feature.portfolio.api.PortfolioRepository
import com.finpulse.feature.portfolio.api.PortfolioStreamState
import com.finpulse.feature.portfolio.api.PriceMicros
import com.finpulse.feature.portfolio.api.QuantityMicros
import com.finpulse.feature.portfolio.api.TickerSymbol
import com.finpulse.feature.vault.api.VaultStatusProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Named
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.math.min

/**
 * INTERVIEW HIGHLIGHT: Staff-Grade Reactive Ticker Pipeline
 *
 * 1. `flatMapLatest` on `vaultStatusProvider.isVaultUnlocked`:
 *    - When the Vault locks (`false`), `flatMapLatest` immediately cancels the active WebSocket stream
 *      (invoking `awaitClose { socketClient.disconnect() }`) and emits `PortfolioStreamState.Locked`,
 *      wiping sensitive holdings from memory!
 * 2. `runningFold`:
 *    - Accumulates single-ticker deltas into an immutable map of holdings on a background dispatcher.
 * 3. `conflate()`:
 *    - Coalesces high-frequency 50ms socket bursts if the downstream collector is busy rendering a frame.
 * 4. `retryWhen`:
 *    - Exponential backoff with cap on transient socket disconnects (note: `retryWhen` never catches
 *      downstream `CancellationException`).
 * 5. `stateIn(..., SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L, replayExpirationMillis = 0L), ...)`:
 *    - Multicasts a single WebSocket connection across all UI collectors.
 *    - Survives Android configuration changes (5s grace period) without reconnecting the socket.
 *    - Disconnects the socket 5s after the app goes to background (`onStop`) to save battery.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [PortfolioRepository::class])
internal class DefaultPortfolioRepository(
    @Provided private val vaultStatusProvider: VaultStatusProvider,
    private val tickerSocketDataSource: TickerSocketDataSource,
    @Named(APP_COROUTINE_SCOPE) private val externalScope: CoroutineScope,
    @Named(DEFAULT_DISPATCHER) private val defaultDispatcher: CoroutineDispatcher,
) : PortfolioRepository {

    private data class SeedAsset(
        val symbol: TickerSymbol,
        val name: String,
        val quantity: QuantityMicros,
        val initialPrice: PriceMicros,
    )

    private val seedAssets = linkedMapOf(
        "BTC" to SeedAsset(
            symbol = TickerSymbol("BTC"),
            name = "Bitcoin",
            quantity = QuantityMicros.fromUnits(1.25),
            initialPrice = PriceMicros.fromUsd(92_400.0),
        ),
        "ETH" to SeedAsset(
            symbol = TickerSymbol("ETH"),
            name = "Ethereum",
            quantity = QuantityMicros.fromUnits(14.0),
            initialPrice = PriceMicros.fromUsd(3_150.0),
        ),
        "SOL" to SeedAsset(
            symbol = TickerSymbol("SOL"),
            name = "Solana",
            quantity = QuantityMicros.fromUnits(85.0),
            initialPrice = PriceMicros.fromUsd(188.50),
        ),
        "NVDA" to SeedAsset(
            symbol = TickerSymbol("NVDA"),
            name = "NVIDIA Corp.",
            quantity = QuantityMicros.fromUnits(40.0),
            initialPrice = PriceMicros.fromUsd(138.20),
        ),
    )

    private fun initialHoldingsMap(): Map<String, AssetHolding> =
        seedAssets.mapValues { (_, seed) ->
            AssetHolding(
                symbol = seed.symbol,
                name = seed.name,
                quantity = seed.quantity,
                livePrice = seed.initialPrice,
                changePercent24h = 0.0,
                lastUpdatedEpochMillis = 0L,
            )
        }

    override val portfolioState: StateFlow<PortfolioStreamState> =
        vaultStatusProvider.isVaultUnlocked
            .flatMapLatest { isUnlocked ->
                if (!isUnlocked) {
                    flowOf(PortfolioStreamState.Locked)
                } else {
                    tickerSocketDataSource
                        .observeTickerStream(seedAssets.keys)
                        .retryWhen { _, attempt ->
                            val backoffMs = min(10_000L, 500L * (1L shl attempt.toInt().coerceAtMost(5)))
                            delay(backoffMs)
                            true
                        }
                        .runningFold(initialHoldingsMap()) { accMap, tick: RawTickerTickDto ->
                            val existing = accMap[tick.symbol] ?: return@runningFold accMap
                            val updated = existing.copy(
                                livePrice = PriceMicros(tick.priceMicros),
                                changePercent24h = tick.changePercent24h,
                                lastUpdatedEpochMillis = tick.timestampEpochMillis,
                            )
                            accMap + (tick.symbol to updated)
                        }
                        .conflate()
                        .map { holdingsMap ->
                            PortfolioStreamState.Active(holdings = holdingsMap.values.toList())
                        }
                        .flowOn(defaultDispatcher)
                }
            }
            .stateIn(
                scope = externalScope,
                started = SharingStarted.WhileSubscribed(
                    stopTimeoutMillis = 5_000L,
                    replayExpirationMillis = 0L,
                ),
                initialValue = PortfolioStreamState.Connecting,
            )

    override fun observeHoldings(): Flow<List<AssetHolding>> =
        portfolioState.map { state ->
            when (state) {
                is PortfolioStreamState.Active -> state.holdings
                PortfolioStreamState.Connecting,
                PortfolioStreamState.Locked -> emptyList()
            }
        }
}
