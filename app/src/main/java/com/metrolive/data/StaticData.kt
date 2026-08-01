package com.metrolive.data

import java.util.Calendar
import kotlin.math.abs

/**
 * M1 정적 데이터: 우선 2호선 시청~홍대입구 구간 하드코딩.
 * M2에서 Room DB + 전 노선 마스터로 교체하되 인터페이스는 유지.
 */
object StaticData {

    /** 노선별 표시 구간 (본선 첫 구간, 순환선은 중복 종점 제거) */
    private val segCache = mutableMapOf<String, List<Station>>()
    fun segmentOf(line: String): List<Station> = segCache.getOrPut(line) {
        val seg = Network.lines[line]?.first() ?: emptyList()
        val names = if (seg.size > 1 && seg.first() == seg.last()) seg.dropLast(1) else seg
        names.map { name ->
            val others = Network.linesOf[name].orEmpty().filter { it != line }
            Station(name, isTransfer = others.isNotEmpty(),
                transferInfo = others.joinToString(" · ").ifEmpty { null })
        }
    }

    fun indexOf(line: String): Map<String, Int> =
        segmentOf(line).mapIndexed { i, s -> s.name to i }.toMap()

    val line2Segment: List<Station> get() = segmentOf("2호선")
    val stationIndex: Map<String, Int> get() = indexOf("2호선")

    /**
     * 상행(up=true) 열차가 기준 목록의 index 증가 방향으로 이동하는가.
     * 2호선: 내선(0) = index 증가. 그 외: 목록이 상행 종점부터 시작하므로 상행 = index 감소.
     */
    fun movesForward(line: String, up: Boolean): Boolean =
        if (line == "2호선") up else !up

    /** 진행 방면 종점명 ("-방면" 표기용). 순환선은 대표 방면 표기. */
    fun terminusOf(line: String, up: Boolean): String {
        if (line == "2호선") return if (up) "을지로 · 성수 방면" else "충정로 · 홍대 방면"
        val seg = segmentOf(line)
        if (seg.isEmpty()) return ""
        val t = if (movesForward(line, up)) seg.last().name else seg.first().name
        return "$t 방면"
    }

    /** 환승 팁 (역, 타는 노선 → 갈아탈 노선) — 샘플, 실차 검증하며 확장 */
    data class TransferTip(val car: String, val platform: String?)
    private val transferTips = mapOf(
        Triple("신도림", "1호선", "2호선") to TransferTip("4-2", "3번 플랫폼"),
        Triple("신도림", "2호선", "1호선") to TransferTip("5-4", "1번 플랫폼"),
        Triple("교대", "2호선", "3호선") to TransferTip("5-3", null),
        Triple("교대", "3호선", "2호선") to TransferTip("3-2", null),
        Triple("왕십리", "2호선", "5호선") to TransferTip("8-1", null),
        Triple("군자", "5호선", "7호선") to TransferTip("1-4", null),
        Triple("군자", "7호선", "5호선") to TransferTip("5-2", null),
        Triple("고속터미널", "3호선", "7호선") to TransferTip("2-3", null),
        Triple("고속터미널", "3호선", "9호선") to TransferTip("8-2", null),
        Triple("종로3가", "1호선", "3호선") to TransferTip("7-1", null),
        Triple("동대문역사문화공원", "2호선", "4호선") to TransferTip("2-2", null),
        Triple("동대문역사문화공원", "2호선", "5호선") to TransferTip("9-3", null),
        Triple("사당", "2호선", "4호선") to TransferTip("3-3", null),
        Triple("잠실", "2호선", "8호선") to TransferTip("9-2", null),
        Triple("합정", "2호선", "6호선") to TransferTip("1-3", null),
        Triple("공덕", "5호선", "6호선") to TransferTip("4-4", null),
    )
    fun transferTip(station: String, fromLine: String, toLine: String): TransferTip? =
        transferTips[Triple(station, fromLine, toLine)]

    /** 구간의 전체 역 목록 (from→to 순서). 2호선은 실제 경유 방향(정거장 수)으로 판별 */
    fun stationsBetween(line: String, from: String, to: String, expectedStops: Int = -1): List<String> {
        val idx = indexOf(line)
        val names = segmentOf(line).map { it.name }
        val a = idx[from] ?: return emptyList()
        val b = idx[to] ?: return emptyList()
        val linear = if (a <= b) names.subList(a, b + 1).toList()
                     else names.subList(b, a + 1).reversed()
        if (line != "2호선" || expectedStops < 0 || linear.size - 1 == expectedStops) return linear
        // 순환 반대 방향 (시청 경계 넘어가는 경로)
        val wrap = if (a <= b)
            (a downTo 0).map { names[it] } + (names.lastIndex downTo b).map { names[it] }
        else
            (a..names.lastIndex).map { names[it] } + (0..b).map { names[it] }
        return if (wrap.size - 1 == expectedStops) wrap else linear
    }

    /** from→to 이동에 필요한 상/하행(2호선은 내선/외선) 판정 */
    fun legUp(line: String, from: String, to: String): Boolean {
        val idx = indexOf(line)
        val forward = (idx[to] ?: 0) > (idx[from] ?: 0)
        return if (line == "2호선") forward else !forward
    }

    /** 역간 평균 소요(초) — 위치 보간용, M2에서 시간표 산출값으로 교체 */
    const val AVG_SEGMENT_SECONDS = 110

    /**
     * 하차 문 방향: (역, 진행방향 상행여부) → Side
     * 2호선 내선(성수 방면) 기준 샘플. 실제 값은 실차 검증 후 보정.
     */
    private val doorSideMap = mapOf(
        ("홍대입구" to true) to Side.RIGHT,
        ("신촌" to true) to Side.RIGHT,
        ("충정로" to true) to Side.LEFT,
    )

    /**
     * 계단 위치: (역, 진행방향) → 계단이 위치한 칸 번호(선두=1 기준).
     * 내 칸과 비교해 좌/우(진행방향 기준 앞/뒤 → 하차 후 좌/우)를 계산.
     */
    private val stairCarMap = mapOf(
        ("홍대입구" to true) to 8,
        ("신촌" to true) to 3,
    )

    private val exitNoMap = mapOf("홍대입구" to "3번 출구", "신촌" to "2번 출구")

    /** 칸당 길이(m) 근사 — 거리 표시용 */
    private const val CAR_LENGTH_M = 20

    fun exitGuide(station: String, upLine: Boolean, boarding: BoardingPosition?): ExitGuide {
        val door = doorSideMap[station to upLine] ?: Side.RIGHT
        val stairCar = stairCarMap[station to upLine] ?: 5
        val myCar = boarding?.car ?: 5
        // 진행방향 기준: 계단이 내 칸보다 선두(작은 번호) 쪽이면 → 하차 후 문 방향에 따라 좌/우 결정
        val towardFront = stairCar < myCar
        val stairSide = when (door) {
            Side.RIGHT -> if (towardFront) Side.LEFT else Side.RIGHT
            Side.LEFT -> if (towardFront) Side.RIGHT else Side.LEFT
        }
        return ExitGuide(
            doorSide = door,
            stairSide = stairSide,
            stairDistanceM = abs(stairCar - myCar) * CAR_LENGTH_M,
            exitNo = exitNoMap[station] ?: "중앙 계단",
        )
    }

    /** 통계 기반 칸별 혼잡도 (요일×시간대). M2에서 공공데이터 CSV → Room 로 교체 */
    fun statisticalCongestion(trainNo: String): TrainCongestion {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dayLabel = arrayOf("일", "월", "화", "수", "목", "금", "토")[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val rush = hour in 7..9 || hour in 17..19
        val base = if (rush) 120 else 60
        // 2호선 통계 경향: 중간 칸 혼잡, 양 끝 여유 (샘플 곡선)
        val percents = (1..10).map { car ->
            val centerBias = 30 - abs(car - 5) * 6
            (base + centerBias + (trainNo.hashCode() + car * 7).mod(15)).coerceIn(30, 180)
        }
        return TrainCongestion(trainNo, "통계 · ${dayLabel} ${hour}시 기준", percents)
    }
}
