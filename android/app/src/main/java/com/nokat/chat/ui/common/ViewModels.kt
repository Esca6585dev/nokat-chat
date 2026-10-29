package com.nokat.chat.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nokat.chat.NokatApplication
import com.nokat.chat.di.AppContainer

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as NokatApplication).container

/**
 * Creates (or retrieves) a ViewModel scoped to the current NavBackStackEntry, built from the
 * manual [AppContainer]. The factory is only invoked the first time.
 */
@Composable
inline fun <reified VM : ViewModel> nokatViewModel(
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(
        factory = viewModelFactory {
            initializer { create(container) }
        },
    )
}
