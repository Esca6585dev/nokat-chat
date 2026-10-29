package com.nokat.chat.data.remote

import com.nokat.chat.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to every request except `/auth/ *`, and logs the user out
 * when the server rejects the token with 401. Reads the token from memory only (never blocks).
 */
class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = sessionManager.token
        if (token == null || request.url.encodedPath.contains("/auth/")) {
            return chain.proceed(request)
        }
        val response = chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build(),
        )
        if (response.code == 401) {
            sessionManager.onUnauthorized(token)
        }
        return response
    }
}
