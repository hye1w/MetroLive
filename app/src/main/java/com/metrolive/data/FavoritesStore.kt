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

    /* ---- 노선 즐겨찾기 ---- */
    fun favoriteLines(): List<String> =
        sp.getStringSet(KEY_LINES, emptySet())!!.sorted()

    fun toggleLine(line: String): Boolean {
        val set = sp.getStringSet(KEY_LINES, emptySet())!!.toMutableSet()
        val added = if (set.contains(line)) { set.remove(line); false } else { set.add(line); true }
        sp.edit().putStringSet(KEY_LINES, set).apply()
        return added
    }

    /* ---- 출근/퇴근 경로 (즐겨찾기와 별개) ---- */
    data class Commute(val from: String, val to: String)

    fun commute(kind: String): Commute? = // kind: "work" | "home"
        sp.getString("commute_$kind", null)?.split("|")?.takeIf { it.size == 2 }
            ?.let { Commute(it[0], it[1]) }

    fun setCommute(kind: String, from: String, to: String) =
        sp.edit().putString("commute_$kind", "$from|$to").apply()

    fun clearCommute(kind: String) = sp.edit().remove("commute_$kind").apply()

    companion object {
        private const val KEY_STATIONS = "fav_stations"
        private const val KEY_LINES = "fav_lines"
    }
}
