package com.metrolive.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.metrolive.data.FavoritesStore
import com.metrolive.data.MetroRepository
import com.metrolive.data.Network
import com.metrolive.ui.home.StationPickerSheet
import com.metrolive.data.StaticData
import com.metrolive.ui.theme.*

@Composable
fun RouteScreen(
    from: String, to: String, onBack: () -> Unit,
    onStartGuidance: (List<Network.Leg>) -> Unit = {},
) {
    var vias by remember { mutableStateOf(listOf<String>()) }
    var viaPicker by remember { mutableStateOf(false) }
    val variants = remember(from, to, vias) { Network.findRoutesVia(from, vias, to) }
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(IosBg).statusBarsPadding()) {
        // 헤더
        val ctx = LocalContext.current
        val store = remember { FavoritesStore(ctx) }
        var fav by remember { mutableStateOf(store.isFavoriteRoute(from, to)) }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp))
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    if (vias.isEmpty()) "$from → $to"
                    else "$from → ${vias.joinToString(" → ")} → $to",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row {
                    Text("역간 2분 · 환승 4분 근사", style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (vias.isEmpty()) "  ＋경유" else "  경유 지우기",
                        fontSize = 11.sp, color = IosBlue, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            if (vias.isEmpty()) viaPicker = true else vias = emptyList()
                        }.padding(start = 4.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (fav) "★" else "☆", fontSize = 24.sp,
                color = if (fav) IosYellow else IosSecondary,
                modifier = Modifier.clickable {
                    fav = store.toggleRoute(from, to)
                }.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }

        if (variants.isEmpty()) {
            Text("경로를 찾을 수 없어요. 출발·도착역을 확인해주세요.",
                modifier = Modifier.padding(24.dp), color = IosSecondary)
            return@Column
        }

        // 기본: 최적 경로. 다른 경로가 있으면 접힌 목록으로 제공
        var showAlts by remember { mutableStateOf(false) }
        if (variants.size > 1) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    if (showAlts) "다른 경로 접기 ▲" else "다른 경로 보기 (${variants.size - 1}) ▼",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IosBlue,
                    modifier = Modifier.clickable { showAlts = !showAlts }.padding(vertical = 6.dp),
                )
                if (showAlts) variants.forEachIndexed { i, alt ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (tab == i) IosBlue.copy(alpha = .1f) else IosCard)
                            .border(0.5.dp, if (tab == i) IosBlue else IosSeparator, RoundedCornerShape(12.dp))
                            .clickable { tab = i; showAlts = false }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(alt.label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        Text("${alt.totalMin}분 · 환승 ${alt.transfers}회",
                            fontSize = 12.sp, color = IosSecondary)
                    }
                }
            }
        }

        val v = variants[tab]
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            // 요약 카드
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(IosCard)
                    .border(0.5.dp, IosSeparator, RoundedCornerShape(18.dp)).padding(18.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("${v.totalMin}", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = IosBlue)
                Text("분", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = IosBlue,
                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    "환승 ${v.transfers}회 · ${v.legs.sumOf { it.stops }}개 역 · " +
                        "약 ${"%,d".format(Network.fareOf(v.legs.sumOf { it.stops }))}원",
                    fontSize = 13.sp, color = IosSecondary, modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(14.dp))

            // 출발역 실시간 도착 (첫 구간 노선 기준)
            val firstLeg = v.legs.first()
            var arrivals by remember(from, firstLeg.line) {
                mutableStateOf<List<MetroRepository.ArrivalInfo>>(emptyList())
            }
            LaunchedEffect(from, firstLeg.line) {
                arrivals = MetroRepository().arrivalsFor(from, firstLeg.line)
            }
            if (arrivals.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(IosCard)
                        .border(0.5.dp, IosSeparator, RoundedCornerShape(18.dp)).padding(16.dp),
                ) {
                    Text("$from 실시간 도착 · ${firstLeg.line}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IosSecondary)
                    Spacer(Modifier.height(6.dp))
                    arrivals.forEach { a ->
                        Row(Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(a.destination + "행", fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Text(a.message, fontSize = 11.sp, color = IosSecondary,
                                modifier = Modifier.weight(1f), maxLines = 1)
                            Text(
                                if (a.etaSeconds <= 0) "곧 도착"
                                else "%d:%02d".format(a.etaSeconds / 60, a.etaSeconds % 60),
                                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = IosBlue,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // 구간 타임라인
            v.legs.forEachIndexed { i, leg ->
                val c = Color(Network.lineColors[leg.line] ?: 0xFF8E8E93)
                Row {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(c))
                        Box(Modifier.width(4.dp).height(64.dp).background(c))
                        if (i == v.legs.lastIndex)
                            Box(Modifier.size(14.dp).clip(CircleShape)
                                .background(Color.White)
                                .border(4.dp, c, CircleShape))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.padding(bottom = 10.dp)) {
                        Text(leg.from, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "${leg.line} · ${leg.stops}개 역 · 약 ${leg.stops * 2}분",
                            fontSize = 12.sp, color = c, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        if (i < v.legs.lastIndex) {
                            val nextLine = v.legs[i + 1].line
                            val tip = StaticData.transferTip(leg.to, leg.line, nextLine)
                            Text(
                                buildString {
                                    append("${leg.to}에서 ${nextLine} 환승 · 도보 약 4분")
                                    tip?.let {
                                        append(" · 빠른 환승 ${it.car}칸")
                                        it.platform?.let { pf -> append(" · $pf") }
                                    }
                                },
                                fontSize = 12.sp, color = IosSecondary,
                            )
                        }
                        else
                            Text(leg.to, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "시간·요금은 근사값입니다. 실시간 도착은 출발역 카드 참고.",
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(16.dp))
            // 이 경로로 안내 시작 → 구간별 실시간 추적 + 환승/하차 알림
            Button(
                onClick = { onStartGuidance(v.legs) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("이 경로로 하차 알림 시작", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            Spacer(Modifier.height(30.dp))
        }

        if (viaPicker) {
            StationPickerSheet(
                title = "경유역 선택",
                store = store,
                onFavChanged = {},
                onDismiss = { viaPicker = false },
            ) { picked ->
                if (picked != from && picked != to) vias = vias + picked
                viaPicker = false
            }
        }
    }
}
