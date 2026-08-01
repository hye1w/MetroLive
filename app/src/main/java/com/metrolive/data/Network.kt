package com.metrolive.data

import java.util.PriorityQueue

/**
 * 수도권 지하철 1~9호선 네트워크 (서울시 실시간 API 커버리지 기준).
 * 같은 역명 = 환승역으로 자동 연결. 역간 2분, 환승 4분 근사(M2에서 실데이터 교체).
 */
object Network {

    // 노선별 구간(지선 포함). 각 리스트는 인접 순서.
    val lines: Map<String, List<List<String>>> = mapOf(
        "1호선" to listOf(
            listOf("도봉산","도봉","방학","창동","녹천","월계","광운대","석계","신이문","외대앞","회기","청량리","제기동","신설동","동묘앞","동대문","종로5가","종로3가","종각","시청","서울역","남영","용산","노량진","대방","신길","영등포","신도림","구로","구일","개봉","오류동","온수"),
            listOf("구로","가산디지털단지","독산","금천구청"),
        ),
        "2호선" to listOf(
            // 내선순환 순서 (시청 → … → 충정로 → 시청)
            listOf("시청","을지로입구","을지로3가","을지로4가","동대문역사문화공원","신당","상왕십리","왕십리","한양대","뚝섬","성수","건대입구","구의","강변","잠실나루","잠실","잠실새내","종합운동장","삼성","선릉","역삼","강남","교대","서초","방배","사당","낙성대","서울대입구","봉천","신림","신대방","구로디지털단지","대림","신도림","문래","영등포구청","당산","합정","홍대입구","신촌","이대","아현","충정로","시청"),
            listOf("성수","용답","신답","용두","신설동"),          // 성수지선
            listOf("신도림","도림천","양천구청","신정네거리","까치산"), // 신정지선
        ),
        "3호선" to listOf(
            listOf("구파발","연신내","불광","녹번","홍제","무악재","독립문","경복궁","안국","종로3가","을지로3가","충무로","동대입구","약수","금호","옥수","압구정","신사","잠원","고속터미널","교대","남부터미널","양재","매봉","도곡","대치","학여울","대청","일원","수서","가락시장","경찰병원","오금"),
        ),
        "4호선" to listOf(
            listOf("당고개","상계","노원","창동","쌍문","수유","미아","미아사거리","길음","성신여대입구","한성대입구","혜화","동대문","동대문역사문화공원","충무로","명동","회현","서울역","숙대입구","삼각지","신용산","이촌","동작","이수","사당","남태령"),
        ),
        "5호선" to listOf(
            listOf("방화","개화산","김포공항","송정","마곡","발산","우장산","화곡","까치산","신정","목동","오목교","양평","영등포구청","영등포시장","신길","여의도","여의나루","마포","공덕","애오개","충정로","서대문","광화문","종로3가","을지로4가","동대문역사문화공원","청구","신금호","행당","왕십리","마장","답십리","장한평","군자","아차산","광나루","천호","강동","길동","굽은다리","명일","고덕","상일동"),
            listOf("강동","둔촌동","올림픽공원","방이","오금","개롱","거여","마천"), // 마천지선
        ),
        "6호선" to listOf(
            listOf("응암","역촌","불광","독바위","연신내","구산","새절","증산","디지털미디어시티","월드컵경기장","마포구청","망원","합정","상수","광흥창","대흥","공덕","효창공원앞","삼각지","녹사평","이태원","한강진","버티고개","약수","청구","신당","동묘앞","창신","보문","안암","고려대","월곡","상월곡","돌곶이","석계","태릉입구","화랑대","봉화산","신내"),
        ),
        "7호선" to listOf(
            listOf("장암","도봉산","수락산","마들","노원","중계","하계","공릉","태릉입구","먹골","중화","상봉","면목","사가정","용마산","중곡","군자","어린이대공원","건대입구","뚝섬유원지","청담","강남구청","학동","논현","반포","고속터미널","내방","이수","남성","숭실대입구","상도","장승배기","신대방삼거리","보라매","신풍","대림","남구로","가산디지털단지","철산","광명사거리","천왕","온수"),
        ),
        "8호선" to listOf(
            listOf("암사","천호","강동구청","몽촌토성","잠실","석촌","송파","가락시장","문정","장지","복정","산성","남한산성입구","단대오거리","신흥","수진","모란"),
        ),
        "9호선" to listOf(
            listOf("개화","김포공항","공항시장","신방화","마곡나루","양천향교","가양","증미","등촌","염창","신목동","선유도","당산","국회의사당","여의도","샛강","노량진","노들","흑석","동작","구반포","신반포","고속터미널","사평","신논현","언주","선정릉","삼성중앙","봉은사","종합운동장","삼전","석촌고분","석촌","송파나루","한성백제","올림픽공원","둔촌오륜","중앙보훈병원"),
        ),
    )

    val lineColors: Map<String, Long> = mapOf(
        "1호선" to 0xFF0052A4, "2호선" to 0xFF00A84D, "3호선" to 0xFFEF7C1C,
        "4호선" to 0xFF00A5DE, "5호선" to 0xFF996CAC, "6호선" to 0xFFCD7C2F,
        "7호선" to 0xFF747F00, "8호선" to 0xFFE6186C, "9호선" to 0xFFBDB092,
    )

    /** 전체 역 이름(중복 제거, 가나다순) */
    val allStations: List<String> by lazy {
        lines.values.flatten().flatten().toSortedSet().toList()
    }

    /** 역 → 지나는 노선들 */
    val linesOf: Map<String, List<String>> by lazy {
        buildMap<String, MutableList<String>> {
            lines.forEach { (line, segs) ->
                segs.flatten().toSet().forEach { st ->
                    getOrPut(st) { mutableListOf() }.add(line)
                }
            }
        }
    }

    /* ---------------- 그래프 ---------------- */

    data class Node(val station: String, val line: String)
    private data class Edge(val to: Node, val rideSec: Int, val isTransfer: Boolean)

    private const val RIDE_SEC = 120      // 역간 소요 근사
    private const val TRANSFER_SEC = 240  // 환승 도보 근사

    private val adjacency: Map<Node, List<Edge>> by lazy {
        val adj = mutableMapOf<Node, MutableList<Edge>>()
        fun add(a: Node, b: Node, sec: Int, xfer: Boolean) {
            adj.getOrPut(a) { mutableListOf() }.add(Edge(b, sec, xfer))
        }
        lines.forEach { (line, segs) ->
            segs.forEach { seg ->
                seg.zipWithNext().forEach { (s1, s2) ->
                    val a = Node(s1, line); val b = Node(s2, line)
                    add(a, b, RIDE_SEC, false); add(b, a, RIDE_SEC, false)
                }
            }
        }
        linesOf.forEach { (st, ls) ->
            if (ls.size > 1) ls.forEach { l1 ->
                ls.forEach { l2 ->
                    if (l1 != l2) add(Node(st, l1), Node(st, l2), TRANSFER_SEC, true)
                }
            }
        }
        adj
    }

    /* ---------------- 경로 탐색 ---------------- */

    data class Leg(val line: String, val from: String, val to: String, val stops: Int)
    data class RouteVariant(
        val label: String,               // 최적 / 최단시간 / 최소환승
        val legs: List<Leg>,
        val totalSec: Int,
        val transfers: Int,
    ) { val totalMin get() = (totalSec + 59) / 60 }

    /** 세 기준으로 탐색 후 중복 제거 */
    fun findRoutes(from: String, to: String): List<RouteVariant> {
        if (from == to) return emptyList()
        val variants = listOf(
            "최적" to 300, "최단시간" to 30, "최소환승" to 7200,
        ).mapNotNull { (label, penalty) -> dijkstra(from, to, penalty)?.copy(label = label) }
        // 같은 경로면 라벨 병합
        val out = mutableListOf<RouteVariant>()
        variants.forEach { v ->
            val dup = out.firstOrNull { it.legs == v.legs }
            if (dup == null) out += v
            else out[out.indexOf(dup)] = dup.copy(label = dup.label + " · " + v.label)
        }
        return out
    }

    private fun dijkstra(from: String, to: String, transferPenaltySec: Int): RouteVariant? {
        val starts = linesOf[from]?.map { Node(from, it) } ?: return null
        if (linesOf[to] == null) return null
        val dist = mutableMapOf<Node, Int>()
        val prev = mutableMapOf<Node, Node>()
        val pq = PriorityQueue<Pair<Node, Int>>(compareBy { it.second })
        starts.forEach { dist[it] = 0; pq += it to 0 }
        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll()
            if (d > (dist[u] ?: Int.MAX_VALUE)) continue
            adjacency[u]?.forEach { e ->
                val w = e.rideSec + if (e.isTransfer) transferPenaltySec else 0
                val nd = d + w
                if (nd < (dist[e.to] ?: Int.MAX_VALUE)) {
                    dist[e.to] = nd; prev[e.to] = u; pq += e.to to nd
                }
            }
        }
        val goal = linesOf[to]!!.map { Node(to, it) }
            .filter { dist.containsKey(it) }
            .minByOrNull { dist[it]!! } ?: return null
        // 경로 복원 → 노선별 leg 병합
        val path = buildList { var c: Node? = goal; while (c != null) { add(c!!); c = prev[c] } }.reversed()
        val legs = mutableListOf<Leg>()
        var legStart = path.first(); var stops = 0
        path.zipWithNext().forEach { (a, b) ->
            if (a.line == b.line) stops++
            else { // 환승 지점
                if (stops > 0) legs += Leg(a.line, legStart.station, a.station, stops)
                legStart = b; stops = 0
            }
        }
        if (stops > 0) legs += Leg(path.last().line, legStart.station, path.last().station, stops)
        val transfers = (legs.size - 1).coerceAtLeast(0)
        val totalSec = legs.sumOf { it.stops * RIDE_SEC } + transfers * TRANSFER_SEC
        return RouteVariant("", legs, totalSec, transfers)
    }
}
