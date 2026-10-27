package com.finpulse.android

import android.app.Application
import com.finpulse.shared.umbrella.initKoin
import org.koin.android.ext.koin.androidContext

class FinPulseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@FinPulseApplication)
        }
    }
}
