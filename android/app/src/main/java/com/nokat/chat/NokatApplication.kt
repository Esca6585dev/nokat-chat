package com.nokat.chat

import android.app.Application
import com.nokat.chat.di.AppContainer

class NokatApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
