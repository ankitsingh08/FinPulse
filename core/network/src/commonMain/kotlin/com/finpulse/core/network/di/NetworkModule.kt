package com.finpulse.core.network.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

const val APP_COROUTINE_SCOPE = "AppCoroutineScope"
const val DEFAULT_DISPATCHER = "DefaultDispatcher"

@Module
@ComponentScan("com.finpulse.core.network")
class NetworkModule {

    /**
     * INTERVIEW HIGHLIGHT:
     * Never use `GlobalScope` in production! Instead, provide an injectable application-level
     * `CoroutineScope` backed by a `SupervisorJob()` so that:
     * 1. A failure in one child job does not cancel sibling features.
     * 2. In unit tests, we can inject a `TestScope` (`backgroundScope`) to deterministically control virtual time.
     */
    @Single
    @Named(APP_COROUTINE_SCOPE)
    fun provideApplicationScope(
        @Named(DEFAULT_DISPATCHER) defaultDispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

    @Single
    @Named(DEFAULT_DISPATCHER)
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
