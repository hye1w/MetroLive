package com.metrolive.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Locale

class MetroRepository(private val api: SeoulApi = SeoulApi.create()) {

    private val dtFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

    /**
     * 10초 주기로 (열차 위치 + 기준역 도착정보)를 결합해 방출.
     * recptnDt 와 현재 시각의 시차만큼 ETA 를 보정한다.
     */
    fun liveTrains(
        lineName: String,
        baseStation: String,
        upLine: Boolean,
        pollMs: Long = 10_000,
    ): Flow<List<Train>> = flow {
        while (true) {
            runCatching {
                val pos = api.realtimePosition(lineName = lineName).list
                val arr = api.realtimeArrival(stationName = baseStation).list
                emit(merge(pos, arr, upLine))
            }.onFailure { emit(emptyList()) } // 네트워크 실패 시 빈 목록 → UI 폴백 라벨
            delay(pollMs)
        }
    }

    private fun merge(
        positions: List<RealtimePositionRow>,
        arrivals: List<RealtimeArrivalRow>,
        upLine: Boolean,
    ): List<Train> {
        val wantUpDown = if (upLine) "0" else "1"
        val etaByTrain = arrivals.associateBy({ it.trainNo }) {
            val raw = it.etaSeconds.toIntOrNull() ?: 0
            (raw - lagSeconds(it.receivedAt)).coerceAtLeast(0)
        }
        return positions
            .filter { it.upDown == wantUpDown }
            .mapNotNull { row ->
                val idx = StaticData.stationIndex[row.stationName] ?: return@mapNotNull null
                val stopped = row.status == "1"
                val lagAdvance = if (stopped) 0f
                else (lagSeconds(row.receivedAt).toFloat() / StaticData.AVG_SEGMENT_SECONDS)
                        .coerceAtMost(0.9f)
                Train(
                    trainNo = row.trainNo,
                    destination = row.destination.removeSuffix("행") + "행",
                    isExpress = row.express == "1",
                    // 내선순환 진행 = index 증가 방향
                    position = (idx + lagAdvance).coerceIn(0f, StaticData.line2Segment.lastIndex.toFloat()),
                    isStopped = stopped,
                    etaSeconds = etaByTrain[row.trainNo] ?: -1,
                )
            }
            .sortedBy { it.position }
    }

    /** 데이터 생성 시각과 현재 시각의 차(초) — 공식 가이드의 보정 규칙 */
    private fun lagSeconds(recptnDt: String): Int = runCatching {
        val t = dtFormat.parse(recptnDt)?.time ?: return 0
        ((System.currentTimeMillis() - t) / 1000).toInt().coerceIn(0, 300)
    }.getOrDefault(0)

    fun congestion(trainNo: String): TrainCongestion =
        // M2: 실시간 혼잡도 키 보유 시 실시간 API 우선, 실패 시 통계 폴백
        StaticData.statisticalCongestion(trainNo)
}
