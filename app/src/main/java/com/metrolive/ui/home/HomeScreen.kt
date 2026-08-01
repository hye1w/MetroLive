package com.metrolive.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolive.data.FavoritesStore
import com.metrolive.data.Network
import com.metrolive.ui.theme.*

/** 출발 기준역: 설정 저장값 사용 (GPS 최근접 역 연동은 좌표 데이터 확보 후) */

@Composable
fun HomeScreen(
    onRoute: (from: String, to: String) -> Unit,
    onLocateMe: ((nearest: String, distanceM: Int) -> Unit) -> Unit = {},
) {
    val ctx = LocalContext.current
    val store = remember { FavoritesStore(ctx) }
    var refresh by remember { mutableIntStateOf(0) } // 즐겨찾기 변경 갱신용

    var origin by remember { mutableStateOf(store.origin()) }
    var pickerFor by remember { mutableStateOf<String?>(null) } // "dest"|"work"|"home"|"route"|"origin"
    var commuteFrom by remember { mutableStateOf<String?>(null) } // 출퇴근 등록 1단계 값

    Column(
        Modifier.fillMaxSize().background(IosBg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 110.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("어디로 갈까요?", style = MaterialTheme.typography.headlineLarge)
        var locating by remember { mutableStateOf(false) }
        var locMsg by remember { mutableStateOf<String?>(null) }
        // 시작 시 자동으로 현 위치 최근접 역을 출발역으로 (실패하면 기존 출발역 유지)
        LaunchedEffect(Unit) {
            onLocateMe { nearest, dist ->
                if (dist >= 0) { store.setOrigin(nearest); origin = nearest
                    locMsg = "현 위치 기준 · $nearest (약 ${dist}m)" }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "출발역 · ${origin} (탭해서 변경)",
                style = MaterialTheme.typography.labelSmall,
                color = IosBlue,
                modifier = Modifier.clickable { pickerFor = "origin" }.padding(vertical = 2.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (locating) "위치 확인 중…" else "📍 현 위치",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IosBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(IosBlue.copy(alpha = .1f))
                    .clickable(enabled = !locating) {
                        locating = true; locMsg = null
                        onLocateMe { nearest, dist ->
                            locating = false
                            store.setOrigin(nearest); origin = nearest
                            locMsg = "최근접 역: $nearest (약 ${dist}m)"
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        locMsg?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        Spacer(Modifier.height(16.dp))

        // ── 1. 도착지 검색
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(IosCard)
                .border(0.5.dp, IosSeparator, RoundedCornerShape(16.dp))
                .clickable { pickerFor = "dest" }
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔍", fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Text("도착역 검색", color = IosSecondary, fontSize = 15.sp)
        }

        // 전체 노선도 (서울교통공사 사이버스테이션)
        Spacer(Modifier.height(10.dp))
        Text(
            "🗺  전체 노선도 보기",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = IosBlue,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)).background(IosCard)
                .border(0.5.dp, IosSeparator, RoundedCornerShape(14.dp))
                .clickable {
                    ctx.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://www.seoulmetro.co.kr/kr/cyberStation.do"),
                        )
                    )
                }
                .padding(horizontal = 16.dp, vertical = 13.dp),
        )

        // ── 2. 출근/퇴근 경로 (즐겨찾기와 별개)
        Spacer(Modifier.height(22.dp))
        SectionTitle("출근 · 퇴근")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CommuteCard("출근", "🌅", store.commute("work"), Modifier.weight(1f),
                onGo = { onRoute(it.from, it.to) },
                onRegister = { commuteFrom = null; pickerFor = "work" },
                onClear = { store.clearCommute("work"); refresh++ })
            CommuteCard("퇴근", "🌙", store.commute("home"), Modifier.weight(1f),
                onGo = { onRoute(it.from, it.to) },
                onRegister = { commuteFrom = null; pickerFor = "home" },
                onClear = { store.clearCommute("home"); refresh++ })
        }

        // ── 3. 역 즐겨찾기
        Spacer(Modifier.height(22.dp))
        key(refresh) {
            SectionTitle("즐겨찾는 역")
            val favs = store.favoriteStations()
            if (favs.isEmpty()) EmptyHint("역 검색에서 ☆ 을 눌러 추가하세요")
            else favs.chunked(2).forEach { row ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { st -> FavStationChip(st) { onRoute(origin, st) } }
                }
            }

            // ── 4. 경로 즐겨찾기 (예: 교대→군자)
            Spacer(Modifier.height(22.dp))
            SectionTitle("즐겨찾는 경로")
            val favRoutes = store.favoriteRoutes()
            if (favRoutes.isEmpty()) EmptyHint("경로 화면에서 ☆ 을 누르거나 아래 + 로 추가")
            else favRoutes.chunked(2).forEach { row ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { r ->
                        Text(
                            "${r.from} → ${r.to}",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(IosBlue)
                                .clickable { onRoute(r.from, r.to) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            FlowChips(listOf(Chip("＋ 경로 추가") { commuteFrom = null; pickerFor = "route" }))
        }
    }

    // ── 역 선택 시트
    pickerFor?.let { target ->
        StationPickerSheet(
            title = when (target) {
                "origin" -> "출발역 선택"
                "dest" -> "도착역 선택"
                "route" -> if (commuteFrom == null) "즐겨찾는 경로 · 출발역" else "즐겨찾는 경로 · 도착역"
                else -> if (commuteFrom == null) "${if (target == "work") "출근" else "퇴근"} 출발역"
                        else "${if (target == "work") "출근" else "퇴근"} 도착역"
            },
            store = store,
            onFavChanged = { refresh++ },
            onDismiss = { pickerFor = null; commuteFrom = null },
        ) { picked ->
            when (target) {
                "origin" -> { store.setOrigin(picked); origin = picked; pickerFor = null }
                "dest" -> { pickerFor = null; onRoute(origin, picked) }
                "route" -> {
                    if (commuteFrom == null) commuteFrom = picked
                    else {
                        store.toggleRoute(commuteFrom!!, picked)
                        pickerFor = null; commuteFrom = null; refresh++
                    }
                }
                else -> {
                    if (commuteFrom == null) commuteFrom = picked
                    else {
                        store.setCommute(if (target == "work") "work" else "home", commuteFrom!!, picked)
                        pickerFor = null; commuteFrom = null; refresh++
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) =
    Text(t, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IosSecondary,
        modifier = Modifier.padding(bottom = 8.dp))

@Composable
private fun EmptyHint(t: String) =
    Text(t, fontSize = 13.sp, color = IosSecondary,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(IosCard).padding(14.dp))

@Composable
private fun FavStationChip(station: String, onTap: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(IosCard)
            .border(0.5.dp, IosSeparator, RoundedCornerShape(20.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(station, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Network.linesOf[station]?.take(4)?.forEach { l ->
            Box(
                Modifier.padding(horizontal = 1.dp).size(18.dp)
                    .clip(CircleShape)
                    .background(Color(Network.lineColors[l]!!)),
                contentAlignment = Alignment.Center,
            ) {
                Text(l.first().toString(), color = Color.White,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class Chip(val text: String, val color: Color? = null, val onTap: () -> Unit)

@Composable
private fun FlowChips(chips: List<Chip>) {
    // 간단 flow: 한 줄 4개씩
    chips.chunked(3).forEach { row ->
        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { c ->
                Text(
                    c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (c.color != null) Color.White else IosLabel,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.color ?: IosCard)
                        .border(0.5.dp, IosSeparator, RoundedCornerShape(20.dp))
                        .clickable(onClick = c.onTap)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun CommuteCard(
    label: String, emoji: String, commute: FavoritesStore.Commute?,
    modifier: Modifier,
    onGo: (FavoritesStore.Commute) -> Unit, onRegister: () -> Unit, onClear: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(IosCard)
            .border(0.5.dp, IosSeparator, RoundedCornerShape(18.dp))
            .clickable { if (commute != null) onGo(commute) else onRegister() }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 15.sp); Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            if (commute != null)
                Text("✕", color = IosSecondary, fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onClear).padding(4.dp))
        }
        Spacer(Modifier.height(6.dp))
        if (commute != null) {
            Text("${commute.from} → ${commute.to}", fontSize = 13.sp, color = IosBlue,
                fontWeight = FontWeight.SemiBold)
            Text("탭하면 바로 경로 안내", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
        } else {
            Text("경로 등록", fontSize = 13.sp, color = IosSecondary)
        }
    }
}

/** 전체 역 목록 + 검색 + ☆ 즐겨찾기 토글 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationPickerSheet(
    title: String,
    store: FavoritesStore,
    onFavChanged: () -> Unit,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var favTick by remember { mutableIntStateOf(0) }
    val list = remember(query) {
        if (query.isBlank()) Network.allStations
        else Network.allStations.filter { it.contains(query.trim()) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = IosCard) {
        Column(Modifier.padding(horizontal = 20.dp).fillMaxHeight(0.88f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("역 이름 검색") },
                singleLine = true, shape = RoundedCornerShape(13.dp),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            key(favTick) {
                LazyColumn {
                    items(list) { st ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onPick(st) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 노선 뱃지
                            Network.linesOf[st]?.take(3)?.forEach { l ->
                                Box(
                                    Modifier.size(20.dp).clip(CircleShape)
                                        .background(Color(Network.lineColors[l]!!)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(l.first().toString(), color = Color.White,
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(st, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            val fav = store.isFavoriteStation(st)
                            Text(
                                if (fav) "★" else "☆",
                                fontSize = 18.sp,
                                color = if (fav) IosYellow else IosSecondary,
                                modifier = Modifier.clickable {
                                    store.toggleStation(st); favTick++; onFavChanged()
                                }.padding(6.dp),
                            )
                        }
                        HorizontalDivider(color = IosSeparator, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
