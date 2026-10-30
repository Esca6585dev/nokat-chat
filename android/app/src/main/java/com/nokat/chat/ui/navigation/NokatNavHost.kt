package com.nokat.chat.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nokat.chat.data.session.SessionState
import com.nokat.chat.di.AppContainer
import com.nokat.chat.ui.auth.LoginScreen
import com.nokat.chat.ui.auth.OtpScreen
import com.nokat.chat.ui.chats.ChatListScreen
import com.nokat.chat.ui.conversation.ConversationScreen
import com.nokat.chat.ui.newchat.NewGroupScreen
import com.nokat.chat.ui.newchat.UserSearchScreen
import com.nokat.chat.ui.profile.ProfileSetupScreen
import com.nokat.chat.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

/** Root: waits for the stored session (the splash stays up meanwhile), then shows the nav graph. */
@Composable
fun NokatRoot(container: AppContainer) {
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    val isLoading = session is SessionState.Loading
    val startDestination: Any? = remember(isLoading) {
        when (val s = session) {
            SessionState.Loading -> null
            SessionState.LoggedOut -> LoginRoute
            is SessionState.LoggedIn -> if (s.user.name.isBlank()) ProfileSetupRoute else ChatsRoute
        }
    }
    if (startDestination == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    } else {
        NokatNavHost(container, startDestination)
    }
}

private const val NAV_ANIM_MS = 300

@Composable
private fun NokatNavHost(container: AppContainer, startDestination: Any) {
    val navController = rememberNavController()

    // Session expired / logged out anywhere → back to login with a clean back stack.
    LaunchedEffect(navController) {
        container.sessionManager.state
            .map { it is SessionState.LoggedIn }
            .distinctUntilChanged()
            .drop(1)
            .collect { loggedIn ->
                if (!loggedIn) navController.resetTo(LoginRoute)
            }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(NAV_ANIM_MS, easing = FastOutSlowInEasing),
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(NAV_ANIM_MS, easing = FastOutSlowInEasing),
                targetOffset = { it / 4 },
            ) + fadeOut(animationSpec = tween(NAV_ANIM_MS), targetAlpha = 0.7f)
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(NAV_ANIM_MS, easing = FastOutSlowInEasing),
                initialOffset = { it / 4 },
            ) + fadeIn(animationSpec = tween(NAV_ANIM_MS), initialAlpha = 0.7f)
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(NAV_ANIM_MS, easing = FastOutSlowInEasing),
            )
        },
    ) {
        composable<LoginRoute> {
            LoginScreen(
                onCodeSent = { phone, devCode -> navController.navigate(OtpRoute(phone, devCode)) },
            )
        }
        composable<OtpRoute> { entry ->
            val route = entry.toRoute<OtpRoute>()
            OtpScreen(
                phone = route.phone,
                initialDevCode = route.devCode,
                onBack = { navController.popBackStack() },
                onVerified = { needsProfile ->
                    navController.resetTo(if (needsProfile) ProfileSetupRoute else ChatsRoute)
                },
            )
        }
        composable<ProfileSetupRoute> {
            ProfileSetupScreen(onDone = { navController.resetTo(ChatsRoute) })
        }
        composable<ChatsRoute> {
            ChatListScreen(
                onOpenChat = { chatId -> navController.navigate(ConversationRoute(chatId)) },
                onNewChat = { navController.navigate(UserSearchRoute()) },
                onNewSecretChat = { navController.navigate(UserSearchRoute(secret = true)) },
                onNewGroup = { navController.navigate(NewGroupRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<UserSearchRoute> { entry ->
            val route = entry.toRoute<UserSearchRoute>()
            UserSearchScreen(
                secretMode = route.secret,
                onBack = { navController.popBackStack() },
                onNewSecretChat = {
                    navController.navigate(UserSearchRoute(secret = true)) {
                        popUpTo<UserSearchRoute> { inclusive = true }
                    }
                },
                onNewGroup = {
                    navController.navigate(NewGroupRoute) {
                        popUpTo<UserSearchRoute> { inclusive = true }
                    }
                },
                onChatOpened = { chatId ->
                    navController.navigate(ConversationRoute(chatId)) {
                        popUpTo<UserSearchRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<NewGroupRoute> {
            NewGroupScreen(
                onBack = { navController.popBackStack() },
                onCreated = { chatId ->
                    navController.navigate(ConversationRoute(chatId)) {
                        popUpTo<NewGroupRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<ConversationRoute> { entry ->
            val route = entry.toRoute<ConversationRoute>()
            ConversationScreen(
                chatId = route.chatId,
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId ->
                    // e.g. "start secret chat" from a direct chat: replace the current conversation.
                    navController.navigate(ConversationRoute(chatId)) {
                        popUpTo<ConversationRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Navigates to [route] and clears the whole back stack. */
private fun NavHostController.resetTo(route: Any) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
