package com.metrolive.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolive.data.MetroRepository
import com.metrolive.data.Network
import com.metrolive.data.StaticData
import com.metrolive.data.Train
import com.metrolive.trip.TripService
import com.metrolive.trip.TripState
import com.metrolive.ui.theme.*

/** 안내 중 화면: 알림/칩 탭 시 표시. 남은 경로 + 현재 구간 실시간 + 안내 종료 */
@Composable
fun TripScreen(onClose: () -> Unit) {
    val info by TripState.info.collectAsState()
    val legs by TripState.legs.collectAsState()
    val ctx = LocalContext.current
    var pendingTrain by remember { mutableStateOf<Train?>(null) }

    Column(Modifier.fillMaxSize().background(IosBg).statusBarsPadding()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 8.dp))
            Column(Modifier.padding(start = 4.dp)) {
                Text("경로 안내 중", style = MaterialTheme.typography.titleMedium)
                info?.let { inf ->
                    Text("${inf.line} · ${legs.lastOrNull()?.to ?: inf.dest} 방면",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (legs.isEmpty()) {
            Text("진행 중인 안내가 없습니다.", modifier = Modifier.padding(24.dp), color = IosSecondary)
            return@Column
        }

        val curLegIdx = (info?.legIdx ?: 0).coerceIn(0, legs.lastIndex)
        var selLeg by remember(legs) { mutableIntStateOf(curLegIdx) }
        LaunchedEffect(curLegIdx) { selLeg = curLegIdx }

        var legTrains by remember { mutableStateOf<List<Train>>(emptyList()) }
        LaunchedEffect(selLeg, legs) {
            val leg = legs.getOrNull(selLeg) ?: return@LaunchedEffect
            val up = StaticData.legUp(leg.line, leg.from, leg.to)
            while (true) {
                legTrains = MetroRepository().trainsOnce(
                    leg.line, leg.from, up,
                    segNames = StaticData.segmentFor(leg.line, leg.from, leg.to).map { it.name })
                kotlinx.coroutines.delay(15_000)
            }
        }

        // 내 열차의 화면 실시간 위치 기반 남은 정거장 (없으면 서비스 값)
        val liveLeft: Int? = run {
            if (selLeg != curLegIdx) return@run null
            val leg = legs.getOrNull(curLegIdx) ?: return@run null
            val my = info?.trainNo?.let { no ->
                legTrains.firstOrNull {
                    com.metrolive.data.normalizeTrainNo(it.trainNo) ==
                    com.metrolive.data.normalizeTrainNo(no)
                }
            } ?: return@run null
            val seg = StaticData.segmentFor(leg.line, leg.from, leg.to)
            val idx = StaticData.indexOfSeg(seg)
            val toIdx = idx[leg.to] ?: return@run null
            val fromIdx = idx[leg.from] ?: return@run null
            val d = if (toIdx > fromIdx) toIdx - my.position else my.position - toIdx
            kotlin.math.ceil(d.toDouble()).toInt().coerceAtLeast(0)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            // 요약 카드: 남은 소요시간 · 도착 예정
            info?.let { inf ->
                val futureSec = legs.drop(inf.legIdx + 1).sumOf { l ->
                    (StaticData.stationsBetween(l.line, l.from, l.to).size - 1) * 120 + 240
                }
                val totalSec = ((liveLeft ?: inf.left).coerceAtLeast(0)) * 120 + futureSec
                val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA)
                    .format(java.util.Date(System.currentTimeMillis() + totalSec * 1000L))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(IosCard)
                        .border(0.5.dp, IosSeparator, RoundedCornerShape(18.dp)).padding(18.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("${(totalSec + 59) / 60}", fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold, color = IosBlue)
                    Text("분", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = IosBlue,
                        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.padding(bottom = 4.dp)) {
                        Text("${legs.lastOrNull()?.to ?: inf.dest} · $clock 도착 예정",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        val leftShow = liveLeft ?: inf.left
                        Text("다음역 ${inf.next}" +
                            if (leftShow > 0) " · ${leftShow}정거장 남음" else "",
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            // 구간 탭 + 실시간 스트립
            val curLeg = legs[selLeg]
            val legColor = Color(Network.lineColors[curLeg.line] ?: 0xFF8E8E93)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(IosCard)
                    .border(0.5.dp, IosSeparator, RoundedCornerShape(18.dp)).padding(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    legs.forEachIndexed { i, leg ->
                        val c = Color(Network.lineColors[leg.line] ?: 0xFF8E8E93)
                        val on = i == selLeg
                        val done = i < curLegIdx
                        Text(
                            "${leg.from} ▶ ${leg.to}" + if (done) " ✓" else "",
                            fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                            color = if (on) Color.White else if (done) IosSecondary else c,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (on) c else IosCard)
                                .border(1.5.dp, if (done) IosSeparator else c, RoundedCornerShape(20.dp))
                                .clickable { selLeg = i }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                val canonical = StaticData.segmentFor(curLeg.line, curLeg.from, curLeg.to)
                val idxMap = StaticData.indexOfSeg(canonical)
                val fwd = (idxMap[curLeg.to] ?: 0) > (idxMap[curLeg.from] ?: 0)
                val slice = canonical.map { it.name }.let { if (fwd) it else it.reversed() }
                val slotW = 78.dp
                val hScroll = rememberScrollState()
                val density = LocalDensity.current

                // 초당 보간: 다음 갱신까지 열차가 역 사이를 실시간처럼 이동
                var creepTick by remember { mutableIntStateOf(0) }
                LaunchedEffect(legTrains) { creepTick = 0 }
                LaunchedEffect(Unit) {
                    while (true) { kotlinx.coroutines.delay(1000); creepTick++ }
                }
                fun dispPos(t: Train): Float {
                    val p = if (fwd) t.position else (canonical.size - 1) - t.position
                    val creep = if (!t.isStopped) (creepTick / 110f).coerceAtMost(0.9f) else 0f
                    return (p + creep).coerceIn(0f, (slice.size - 1).toFloat())
                }
                val trainsView = legTrains.distinctBy {
                    com.metrolive.data.normalizeTrainNo(it.trainNo)
                }
                val trainAtSlot: Set<Int> = trainsView.map { dispPos(it).toInt() }.toSet()

                // 자동 스크롤: 내 열차 부근으로
                LaunchedEffect(selLeg, info?.trainNo, legTrains.size) {
                    val my = info?.trainNo?.let { no ->
                        trainsView.firstOrNull {
                            com.metrolive.data.normalizeTrainNo(it.trainNo) ==
                            com.metrolive.data.normalizeTrainNo(no)
                        }
                    }
                    val target = my?.let { dispPos(it).toInt() } ?: slice.indexOf(curLeg.from)
                    hScroll.scrollTo(
                        with(density) { (slotW * (target - 1).coerceAtLeast(0)).roundToPx() })
                }

                Box(Modifier.horizontalScroll(hScroll)) {
                    // 바닥: 역 슬롯들 (트랙·점·이름)
                    Row {
                        slice.forEachIndexed { si, name ->
                            Column(Modifier.width(slotW),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(56.dp))
                                Box(Modifier.fillMaxWidth().height(14.dp),
                                    contentAlignment = Alignment.Center) {
                                    Box(Modifier.fillMaxWidth().height(3.dp)
                                        .background(legColor.copy(alpha = .35f)))
                                    Box(
                                        Modifier.size(if (si in trainAtSlot) 13.dp else 9.dp)
                                            .clip(CircleShape)
                                            .background(if (si in trainAtSlot) legColor else IosCard)
                                            .border(2.5.dp, legColor, CircleShape)
                                    )
                                }
                                val isEnd = name == curLeg.from || name == curLeg.to
                                Text(name, fontSize = 10.5.sp, maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    color = if (isEnd) legColor else IosLabel,
                                    fontWeight = if (isEnd) FontWeight.ExtraBold
                                                 else FontWeight.Medium)
                            }
                        }
                    }
                    // 오버레이: 열차 카드 (역 사이를 부드럽게 이동)
                    trainsView.forEach { t ->
                        val isMine = info?.trainNo?.let {
                            com.metrolive.data.normalizeTrainNo(t.trainNo) ==
                            com.metrolive.data.normalizeTrainNo(it) } == true
                        val isPending = pendingTrain?.trainNo == t.trainNo
                        val x by animateDpAsState(
                            slotW * dispPos(t) + 3.dp, tween(900), label = "trainX")
                        Column(
                            Modifier.offset(x = x)
                                .width(72.dp).height(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !isMine) {
                                    pendingTrain = if (isPending) null else t
                                }
                                .background(
                                    if (isMine) IosBlue.copy(alpha = .1f)
                                    else if (isPending) IosOrange.copy(alpha = .12f)
                                    else IosCard)
                                .border(if (isMine || isPending) 2.dp else 1.5.dp,
                                    if (isMine) IosBlue
                                    else if (isPending) IosOrange else legColor,
                                    RoundedCornerShape(10.dp))
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(t.destination, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, color = legColor, maxLines = 1)
                            Text(if (isMine) "내 열차" else t.trainNo,
                                fontSize = 9.sp, maxLines = 1,
                                color = if (isMine) IosBlue else IosSecondary,
                                fontWeight = if (isMine) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            pendingTrain?.let { pt ->
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(IosOrange.copy(alpha = .1f))
                        .border(1.dp, IosOrange, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${pt.destination} · ${pt.trainNo}", fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(
                        onClick = { TripService.setTrain(ctx, pt.trainNo); pendingTrain = null },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IosOrange),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) { Text("이 열차로 알림 받기", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 남은 경로 타임라인
            legs.forEachIndexed { i, leg ->
                val c = Color(Network.lineColors[leg.line] ?: 0xFF8E8E93)
                val done = i < curLegIdx
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(14.dp).clip(CircleShape)
                            .background(if (done) IosSeparator else c))
                        Box(Modifier.width(4.dp).weight(1f)
                            .background(if (done) IosSeparator else c))
                        if (i == legs.lastIndex)
                            Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White)
                                .border(4.dp, c, CircleShape))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.padding(bottom = 12.dp)) {
                        Text(leg.from, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = if (done) IosSecondary else IosLabel)
                        Text(
                            "${leg.line}" + if (done) " · 완료 ✓"
                            else if (i == curLegIdx) " · 진행 중" else "",
                            fontSize = 12.sp, color = if (done) IosSecondary else c,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        // 전체 역 목록 (진행 중 구간엔 실시간 열차·내 열차 표시)
                        val mids = StaticData.stationsBetween(leg.line, leg.from, leg.to)
                        val canonicalL = StaticData.segmentOf(leg.line)
                        val idxMapT = StaticData.indexOfSeg(
                            StaticData.segmentFor(leg.line, leg.from, leg.to))
                        val fwdT = (idxMapT[leg.to] ?: 0) > (idxMapT[leg.from] ?: 0)
                        val myTrain = if (i == curLegIdx) info?.trainNo?.let { no ->
                            legTrains.firstOrNull {
                                com.metrolive.data.normalizeTrainNo(it.trainNo) ==
                                com.metrolive.data.normalizeTrainNo(no)
                            }
                        } else null
                        mids.forEachIndexed { mi, name ->
                            if (mi == 0 || mi == mids.lastIndex) return@forEachIndexed
                            val here = if (i == selLeg) legTrains.filter { tt ->
                                canonicalL.getOrNull(tt.position.toInt())?.name == name } else emptyList()
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Box(Modifier.size(6.dp).clip(CircleShape)
                                    .background(if (done) IosSeparator else c.copy(alpha = .5f)))
                                Spacer(Modifier.width(8.dp))
                                val mineHere = here.any { tt ->
                                    info?.trainNo?.let {
                                        com.metrolive.data.normalizeTrainNo(tt.trainNo) ==
                                        com.metrolive.data.normalizeTrainNo(it)
                                    } == true
                                }
                                Text(name, fontSize = 12.sp,
                                    color = if (mineHere) IosBlue else IosSecondary,
                                    fontWeight = if (mineHere) FontWeight.Bold else FontWeight.Normal)
                                // 내 열차만 태그 표시 (타 열차 정보 미표시)
                                here.firstOrNull { tt ->
                                    info?.trainNo?.let {
                                        com.metrolive.data.normalizeTrainNo(tt.trainNo) ==
                                        com.metrolive.data.normalizeTrainNo(it)
                                    } == true
                                }?.let { tt ->
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "내 열차 · ${tt.destination}",
                                        fontSize = 10.5.sp, color = IosBlue,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                            .background(IosBlue.copy(alpha = .12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                // 내 열차 기준 이 역까지 남은 시간 (앞쪽 역만)
                                myTrain?.let { mt ->
                                    val stIdx = idxMapT[name]
                                    if (stIdx != null) {
                                        val ahead = if (fwdT) stIdx > mt.position else stIdx < mt.position
                                        if (ahead) {
                                            val min = (kotlin.math.abs(stIdx - mt.position).toInt()) * 2
                                            Spacer(Modifier.weight(1f))
                                            Text("약 ${min}분", fontSize = 11.sp,
                                                color = IosBlue, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        if (i == legs.lastIndex)
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(Modifier.width(14.dp))   // 역 dot 열과 정렬
                                Text(leg.to, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Spacer(Modifier.weight(1f))
                                info?.let { inf ->
                                    val futureSec2 = legs.drop(inf.legIdx + 1).sumOf { l ->
                                        (StaticData.stationsBetween(l.line, l.from, l.to).size - 1) * 120 + 240
                                    }
                                    val totalSec2 = ((liveLeft ?: inf.left).coerceAtLeast(0)) * 120 + futureSec2
                                    val clock2 = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA)
                                        .format(java.util.Date(System.currentTimeMillis() + totalSec2 * 1000L))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("약 ${(totalSec2 + 59) / 60}분 후",
                                            fontSize = 13.sp, color = IosBlue, fontWeight = FontWeight.Bold)
                                        Text("$clock2 도착 예정",
                                            fontSize = 11.sp, color = IosSecondary)
                                    }
                                }
                            }
                    }
                }
            }
        }

        // 하단: 안내 종료
        Button(
            onClick = { TripService.stop(ctx); onClose() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .navigationBarsPadding().padding(bottom = 28.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IosRed),
        ) { Text("하차 알림 종료", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
    }
}
