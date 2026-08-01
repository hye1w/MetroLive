package com.metrolive.data

import android.content.Context
import android.content.SharedPreferences

/** 즐겨찾기(역·노선)와 출근/퇴근 경로 저장 (기기 내 SharedPreferences) */
class FavoritesStore(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    /* ---- 역 즐겨찾기 ---- */
    fun favoriteStations(): List<String> =
        sp.getStringSet(KEY_STATIONS, emptySet())!!.sorted()

    fun toggleStation(name: String): Boolean {
        val set = sp.getStringSet(KEY_STATIONS, emptySet())!!.toMutableSet()
        val added = if (set.contains(name)) { set.remove(name); false } else { set.add(name); true }
        sp.edit().putStringSet(KEY_STATIONS, set).apply()
        return added
    }

    fun isFavoriteStation(name: String) =
        sp.getStringSet(KEY_STATIONS, emptySet())!!.contains(name)

    /* ---- 경로 즐겨찾기 (예: 교대→군자) ---- */
    fun favoriteRoutes(): List<Commute> =
        sp.getStringSet(KEY_ROUTES, emptySet())!!.mapNotNull {
            it.split("|").takeIf { p -> p.size == 2 }?.let { p -> Commute(p[0], p[1]) }
        }.sortedBy { it.from }

    fun toggleRoute(from: String, to: String): Boolean {
        val set = sp.getStringSet(KEY_ROUTES, emptySet())!!.toMutableSet()
        val key = "$from|$to"
        val added = if (set.contains(key)) { set.remove(key); false } else { set.add(key); true }
        sp.edit().putStringSet(KEY_ROUTES, set).apply()
        return added
    }

    fun isFavoriteRoute(from: String, to: String) =
        sp.getStringSet(KEY_ROUTES, emptySet())!!.contains("$from|$to")

    /* ---- 출근/퇴근 경로 (즐겨찾기와 별개) ---- */
    data class Commute(val from: String, val to: String)

    fun commute(kind: String): Commute? = // kind: "work" | "home"
        sp.getString("commute_$kind", null)?.split("|")?.takeIf { it.size == 2 }
            ?.let { Commute(it[0], it[1]) }

    fun setCommute(kind: String, from: String, to: String) =
        sp.edit().putString("commute_$kind", "$from|$to").apply()

    fun clearCommute(kind: String) = sp.edit().remove("commute_$kind").apply()

    /* ---- 기본 출발역 ---- */
    fun origin(): String = sp.getString("origin", "가산디지털단지")!!
    fun setOrigin(st: String) = sp.edit().putString("origin", st).apply()

    companion object {
        private const val KEY_STATIONS = "fav_stations"
        private const val KEY_ROUTES = "fav_routes"
    }
}
