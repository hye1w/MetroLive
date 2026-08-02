package com.metrolive.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
                    // 남은 시간 추정: 현 구간 잔여 + 이후 구간(역×2분 + 환승 4분)
                    val futureSec = legs.drop(inf.legIdx + 1).sumOf { l ->
                        (StaticData.stationsBetween(l.line, l.from, l.to).size - 1) * 120 + 240
                    }
                    val totalSec = (inf.left.coerceAtLeast(0)) * 120 + futureSec
                    val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA)
                        .format(java.util.Date(System.currentTimeMillis() + totalSec * 1000L))
                    Text(
                        "다음역 ${inf.next} · ${if (inf.left > 0) "${inf.left}정거장" else "확인 중"}" +
                        " · ${legs.lastOrNull()?.to ?: inf.dest} ${clock} 도착 예정",
                        style = MaterialTheme.typography.labelSmall,
                    )
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

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            // 요약 카드: 남은 소요시간 · 도착 예정
            info?.let { inf ->
                val futureSec = legs.drop(inf.legIdx + 1).sumOf { l ->
                    (StaticData.stationsBetween(l.line, l.from, l.to).size - 1) * 120 + 240
                }
                val totalSec = (inf.left.coerceAtLeast(0)) * 120 + futureSec
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
                        Text("다음역 ${inf.next}" +
                            if (inf.left > 0) " · ${inf.left}정거장 남음" else "",
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
                val listState = rememberLazyListState()
                LaunchedEffect(selLeg, slice.size, info?.trainNo, legTrains.size) {
                    val myName = info?.trainNo?.let { no ->
                        legTrains.firstOrNull {
                            com.metrolive.data.normalizeTrainNo(it.trainNo) ==
                            com.metrolive.data.normalizeTrainNo(no)
                        }
                    }?.let { canonical.getOrNull(it.position.toInt())?.name }
                    val target = myName?.let { slice.indexOf(it) }?.takeIf { it >= 0 }
                        ?: slice.indexOf(curLeg.from)
                    listState.scrollToItem((target - 1).coerceAtLeast(0))
                }
                LazyRow(state = listState) {
                    itemsIndexed(slice) { _, name ->
                        val trainsHere = legTrains.filter { t ->
                            canonical.getOrNull(t.position.toInt())?.name == name
                        }.distinctBy { it.trainNo }
                        Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.height(56.dp), contentAlignment = Alignment.BottomCenter) {
                                trainsHere.firstOrNull()?.let { t ->
                                    val isMine = info?.trainNo?.let {
                                        com.metrolive.data.normalizeTrainNo(t.trainNo) ==
                                        com.metrolive.data.normalizeTrainNo(it) } == true
                                    val isPending = pendingTrain?.trainNo == t.trainNo
                                    Column(
                                        Modifier.width(72.dp).height(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable(enabled = !isMine) {
                                                pendingTrain = if (isPending) null else t
                                            }
                                            .background(
                                                if (isMine) IosBlue.copy(alpha = .08f)
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
                            Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.fillMaxWidth().height(3.dp).background(legColor.copy(alpha = .35f)))
                                Box(
                                    Modifier.size(if (trainsHere.isNotEmpty()) 13.dp else 9.dp)
                                        .clip(CircleShape)
                                        .background(if (trainsHere.isNotEmpty()) legColor else IosCard)
                                        .border(2.5.dp, legColor, CircleShape)
                                )
                            }
                            val isEnd = name == curLeg.from || name == curLeg.to
                            Text(name, fontSize = 10.5.sp, maxLines = 2, textAlign = TextAlign.Center,
                                color = if (isEnd) legColor else IosLabel,
                                fontWeight = if (isEnd) FontWeight.ExtraBold else FontWeight.Medium)
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
                                Text(name, fontSize = 12.sp,
                                    color = if (here.isNotEmpty()) c else IosSecondary,
                                    fontWeight = if (here.isNotEmpty()) FontWeight.Bold else FontWeight.Normal)
                                here.firstOrNull()?.let { tt ->
                                    val mine = info?.trainNo?.let {
                                        com.metrolive.data.normalizeTrainNo(tt.trainNo) ==
                                        com.metrolive.data.normalizeTrainNo(it) } == true
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        (if (mine) "내 열차 · " else "🚇 ") + "${tt.destination} ${tt.trainNo}",
                                        fontSize = 10.5.sp,
                                        color = if (mine) IosBlue else c,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                            .background((if (mine) IosBlue else c).copy(alpha = .12f))
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
                            Text(leg.to, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                modifier = Modifier.padding(top = 6.dp))
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
