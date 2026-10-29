package com.nokat.chat.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nokat.chat.R
import com.nokat.chat.data.model.User
import com.nokat.chat.data.remote.httpCode
import com.nokat.chat.data.remote.toUiText
import com.nokat.chat.data.repo.AuthRepository
import com.nokat.chat.data.repo.UserRepository
import com.nokat.chat.data.session.SessionManager
import com.nokat.chat.data.session.SessionState
import com.nokat.chat.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared by the first-run profile setup and the settings screen. */
class ProfileViewModel(
    private val users: UserRepository,
    private val auth: AuthRepository,
    session: SessionManager,
) : ViewModel() {

    private val initial: User? = session.currentUser

    val user: StateFlow<User?> = session.state
        .map { (it as? SessionState.LoggedIn)?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    var name by mutableStateOf(initial?.name.orEmpty())
        private set
    var username by mutableStateOf(initial?.username.orEmpty())
        private set
    var bio by mutableStateOf(initial?.bio.orEmpty())
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    init {
        viewModelScope.launch { users.refreshMe() }
    }

    fun onNameChange(value: String) {
        name = value.take(64)
        error = null
    }

    fun onUsernameChange(value: String) {
        username = value.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(32)
        error = null
    }

    fun onBioChange(value: String) {
        bio = value.take(160)
        error = null
    }

    fun isDirty(current: User?): Boolean =
        current == null || name.trim() != current.name || username != current.username || bio.trim() != current.bio

    fun save() {
        if (saving) return
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            error = UiText.Res(R.string.error_name_required)
            return
        }
        if (username.isNotEmpty() && !USERNAME_REGEX.matches(username)) {
            error = UiText.Res(R.string.error_username_invalid)
            return
        }
        saving = true
        error = null
        viewModelScope.launch {
            try {
                val updated = users.updateProfile(trimmedName, username, bio.trim())
                name = updated.name
                username = updated.username
                bio = updated.bio
                _saved.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = if (e.httpCode == 409) UiText.Res(R.string.error_username_taken) else e.toUiText()
            } finally {
                saving = false
            }
        }
    }

    fun logout() = auth.logout()

    private companion object {
        val USERNAME_REGEX = Regex("^[a-z0-9_]{3,32}$")
    }
}
