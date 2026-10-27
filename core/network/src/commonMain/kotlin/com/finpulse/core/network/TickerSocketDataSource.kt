package com.finpulse.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Single
import kotlin.math.roundToLong

@Serializable
data class RawTickerTickDto(
    val symbol: String,
    val priceMicros: Long,
    val changePercent24h: Double,
    val timestampEpochMillis: Long,
)

/**
 * Simulates a legacy or 3rd-party callback-based WebSocket client (like OkHttp `WebSocketListener`
 * or iOS `URLSessionWebSocketTask`) to demonstrate bridging imperative callbacks to Kotlin Flow.
 */
interface RealtimeSocketClient {
    interface Listener {
        fun onConnected()
        fun onTick(rawTick: RawTickerTickDto)
        fun onDisconnected(cause: Throwable?)
    }

    fun connect(symbols: Set<String>, listener: Listener)
    fun disconnect()
}

interface TickerSocketDataSource {
    fun observeTickerStream(symbols: Set<String>): Flow<RawTickerTickDto>
}

/**
 * INTERVIEW HIGHLIGHT:
 * 1. Uses `callbackFlow` to bridge a callback-based WebSocket API into a cold `Flow`.
 * 2. Uses `trySend` (non-blocking) inside callbacks combined with `.buffer(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)`
 *    so a slow downstream consumer NEVER blocks the network socket callback thread.
 * 3. Guarantees deterministic resource cleanup via `awaitClose { client.disconnect() }` whenever the
 *    downstream collector cancels (e.g., 5 seconds after the user backgrounds the app via `WhileSubscribed(5_000)`).
 */
@Single(binds = [TickerSocketDataSource::class])
internal class DefaultTickerSocketDataSource(
    private val socketClient: RealtimeSocketClient,
) : TickerSocketDataSource {

    override fun observeTickerStream(symbols: Set<String>): Flow<RawTickerTickDto> = callbackFlow {
        val listener = object : RealtimeSocketClient.Listener {
            override fun onConnected() {
                // Socket connected
            }

            override fun onTick(rawTick: RawTickerTickDto) {
                // Non-blocking send into Channel; if buffer is full, DROP_OLDEST handles backpressure.
                trySend(rawTick)
            }

            override fun onDisconnected(cause: Throwable?) {
                // Closing the ProducerScope propagates the exception downstream so `.retryWhen` can handle it.
                close(cause)
            }
        }

        socketClient.connect(symbols, listener)

        // CRITICAL: Suspended until downstream cancels or `close()` is called.
        // Invoked deterministically on Structured Concurrency cancellation.
        awaitClose {
            socketClient.disconnect()
        }
    }.buffer(
        capacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
}

/**
 * High-frequency simulated WebSocket engine that emits realistic price ticks every 50ms (20 ticks/sec)
 * to exercise backpressure, conflation, and `CancellationException` safety.
 */
@Single(binds = [RealtimeSocketClient::class])
internal class SimulatedRealtimeSocketClient : RealtimeSocketClient {

    private var producerJob: Job? = null
    private val socketScope = CoroutineScope(Dispatchers.Default)

    private val basePricesUsd = mapOf(
        "BTC" to 92_400.0,
        "ETH" to 3_150.0,
        "SOL" to 188.50,
        "NVDA" to 138.20,
    )

    override fun connect(symbols: Set<String>, listener: RealtimeSocketClient.Listener) {
        disconnect()
        producerJob = socketScope.launch {
            listener.onConnected()
            var tickCount = 0L
            while (isActive) {
                try {
                    delay(50L) // 20 Hz high-frequency tick burst
                    tickCount++
                    for (symbol in symbols) {
                        val base = basePricesUsd[symbol] ?: 100.0
                        // Deterministic wave oscillation for reproducible UI & tests
                        val deltaRatio = ((tickCount % 20) - 10) * 0.0005
                        val liveUsd = base * (1.0 + deltaRatio)
                        val micros = (liveUsd * 1_000_000.0).roundToLong()
                        listener.onTick(
                            RawTickerTickDto(
                                symbol = symbol,
                                priceMicros = micros,
                                changePercent24h = (deltaRatio * 100.0),
                                timestampEpochMillis = 1_761_316_000_000L + (tickCount * 50L),
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    // INTERVIEW HIGHLIGHT:
                    // NEVER swallow CancellationException! Always rethrow so structured concurrency
                    // can cooperatively cancel the coroutine.
                    throw e
                } catch (t: Throwable) {
                    listener.onDisconnected(t)
                    break
                }
            }
        }
    }

    override fun disconnect() {
        producerJob?.cancel()
        producerJob = null
    }
}
