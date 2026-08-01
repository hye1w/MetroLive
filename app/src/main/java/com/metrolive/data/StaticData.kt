package com.metrolive.data

import java.util.Calendar
import kotlin.math.abs

/**
 * M1 정적 데이터: 우선 2호선 시청~홍대입구 구간 하드코딩.
 * M2에서 Room DB + 전 노선 마스터로 교체하되 인터페이스는 유지.
 */
object StaticData {

    /** 노선도 표시 순서 (index 0 = 화면 최상단) */
    val line2Segment = listOf(
        Station("홍대입구", isTransfer = true, transferInfo = "공항철도 · 경의중앙"),
        Station("신촌"),
        Station("이대"),
        Station("아현"),
        Station("충정로", isTransfer = true, transferInfo = "5호선"),
        Station("시청", isTransfer = true, transferInfo = "1호선"),
    )

    val stationIndex = line2Segment.mapIndexed { i, s -> s.name to i }.toMap()

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
