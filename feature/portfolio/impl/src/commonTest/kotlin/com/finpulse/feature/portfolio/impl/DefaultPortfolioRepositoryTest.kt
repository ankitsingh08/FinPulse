package com.finpulse.feature.portfolio.impl

import app.cash.turbine.test
import com.finpulse.core.network.RawTickerTickDto
import com.finpulse.core.network.RealtimeSocketClient
import com.finpulse.core.network.TickerSocketDataSource
import com.finpulse.feature.portfolio.api.PortfolioStreamState
import com.finpulse.feature.vault.api.VaultStatusProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPortfolioRepositoryTest {

    private class FakeVaultStatusProvider(initialUnlocked: Boolean = true) : VaultStatusProvider {
        override val isVaultUnlocked: MutableStateFlow<Boolean> = MutableStateFlow(initialUnlocked)
        override fun setVaultUnlocked(unlocked: Boolean) {
            isVaultUnlocked.value = unlocked
        }
    }

    private class FakeTickerSocketDataSource : TickerSocketDataSource {
        var isConnected: Boolean = false
            private set
        var connectCount: Int = 0
            private set
        var disconnectCount: Int = 0
            private set

        private var activeListener: RealtimeSocketClient.Listener? = null

        override fun observeTickerStream(symbols: Set<String>): Flow<RawTickerTickDto> = callbackFlow {
            val listener = object : RealtimeSocketClient.Listener {
                override fun onConnected() {}
                override fun onTick(rawTick: RawTickerTickDto) {
                    trySend(rawTick)
                }
                override fun onDisconnected(cause: Throwable?) {
                    close(cause)
                }
            }
            isConnected = true
            connectCount++
            activeListener = listener

            awaitClose {
                isConnected = false
                disconnectCount++
                activeListener = null
            }
        }

        fun emitTick(symbol: String, priceMicros: Long, changePercent24h: Double = 1.5, timestamp: Long = 1000L) {
            activeListener?.onTick(
                RawTickerTickDto(
                    symbol = symbol,
                    priceMicros = priceMicros,
                    changePercent24h = changePercent24h,
                    timestampEpochMillis = timestamp,
                )
            )
        }
    }

    @Test
    fun whenVaultIsLocked_streamImmediatelyEmitsLockedAndDisconnectsSocket() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeVault = FakeVaultStatusProvider(initialUnlocked = true)
        val fakeSocket = FakeTickerSocketDataSource()

        val repository = DefaultPortfolioRepository(
            vaultStatusProvider = fakeVault,
            tickerSocketDataSource = fakeSocket,
            externalScope = backgroundScope,
            defaultDispatcher = testDispatcher,
        )

        repository.portfolioState.test {
            assertEquals(PortfolioStreamState.Connecting, awaitItem())
            runCurrent()

            // Initial seed state when unlocked
            val initialActive = assertIs<PortfolioStreamState.Active>(awaitItem())
            assertTrue(fakeSocket.isConnected)
            assertEquals(4, initialActive.holdings.size)

            // Emit a real-time BTC price update ($95,000.00)
            fakeSocket.emitTick(symbol = "BTC", priceMicros = 95_000_000_000L, changePercent24h = 2.8)
            runCurrent()

            val updatedActive = assertIs<PortfolioStreamState.Active>(awaitItem())
            val btc = updatedActive.holdings.first { it.symbol.value == "BTC" }
            assertEquals(95_000.0, btc.livePrice.usd)

            // Lock the vault -> flatMapLatest cancels the upstream callbackFlow -> awaitClose disconnects socket!
            fakeVault.setVaultUnlocked(false)
            runCurrent()

            assertEquals(PortfolioStreamState.Locked, awaitItem())
            assertFalse(fakeSocket.isConnected)
            assertEquals(1, fakeSocket.disconnectCount)
        }
    }

    @Test
    fun whileSubscribed5000_keepsSocketAliveDuringConfigChangeAndDisconnectsAfter5sInBackground() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeVault = FakeVaultStatusProvider(initialUnlocked = true)
        val fakeSocket = FakeTickerSocketDataSource()

        val repository = DefaultPortfolioRepository(
            vaultStatusProvider = fakeVault,
            tickerSocketDataSource = fakeSocket,
            externalScope = backgroundScope,
            defaultDispatcher = testDispatcher,
        )

        // Collector 1 (simulating Activity before rotation)
        repository.portfolioState.test {
            assertEquals(PortfolioStreamState.Connecting, awaitItem())
            runCurrent()
            assertIs<PortfolioStreamState.Active>(awaitItem())
            assertTrue(fakeSocket.isConnected)
            assertEquals(1, fakeSocket.connectCount)
            // Cancel collector (simulating Activity onStop during rotation or Home button press)
            cancelAndIgnoreRemainingEvents()
        }

        // Advance 2,000ms (< 5,000ms grace period): socket MUST still be connected!
        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(fakeSocket.isConnected, "Socket should remain open during 5s rotation window")
        assertEquals(0, fakeSocket.disconnectCount)

        // Advance remaining 3,001ms (> 5,000ms total): WhileSubscribed cancels upstream -> awaitClose runs!
        advanceTimeBy(3_001L)
        runCurrent()
        assertFalse(fakeSocket.isConnected, "Socket must disconnect after 5s timeout in background")
        assertEquals(1, fakeSocket.disconnectCount)
    }
}
