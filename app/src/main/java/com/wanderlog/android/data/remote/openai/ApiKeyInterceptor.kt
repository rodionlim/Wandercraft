package com.wanderlog.android.data.remote.openai

import android.content.Context
import com.wanderlog.android.presentation.settings.AiProvider
import com.wanderlog.android.presentation.settings.SettingsViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class ApiKeyInterceptor @Inject constructor(
    @ApplicationContext private val context: Context
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val provider = SettingsViewModel.getAiProvider(context)
        val key = SettingsViewModel.getActiveAiKey(context)
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        if (provider == AiProvider.GEMINI && originalRequest.url.host == "api.openai.com") {
            builder.url(GEMINI_COMPAT_CHAT_COMPLETIONS_URL)
        }

        if (key.isNotBlank()) {
            builder.header("Authorization", "Bearer $key")
        }

        return chain.proceed(builder.build())
    }

    companion object {
        private val GEMINI_COMPAT_CHAT_COMPLETIONS_URL =
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions".toHttpUrl()
    }
}
