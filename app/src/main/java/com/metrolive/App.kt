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

/** API 키: 설정 입력값 → 내 개인 키(고정) 순. 재설치해도 개인 키 유지 */
object ApiKeys {
    private const val MY_KEY = "7070636950646e6a313031446b694e65"
    fun current(): String =
        App.prefs.getString("seoul_api_key", null)?.takeIf { it.isNotBlank() }
            ?: MY_KEY
    fun set(key: String) = App.prefs.edit().putString("seoul_api_key", key.trim()).apply()
    fun isSample(): Boolean = current() == "sample"
}
