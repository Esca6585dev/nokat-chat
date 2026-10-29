package com.nokat.chat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nokat.chat.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "nokat_session")

data class StoredSession(val token: String, val user: User)

/** Persists the JWT and the signed-in user in DataStore (all I/O happens off the main thread). */
class SessionStore(context: Context, private val json: Json) {

    private val store: DataStore<Preferences> = context.applicationContext.sessionDataStore

    val session: Flow<StoredSession?> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val token = prefs[KEY_TOKEN]
            val userJson = prefs[KEY_USER]
            if (token.isNullOrBlank() || userJson.isNullOrBlank()) {
                null
            } else {
                runCatching { StoredSession(token, json.decodeFromString(User.serializer(), userJson)) }
                    .getOrNull()
            }
        }

    suspend fun save(token: String, user: User) {
        store.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_USER] = json.encodeToString(User.serializer(), user)
        }
    }

    suspend fun saveUser(user: User) {
        store.edit { prefs -> prefs[KEY_USER] = json.encodeToString(User.serializer(), user) }
    }

    suspend fun clear() {
        store.edit { prefs -> prefs.clear() }
    }

    private companion object {
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_USER = stringPreferencesKey("user")
    }
}
