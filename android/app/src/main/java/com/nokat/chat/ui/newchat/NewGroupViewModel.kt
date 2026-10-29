package com.nokat.chat.ui.newchat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.nokat.chat.R
import com.nokat.chat.data.model.User
import com.nokat.chat.data.remote.toUiText
import com.nokat.chat.data.repo.ChatRepository
import com.nokat.chat.data.repo.UserRepository
import com.nokat.chat.data.session.SessionManager
import com.nokat.chat.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class NewGroupViewModel(
    users: UserRepository,
    chats: ChatRepository,
    session: SessionManager,
) : UserSearchViewModel(users, chats, session) {

    var title by mutableStateOf("")
        private set

    /** Selected members, in selection order. */
    val selected = mutableStateListOf<User>()

    val canCreate: Boolean
        get() = title.isNotBlank() && selected.isNotEmpty() && !working

    fun onTitleChange(value: String) {
        title = value.take(64)
        error = null
    }

    fun toggle(user: User) {
        val index = selected.indexOfFirst { it.id == user.id }
        if (index >= 0) selected.removeAt(index) else selected.add(user)
        error = null
    }

    fun isSelected(user: User): Boolean = selected.any { it.id == user.id }

    fun create() {
        if (working) return
        if (title.isBlank() || selected.isEmpty()) {
            error = UiText.Res(R.string.error_group_invalid)
            return
        }
        working = true
        viewModelScope.launch {
            try {
                val chat = chats.createGroup(title.trim(), selected.map { it.id })
                emitOpenChat(chat.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            } finally {
                working = false
            }
        }
    }
}
