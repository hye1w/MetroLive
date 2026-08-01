package com.metrolive.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 노선도에 그리는 역 */
data class Station(
    val name: String,
    val isTransfer: Boolean = false,
    val transferInfo: String? = null,
)

/** 화면용 열차 상태 (실시간 API 가공 결과) */
data class Train(
    val trainNo: String,
    val destination: String,        // "성수행"
    val isExpress: Boolean,
    /** 0.0 = 목록 첫 역, 1.0 = 다음 역 … 역 인덱스 기준 소수 위치 */
    val position: Float,
    val isStopped: Boolean,
    /** 기준역(내 역) 도착까지 남은 초 */
    val etaSeconds: Int,
    val platform: String = "3번 플랫폼",
)

enum class CongestionLevel(val label: String) {
    RELAXED("여유"), NORMAL("보통"), CROWDED("혼잡"), PACKED("매우혼잡");
    companion object {
        fun fromPercent(p: Int) = when {
            p < 80 -> RELAXED; p < 130 -> NORMAL; p < 150 -> CROWDED; else -> PACKED
        }
    }
}

/** 칸별 혼잡도 (10칸) */
data class TrainCongestion(
    val trainNo: String,
    val source: String,             // "통계 · 화 08시 기준" / "실시간"
    val carPercents: List<Int>,     // size 10
) {
    val levels get() = carPercents.map { CongestionLevel.fromPercent(it) }
    val recommendedCar get() = carPercents.withIndex().minBy { it.value }.index + 1
}

/** 사용자 탑승 위치: 칸(1~10) - 문(1~4) */
data class BoardingPosition(val car: Int, val door: Int) {
    override fun toString() = "$car-$door"
}

enum class Side(val label: String, val arrow: String) {
    LEFT("왼쪽", "◀"), RIGHT("오른쪽", "▶")
}

/** 하차역 안내 정보 (정적 DB에서 조회) */
data class ExitGuide(
    val doorSide: Side,             // 내리는 문 방향
    val stairSide: Side,            // 내려서 계단 방향 (탑승 위치 기준)
    val stairDistanceM: Int,
    val exitNo: String,             // "3번 출구"
)

/* ---------------- 서울 열린데이터광장 응답 DTO ---------------- */

@Serializable
data class RealtimePositionResponse(
    @SerialName("realtimePositionList") val list: List<RealtimePositionRow> = emptyList(),
)

@Serializable
data class RealtimePositionRow(
    @SerialName("trainNo") val trainNo: String,
    @SerialName("statnNm") val stationName: String,   // 현재 역
    @SerialName("statnTnm") val destination: String,  // 종착역
    @SerialName("updnLine") val upDown: String,       // 0 상행/내선, 1 하행/외선
    @SerialName("directAt") val express: String = "0",// 1 급행
    @SerialName("trainSttus") val status: String = "1", // 0 진입 1 도착 2 출발 3 전역출발
    @SerialName("recptnDt") val receivedAt: String = "",
)

@Serializable
data class RealtimeArrivalResponse(
    @SerialName("realtimeArrivalList") val list: List<RealtimeArrivalRow> = emptyList(),
)

@Serializable
data class RealtimeArrivalRow(
    @SerialName("btrainNo") val trainNo: String = "",
    @SerialName("bstatnNm") val destination: String = "",
    @SerialName("barvlDt") val etaSeconds: String = "0",
    @SerialName("arvlMsg2") val positionMsg: String = "",
    @SerialName("updnLine") val upDown: String = "",
    @SerialName("subwayId") val subwayId: String = "",   // 1001=1호선 … 1009=9호선
    @SerialName("recptnDt") val receivedAt: String = "",
)
