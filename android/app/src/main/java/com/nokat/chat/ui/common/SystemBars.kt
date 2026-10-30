package com.nokat.chat.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Sets light/dark status bar icons while this composable is on screen. */
@Composable
fun StatusBarAppearance(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(darkIcons) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
        }
        onDispose { }
    }
}

/**
 * Sets FLAG_SECURE on the activity window while [enabled] (no screenshots, screen recording or
 * recents thumbnail). Reference-counted so overlapping screens during navigation don't clear it.
 */
@Composable
fun SecureWindow(enabled: Boolean) {
    val view = LocalView.current
    if (!enabled || view.isInEditMode) return
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            SecureWindowCounter.count++
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (window != null) {
                SecureWindowCounter.count--
                if (SecureWindowCounter.count <= 0) {
                    SecureWindowCounter.count = 0
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }
}

/** Main-thread only. */
private object SecureWindowCounter {
    var count = 0
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
