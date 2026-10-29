package com.nokat.chat.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nokat.chat.data.remote.toUiText
import com.nokat.chat.data.repo.AuthRepository
import com.nokat.chat.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class OtpViewModel(
    val phone: String,
    initialDevCode: String?,
    private val auth: AuthRepository,
) : ViewModel() {

    sealed interface Event {
        /** [needsProfile] → profile setup, else → chat list. */
        data class Verified(val needsProfile: Boolean) : Event
        data object CodeResent : Event
    }

    var code by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    var devCode by mutableStateOf(initialDevCode)
        private set
    var secondsLeft by mutableIntStateOf(RESEND_SECONDS)
        private set

    /** Incremented on every failed attempt; the UI shakes the boxes. */
    var errorCount by mutableIntStateOf(0)
        private set

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private var countdownJob: Job? = null

    init {
        startCountdown()
    }

    fun onCodeChange(value: String) {
        if (loading) return
        val digits = value.filter { it.isDigit() }.take(CODE_LENGTH)
        code = digits
        error = null
        if (digits.length == CODE_LENGTH) verify()
    }

    fun fillDevCode() {
        devCode?.let { onCodeChange(it) }
    }

    fun verify() {
        if (loading || code.length != CODE_LENGTH) return
        loading = true
        viewModelScope.launch {
            try {
                val response = auth.verifyOtp(phone, code)
                _events.send(Event.Verified(response.isNew || response.user.name.isBlank()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
                code = ""
                errorCount++
            } finally {
                loading = false
            }
        }
    }

    fun resend() {
        if (secondsLeft > 0 || loading) return
        loading = true
        viewModelScope.launch {
            try {
                val response = auth.requestOtp(phone)
                if (response.devCode != null) devCode = response.devCode
                code = ""
                error = null
                startCountdown()
                _events.send(Event.CodeResent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            } finally {
                loading = false
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (s in RESEND_SECONDS downTo 0) {
                secondsLeft = s
                if (s > 0) delay(1_000)
            }
        }
    }

    companion object {
        const val CODE_LENGTH = 6
        const val RESEND_SECONDS = 30
    }
}
