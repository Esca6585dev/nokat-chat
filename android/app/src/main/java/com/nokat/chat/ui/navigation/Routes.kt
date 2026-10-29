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

@Serializable
object UserSearchRoute

@Serializable
object NewGroupRoute

@Serializable
data class ConversationRoute(val chatId: String)

@Serializable
object SettingsRoute
