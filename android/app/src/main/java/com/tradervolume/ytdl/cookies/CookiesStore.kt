package com.tradervolume.ytdl.cookies

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CookiesStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "yt_cookies",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCookieHeader(value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_COOKIE) else putString(KEY_COOKIE, value)
            apply()
        }
    }

    fun loadCookieHeader(): String? = prefs.getString(KEY_COOKIE, null)
    fun clear() { prefs.edit().clear().apply() }

    companion object { private const val KEY_COOKIE = "cookie_header" }
}
