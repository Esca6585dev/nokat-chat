package com.nokat.chat.data.session

import com.nokat.chat.data.local.SessionStore
import com.nokat.chat.data.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val token: String, val user: User) : SessionState
}

/**
 * In-memory source of truth for the session. DataStore is read once at start-up and written
 * on every change, so the token is always available synchronously (for the OkHttp interceptor).
 */
class SessionManager(
    private val store: SessionStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    var token: String? = null
        private set

    val currentUser: User?
        get() = (_state.value as? SessionState.LoggedIn)?.user

    init {
        scope.launch {
            val stored = store.session.first()
            // Do not clobber a login that happened while we were reading.
            if (_state.value == SessionState.Loading) {
                if (stored != null) {
                    token = stored.token
                    _state.value = SessionState.LoggedIn(stored.token, stored.user)
                } else {
                    _state.value = SessionState.LoggedOut
                }
            }
        }
    }

    suspend fun login(token: String, user: User) {
        this.token = token
        _state.value = SessionState.LoggedIn(token, user)
        store.save(token, user)
    }

    suspend fun updateUser(user: User) {
        val current = _state.value as? SessionState.LoggedIn ?: return
        _state.value = current.copy(user = user)
        store.saveUser(user)
    }

    fun logout() {
        token = null
        _state.value = SessionState.LoggedOut
        scope.launch { store.clear() }
    }

    /** Called when the server rejected [rejectedToken]; ignored if the user has since re-logged in. */
    fun onUnauthorized(rejectedToken: String) {
        if (token != null && token == rejectedToken) logout()
    }
}
