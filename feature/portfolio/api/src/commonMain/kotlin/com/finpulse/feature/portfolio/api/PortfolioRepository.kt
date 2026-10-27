package com.finpulse.feature.portfolio.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Public contract for `:feature:portfolio:api`.
 *
 * Notice that no Ktor, WebSocket, or database types leak into `:api`.
 */
interface PortfolioRepository {
    /**
     * Hot, multicasted state stream of the user's portfolio holdings merged with real-time ticks.
     * Backed by `stateIn(..., SharingStarted.WhileSubscribed(5_000), ...)`.
     */
    val portfolioState: StateFlow<PortfolioStreamState>

    /**
     * Cold/shared stream of holdings for simple consumers.
     */
    fun observeHoldings(): Flow<List<AssetHolding>>
}
