package com.vdigital.volumestream

import android.app.Application
import com.vditital.data.util.AppLogger


class AndroidApp : Application() {


    override fun onCreate() {
        super.onCreate()
        AppLogger.init()
        instance = this
        initKoinIfNeeded()
    }

    companion object {
       lateinit var instance: AndroidApp

        fun getAppInstance(): AndroidApp {
            return instance
        }
    }
}