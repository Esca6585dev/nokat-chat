package com.nokat.chat

import android.app.Application
import com.nokat.chat.di.AppContainer

class NokatApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Decrypted secret-chat files shared with other apps must not outlive a killed process.
        java.io.File(cacheDir, "secret").deleteRecursively()
        container = AppContainer(this)
    }
}
