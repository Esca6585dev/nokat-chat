package com.nokat.chat.ui.newchat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nokat.chat.data.model.User
import com.nokat.chat.data.model.peer
import com.nokat.chat.data.remote.toUiText
import com.nokat.chat.data.repo.ChatRepository
import com.nokat.chat.data.repo.UserRepository
import com.nokat.chat.data.session.SessionManager
import com.nokat.chat.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Debounced user search shared by "new chat" and "new group".
 * With an empty query it suggests people from existing direct chats.
 */
open class UserSearchViewModel(
    private val users: UserRepository,
    protected val chats: ChatRepository,
    private val session: SessionManager,
) : ViewModel() {

    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<User>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        protected set

    /** Busy creating/opening a chat. */
    var working by mutableStateOf(false)
        protected set

    private val _openChat = Channel<String>(Channel.BUFFERED)
    val openChat: Flow<String> = _openChat.receiveAsFlow()

    /** People from existing direct chats, most recent first. */
    val recentPeople: List<User>
        get() {
            val me = session.currentUser?.id
            return chats.chats.value
                .filter { !it.isGroup }
                .mapNotNull { it.peer(me) }
                .filter { it.id != me }
                .distinctBy { it.id }
        }

    init {
        observeQuery()
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        viewModelScope.launch {
            snapshotFlow { query }
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(300)
                .collectLatest { q ->
                    if (q.isEmpty()) {
                        results = emptyList()
                        searching = false
                        return@collectLatest
                    }
                    searching = true
                    try {
                        results = users.search(q)
                        error = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.toUiText()
                    } finally {
                        searching = false
                    }
                }
        }
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun openDirectChat(user: User) {
        if (working) return
        working = true
        viewModelScope.launch {
            try {
                val chat = chats.openDirectChat(user.id)
                _openChat.send(chat.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            } finally {
                working = false
            }
        }
    }

    protected suspend fun emitOpenChat(chatId: String) {
        _openChat.send(chatId)
    }
}
