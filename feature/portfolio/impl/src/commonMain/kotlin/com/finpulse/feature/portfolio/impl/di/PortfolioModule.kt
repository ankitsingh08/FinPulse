package com.finpulse.feature.portfolio.impl.di

import com.finpulse.core.network.di.NetworkModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [NetworkModule::class])
@ComponentScan("com.finpulse.feature.portfolio.impl")
class PortfolioModule
