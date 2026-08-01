package com.metrolive.data

import com.metrolive.ApiKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Locale

class MetroRepository(private val api: SeoulApi = SeoulApi.create()) {

    /** 마지막 API 오류 (성공 시 null) — 화면 표시용 */
    @Volatile var lastError: String? = null
        private set

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
        val sign = if (StaticData.movesForward(lineName, upLine)) 1 else -1
        while (true) {
            var done = false
            repeat(2) { attempt ->                       // DNS 일시 실패 등 1회 즉시 재시도
                if (done) return@repeat
                runCatching {
                    val pos = api.realtimePosition(lineName = lineName).list
                    val arr = api.realtimeArrival(stationName = baseStation).list
                    lastError = if (pos.isEmpty()) "응답에 열차 데이터 없음" else null
                    emit(merge(pos, arr, index, upLine, sign))
                    done = true
                }.onFailure { e ->
                    lastError = "일시 오류 · 재시도 중 (${e.javaClass.simpleName})"
                    if (attempt == 0) delay(1200)        // 기존 목록 유지한 채 재시도
                }
            }
            delay(pollMs)
        }
    }

    private fun merge(
        positions: List<RealtimePositionRow>,
        arrivals: List<RealtimeArrivalRow>,
        index: Map<String, Int>,
        upLine: Boolean,
        sign: Int,
    ): List<Train> {
        val wantUpDown = if (upLine) "0" else "1"
        val etaByTrain = arrivals.associateBy({ normalizeTrainNo(it.trainNo) }) {
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
                    position = (idx + sign * lagAdvance).coerceIn(0f, last),
                    isStopped = stopped,
                    etaSeconds = etaByTrain[normalizeTrainNo(row.trainNo)] ?: -1,
                    curStation = row.stationName,
                )
            }
            .sortedBy { it.position }
    }

    /** 단발 조회 (탑승 세션 폴링용) */
    suspend fun trainsOnce(lineName: String, baseStation: String, upLine: Boolean): List<Train> =
        runCatching {
            val index = StaticData.indexOf(lineName)
            val sign = if (StaticData.movesForward(lineName, upLine)) 1 else -1
            merge(
                api.realtimePosition(lineName = lineName).list,
                api.realtimeArrival(stationName = baseStation).list,
                index, upLine, sign,
            )
        }.getOrDefault(emptyList())

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

    /** 설정 화면 연결 테스트: 원문 응답 확인 */
    suspend fun rawTest(): String = withContext(Dispatchers.IO) {
        val key = ApiKeys.current()
        val url = "http://swopenapi.seoul.go.kr/api/subway/$key/json/realtimePosition/0/5/2호선"
        runCatching {
            val client = OkHttpClient()
            client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                val body = res.body?.string().orEmpty()
                "HTTP ${res.code}\n키: ${key.take(8)}…\n${body.take(400)}"
            }
        }.getOrElse { "요청 실패: ${it.javaClass.simpleName} ${it.message?.take(200)}" }
    }

    fun congestion(trainNo: String): TrainCongestion =
        StaticData.statisticalCongestion(trainNo)
}
