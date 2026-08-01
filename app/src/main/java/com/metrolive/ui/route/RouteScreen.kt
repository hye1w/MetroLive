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
import com.metrolive.data.Network
import com.metrolive.ui.theme.*

@Composable
fun RouteScreen(from: String, to: String, onBack: () -> Unit) {
    val variants = remember(from, to) { Network.findRoutes(from, to) }
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(IosBg).statusBarsPadding()) {
        // 헤더
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp))
            Column(Modifier.padding(start = 4.dp)) {
                Text("$from → $to", style = MaterialTheme.typography.titleMedium)
                Text("역간 2분 · 환승 4분 근사 기준 (실시간 반영 예정)",
                    style = MaterialTheme.typography.labelSmall)
            }
        }

        if (variants.isEmpty()) {
            Text("경로를 찾을 수 없어요. 출발·도착역을 확인해주세요.",
                modifier = Modifier.padding(24.dp), color = IosSecondary)
            return@Column
        }

        // 탭 (최적 / 최단시간 / 최소환승 — 동일 경로는 병합 라벨)
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                .clip(RoundedCornerShape(11.dp)).background(Color(0x1F767680)).padding(2.dp),
        ) {
            variants.forEachIndexed { i, v ->
                Text(
                    v.label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (tab == i) IosLabel else IosSecondary,
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (tab == i) Color.White else Color.Transparent)
                        .clickable { tab = i }
                        .padding(vertical = 8.dp),
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
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
                Text("환승 ${v.transfers}회 · ${v.legs.sumOf { it.stops }}개 역",
                    fontSize = 13.sp, color = IosSecondary, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(18.dp))

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
                        if (i < v.legs.lastIndex)
                            Text("${leg.to}에서 환승 · 도보 약 4분",
                                fontSize = 12.sp, color = IosSecondary)
                        else
                            Text(leg.to, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "다음 단계: 첫 구간 실시간 도착·환승 대기 반영, 플랫폼·빠른 환승칸 표시",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
