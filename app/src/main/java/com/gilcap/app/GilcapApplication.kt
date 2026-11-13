package com.gilcap.app

import android.app.Application
import com.gilcap.app.di.AppContainer

class GilcapApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.vehicleStateSource.start()
        container.locationSource.start()
        container.capsuleDiscoveryFlow.start()
        println("GILCAP 앱 시작")
    }
}