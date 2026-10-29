package com.nokat.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nokat.chat.data.session.SessionState
import com.nokat.chat.ui.navigation.NokatRoot
import com.nokat.chat.ui.theme.NokatTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NokatApplication).container
        // Keep the splash until the stored session has been read from DataStore.
        splash.setKeepOnScreenCondition {
            container.sessionManager.state.value is SessionState.Loading
        }

        setContent {
            NokatTheme {
                NokatRoot(container)
            }
        }
    }
}
