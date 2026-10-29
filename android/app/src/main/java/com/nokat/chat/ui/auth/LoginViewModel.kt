package com.nokat.chat.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nokat.chat.R
import com.nokat.chat.data.remote.httpCode
import com.nokat.chat.data.remote.toUiText
import com.nokat.chat.data.repo.AuthRepository
import com.nokat.chat.util.PhoneUtils
import com.nokat.chat.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    data class CodeSent(val phone: String, val devCode: String?)

    var phone by mutableStateOf(
        TextFieldValue(PhoneUtils.DEFAULT_PREFIX, selection = TextRange(PhoneUtils.DEFAULT_PREFIX.length)),
    )
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set

    private val _events = Channel<CodeSent>(Channel.BUFFERED)
    val events: Flow<CodeSent> = _events.receiveAsFlow()

    fun onPhoneChange(value: TextFieldValue) {
        val sanitized = PhoneUtils.sanitizeInput(value.text)
        phone = if (sanitized == value.text) value else TextFieldValue(sanitized, TextRange(sanitized.length))
        error = null
    }

    fun submit() {
        if (loading) return
        val normalized = PhoneUtils.normalize(phone.text)
        if (normalized == null) {
            error = UiText.Res(R.string.error_invalid_phone)
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val response = auth.requestOtp(normalized)
                _events.send(CodeSent(normalized, response.devCode))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.httpCode == 429) {
                    // A code was sent less than 30 s ago — let the user type it in.
                    _events.send(CodeSent(normalized, null))
                } else {
                    error = e.toUiText()
                }
            } finally {
                loading = false
            }
        }
    }
}
