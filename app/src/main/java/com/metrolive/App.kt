package com.metrolive

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    companion object {
        lateinit var instance: App
            private set
        val prefs: SharedPreferences
            get() = instance.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }
}

/** API 키: 설정 화면 입력값 우선, 없으면 빌드시 키(기본 sample) */
object ApiKeys {
    fun current(): String =
        App.prefs.getString("seoul_api_key", null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SEOUL_API_KEY
    fun set(key: String) = App.prefs.edit().putString("seoul_api_key", key.trim()).apply()
    fun isSample(): Boolean = current() == "sample"
}
