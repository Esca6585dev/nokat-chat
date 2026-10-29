package com.nokat.chat.data.repo

import com.nokat.chat.data.model.UpdateProfileRequest
import com.nokat.chat.data.model.User
import com.nokat.chat.data.remote.NokatApi
import com.nokat.chat.data.remote.decodeList
import com.nokat.chat.data.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class UserRepository(
    private val api: NokatApi,
    private val json: Json,
    private val session: SessionManager,
) {
    suspend fun search(query: String): List<User> =
        json.decodeList(api.searchUsers(query), User.serializer())

    suspend fun updateProfile(name: String, username: String, bio: String): User {
        val current = session.currentUser
        val request = UpdateProfileRequest(
            name = name,
            // Only send an empty username when clearing an existing one.
            username = username.takeIf { it.isNotEmpty() || !current?.username.isNullOrEmpty() },
            bio = bio,
        )
        val user = api.updateMe(request)
        session.updateUser(user)
        return user
    }

    suspend fun refreshMe() {
        try {
            session.updateUser(api.me())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Non-fatal: keep the cached profile.
        }
    }
}
