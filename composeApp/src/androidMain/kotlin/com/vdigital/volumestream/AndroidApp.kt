package com.vdigital.volumestream

import android.app.Application
import com.vdigital.volumestream.di.appModule
import com.vditital.data.util.AppLogger
import org.koin.core.context.startKoin


class AndroidApp : Application() {


    override fun onCreate() {
        super.onCreate()
        AppLogger.init()
        instance = this
        startKoin {
            configureKoin()
            modules(appModule)
        }
    }

    companion object {
       lateinit var instance: AndroidApp

        fun getAppInstance(): AndroidApp {
            return instance
        }
    }
}