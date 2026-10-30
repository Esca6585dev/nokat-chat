package com.nokat.chat.data.remote

import com.nokat.chat.data.model.Attachment
import com.nokat.chat.data.model.Chat
import com.nokat.chat.data.model.DirectChatRequest
import com.nokat.chat.data.model.GroupChatRequest
import com.nokat.chat.data.model.Message
import com.nokat.chat.data.model.OkResponse
import com.nokat.chat.data.model.OtpRequest
import com.nokat.chat.data.model.OtpResponse
import com.nokat.chat.data.model.SecretAcceptRequest
import com.nokat.chat.data.model.SecretChatRequest
import com.nokat.chat.data.model.SendMessageRequest
import com.nokat.chat.data.model.UpdateProfileRequest
import com.nokat.chat.data.model.User
import com.nokat.chat.data.model.VerifyRequest
import com.nokat.chat.data.model.VerifyResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * REST contract from docs/API.md. Paths are relative to the base URL (which ends in `/api/v1/`).
 *
 * List endpoints return [JsonElement] so that a JSON `null` body (e.g. a nil Go slice) is
 * tolerated; decode them with [decodeList].
 */
interface NokatApi {

    @POST("auth/otp/request")
    suspend fun requestOtp(@Body body: OtpRequest): OtpResponse

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: VerifyRequest): VerifyResponse

    @GET("me")
    suspend fun me(): User

    @PATCH("me")
    suspend fun updateMe(@Body body: UpdateProfileRequest): User

    @GET("users/search")
    suspend fun searchUsers(@Query("q") query: String): JsonElement

    @GET("users/{id}")
    suspend fun user(@Path("id") id: String): User

    @GET("chats")
    suspend fun chats(): JsonElement

    @POST("chats/direct")
    suspend fun createDirectChat(@Body body: DirectChatRequest): Chat

    @POST("chats/group")
    suspend fun createGroupChat(@Body body: GroupChatRequest): Chat

    /** Starts a new secret chat (always a new chat) → `201 Chat` with `secret.state = "pending"`. */
    @POST("chats/secret")
    suspend fun createSecretChat(@Body body: SecretChatRequest): Chat

    /** Non-initiator accepts a pending secret chat (first device wins; 409 afterwards). */
    @POST("chats/{id}/secret/accept")
    suspend fun acceptSecretChat(@Path("id") chatId: String, @Body body: SecretAcceptRequest): Chat

    /** Secret chats only: deletes the chat for both sides. The body (if any) is ignored. */
    @DELETE("chats/{id}")
    suspend fun deleteChat(@Path("id") chatId: String): Response<Unit>

    @GET("chats/{id}")
    suspend fun chat(@Path("id") id: String): Chat

    @GET("chats/{id}/messages")
    suspend fun messages(
        @Path("id") chatId: String,
        @Query("before") before: String?,
        @Query("limit") limit: Int,
    ): JsonElement

    @POST("chats/{id}/messages")
    suspend fun sendMessage(@Path("id") chatId: String, @Body body: SendMessageRequest): Message

    /** multipart/form-data with a single `file` part; max 20 MB (413 otherwise). */
    @Multipart
    @POST("uploads")
    suspend fun upload(@Part file: MultipartBody.Part): Attachment

    @POST("chats/{id}/read")
    suspend fun markRead(@Path("id") chatId: String, @Body body: JsonObject): OkResponse
}
