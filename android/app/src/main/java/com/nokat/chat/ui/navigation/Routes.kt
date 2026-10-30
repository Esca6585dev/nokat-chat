package com.nokat.chat.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose (2.8+) destinations.

@Serializable
object LoginRoute

@Serializable
data class OtpRoute(val phone: String, val devCode: String? = null)

@Serializable
object ProfileSetupRoute

@Serializable
object ChatsRoute

/** [secret] = true → picking a user starts an end-to-end encrypted secret chat. */
@Serializable
data class UserSearchRoute(val secret: Boolean = false)

@Serializable
object NewGroupRoute

@Serializable
data class ConversationRoute(val chatId: String)

@Serializable
object SettingsRoute
