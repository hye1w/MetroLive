package com.metrolive.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Locale

class MetroRepository(private val api: SeoulApi = SeoulApi.create()) {

    private val dtFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

    /** 노선명 → 실시간 API subwayId */
    private val subwayIds = mapOf(
        "1호선" to "1001", "2호선" to "1002", "3호선" to "1003", "4호선" to "1004",
        "5호선" to "1005", "6호선" to "1006", "7호선" to "1007", "8호선" to "1008", "9호선" to "1009",
    )

    /** 선택 노선의 실시간 열차 + 기준역 도착 ETA. recptnDt 시차 보정 포함. */
    fun liveTrains(
        lineName: String,
        baseStation: String,
        upLine: Boolean,
        pollMs: Long = 10_000,
    ): Flow<List<Train>> = flow {
        val index = StaticData.indexOf(lineName)
        while (true) {
            runCatching {
                val pos = api.realtimePosition(lineName = lineName).list
                val arr = api.realtimeArrival(stationName = baseStation).list
                emit(merge(pos, arr, index, upLine))
            }.onFailure { emit(emptyList()) }
            delay(pollMs)
        }
    }

    private fun merge(
        positions: List<RealtimePositionRow>,
        arrivals: List<RealtimeArrivalRow>,
        index: Map<String, Int>,
        upLine: Boolean,
    ): List<Train> {
        val wantUpDown = if (upLine) "0" else "1"
        val etaByTrain = arrivals.associateBy({ it.trainNo }) {
            val raw = it.etaSeconds.toIntOrNull() ?: 0
            (raw - lagSeconds(it.receivedAt)).coerceAtLeast(0)
        }
        val last = (index.size - 1).coerceAtLeast(0).toFloat()
        return positions
            .filter { it.upDown == wantUpDown }
            .mapNotNull { row ->
                val idx = index[row.stationName] ?: return@mapNotNull null
                val stopped = row.status == "1"
                val lagAdvance = if (stopped) 0f
                else (lagSeconds(row.receivedAt).toFloat() / StaticData.AVG_SEGMENT_SECONDS)
                        .coerceAtMost(0.9f)
                Train(
                    trainNo = row.trainNo,
                    destination = row.destination.removeSuffix("행") + "행",
                    isExpress = row.express == "1",
                    position = (idx + lagAdvance).coerceIn(0f, last),
                    isStopped = stopped,
                    etaSeconds = etaByTrain[row.trainNo] ?: -1,
                )
            }
            .sortedBy { it.position }
    }

    /** 경로 첫 구간용: 특정 역의 해당 노선 실시간 도착 목록 */
    data class ArrivalInfo(val destination: String, val etaSeconds: Int, val message: String)

    suspend fun arrivalsFor(station: String, lineName: String): List<ArrivalInfo> = runCatching {
        val id = subwayIds[lineName]
        api.realtimeArrival(stationName = station).list
            .filter { id == null || it.subwayId == id }
            .map {
                val raw = it.etaSeconds.toIntOrNull() ?: 0
                ArrivalInfo(
                    destination = it.destination,
                    etaSeconds = (raw - lagSeconds(it.receivedAt)).coerceAtLeast(0),
                    message = it.positionMsg,
                )
            }
            .sortedBy { it.etaSeconds }
            .take(4)
    }.getOrDefault(emptyList())

    private fun lagSeconds(recptnDt: String): Int = runCatching {
        val t = dtFormat.parse(recptnDt)?.time ?: return 0
        ((System.currentTimeMillis() - t) / 1000).toInt().coerceIn(0, 300)
    }.getOrDefault(0)

    fun congestion(trainNo: String): TrainCongestion =
        StaticData.statisticalCongestion(trainNo)
}
