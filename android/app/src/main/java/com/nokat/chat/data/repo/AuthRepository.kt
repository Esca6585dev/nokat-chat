package com.nokat.chat.data.repo

import com.nokat.chat.data.model.OtpRequest
import com.nokat.chat.data.model.OtpResponse
import com.nokat.chat.data.model.VerifyRequest
import com.nokat.chat.data.model.VerifyResponse
import com.nokat.chat.data.remote.NokatApi
import com.nokat.chat.data.session.SessionManager

class AuthRepository(
    private val api: NokatApi,
    private val session: SessionManager,
) {
    suspend fun requestOtp(phone: String): OtpResponse = api.requestOtp(OtpRequest(phone))

    /** Verifies the code and persists the session (token + user). */
    suspend fun verifyOtp(phone: String, code: String): VerifyResponse {
        val response = api.verifyOtp(VerifyRequest(phone, code))
        session.login(response.token, response.user)
        return response
    }

    fun logout() = session.logout()
}
