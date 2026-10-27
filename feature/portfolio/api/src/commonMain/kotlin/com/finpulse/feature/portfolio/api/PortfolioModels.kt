package com.finpulse.feature.portfolio.api

import kotlin.jvm.JvmInline

/**
 * INTERVIEW HIGHLIGHT: Zero-Allocation Domain Modeling with `@JvmInline value class`
 *
 * In a high-frequency financial ticker (50–100 ticks/sec), wrapping primitives (`String`, `Long`, `Double`)
 * in regular `data class` wrappers allocates thousands of short-lived heap objects per minute,
 * triggering GC pressure and frame drops.
 *
 * `@JvmInline value class` gives us compile-time type safety (impossible to accidentally pass
 * `PriceMicros` where `QuantityMicros` is expected) while erasing to the underlying primitive
 * (`String` or `Long`) in JVM bytecode whenever not boxed inside a nullable or generic container.
 */
@JvmInline
value class TickerSymbol(val value: String) {
    init {
        require(value.isNotBlank()) { "TickerSymbol cannot be blank" }
    }
}

/**
 * Represents monetary price in micro-USD (1 USD = 1,000,000 micros) to avoid IEEE 754 floating-point
 * precision bugs (`0.1 + 0.2 != 0.3`) and avoid `BigDecimal` heap allocations in hot paths.
 */
@JvmInline
value class PriceMicros(val micros: Long) {
    val usd: Double get() = micros / 1_000_000.0

    companion object {
        fun fromUsd(usd: Double): PriceMicros = PriceMicros((usd * 1_000_000L).toLong())
    }
}

/**
 * Represents asset quantity in micro-units (1.0 unit = 1,000,000 micro-units).
 */
@JvmInline
value class QuantityMicros(val micros: Long) {
    val units: Double get() = micros / 1_000_000.0

    companion object {
        fun fromUnits(units: Double): QuantityMicros = QuantityMicros((units * 1_000_000L).toLong())
    }
}

data class AssetHolding(
    val symbol: TickerSymbol,
    val name: String,
    val quantity: QuantityMicros,
    val livePrice: PriceMicros,
    val changePercent24h: Double,
    val lastUpdatedEpochMillis: Long,
) {
    /**
     * Convenience properties for iOS Objective-C/Swift interop, where `@JvmInline value class`
     * wrapping `String` (`Any`) or `Long` (`Int64`) loses its helper getters across the Obj-C header.
     */
    val symbolCode: String get() = symbol.value
    val quantityUnits: Double get() = quantity.units
    val livePriceUsd: Double get() = livePrice.usd
    val totalValueUsd: Double
        get() = quantity.units * livePrice.usd
}

sealed interface PortfolioStreamState {
    data object Locked : PortfolioStreamState
    data object Connecting : PortfolioStreamState
    data class Active(
        val holdings: List<AssetHolding>,
        val totalPortfolioUsd: Double = holdings.sumOf { it.totalValueUsd },
    ) : PortfolioStreamState
}
