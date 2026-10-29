package com.nokat.chat.di

import android.content.Context
import com.nokat.chat.BuildConfig
import com.nokat.chat.data.local.SessionStore
import com.nokat.chat.data.realtime.RealtimeClient
import com.nokat.chat.data.remote.AuthInterceptor
import com.nokat.chat.data.remote.FileUrls
import com.nokat.chat.data.remote.NokatApi
import com.nokat.chat.data.repo.AuthRepository
import com.nokat.chat.data.repo.ChatRepository
import com.nokat.chat.data.repo.UserRepository
import com.nokat.chat.data.session.SessionManager
import com.nokat.chat.data.session.SessionState
import com.nokat.chat.util.AttachmentReader
import com.nokat.chat.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Manual dependency graph (no Hilt). One instance per process, owned by [com.nokat.chat.NokatApplication]. */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Lives as long as the process. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** e.g. `http://10.0.2.2:8080/api/v1` (no trailing slash). */
    val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')

    private val sessionStore = SessionStore(appContext, json)
    val sessionManager = SessionManager(sessionStore, appScope)

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(sessionManager))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    val api: NokatApi = Retrofit.Builder()
        .baseUrl("$baseUrl/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(NokatApi::class.java)

    val realtime = RealtimeClient(appContext, okHttp, json, baseUrl, appScope)
    val timeFormatter = TimeFormatter(appContext)
    val fileUrls = FileUrls(baseUrl)
    val attachmentReader = AttachmentReader(appContext)

    val authRepository = AuthRepository(api, sessionManager)
    val userRepository = UserRepository(api, json, sessionManager)
    val chatRepository = ChatRepository(api, json, realtime, sessionManager, appScope, appContext.contentResolver)

    init {
        // Connect the socket while signed in; tear everything down on logout.
        appScope.launch {
            sessionManager.state
                .map { (it as? SessionState.LoggedIn)?.token }
                .distinctUntilChanged()
                .collect { token ->
                    if (token != null) {
                        realtime.start(token)
                    } else {
                        realtime.stop()
                        chatRepository.clear()
                    }
                }
        }
    }
}
