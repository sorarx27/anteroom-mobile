package com.zayedmd.anteroom.auth

import com.russhwolf.settings.Settings

class TokenStorage(private val settings: Settings = Settings()) {
    private val TOKEN_KEY = "auth_token"

    fun saveToken(token: String) {
        settings.putString(TOKEN_KEY, token)
    }

    fun getToken(): String? {
        return settings.getStringOrNull(TOKEN_KEY)
    }

    fun clearToken() {
        settings.remove(TOKEN_KEY)
    }
}
