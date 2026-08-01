package com.metrolive.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.metrolive.ApiKeys
import com.metrolive.data.Network
import com.metrolive.data.StaticData
import com.metrolive.data.Train
import com.metrolive.ui.sheets.BoardingPositionSheet
import com.metrolive.ui.sheets.CongestionSheet
import com.metrolive.ui.theme.*

private val StationGap = 96.dp
private val TrackX = 44.dp

@Composable
fun LiveMapScreen(
    vm: LiveMapViewModel,
    onStartTrip: (Train, destStation: String) -> Unit,
) {
    val st by vm.state.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var destPickerFor by remember { mutableStateOf<Train?>(null) }
    val lineColor = Color(Network.lineColors[st.line] ?: 0xFF00A84D)

    Box(Modifier.fillMaxSize().background(IosBg)) {
        Column(Modifier.fillMaxSize()) {
            Header(st, lineColor, onLine = vm::selectLine, onDirection = vm::setDirection)
            TrainMap(
                line = st.line,
                upLine = st.upLine,
                lineColor = lineColor,
                baseStation = st.baseStation,
                trains = st.trains,
                selectedNo = st.selectedTrainNo,
                onTrainTap = vm::selectTrain,
                modifier = Modifier.weight(1f),
            )
        }
        st.selectedTrain?.let { train ->
            BottomBoardCard(
                train = train, baseStation = st.baseStation,
                onCongestion = vm::openCongestion, onBoard = vm::requestBoard,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    st.congestion?.let { CongestionSheet(it, boarding = st.boarding, onDismiss = vm::closeCongestion) }
    if (st.showBoardingSheet) {
        BoardingPositionSheet(
            initial = st.boarding,
            onConfirm = { pos ->
                val forTrip = st.boardingForTrip
                vm.confirmBoarding(pos)
                if (forTrip) destPickerFor = st.selectedTrain   // 탑승 흐름일 때만 하차역 선택
                else com.metrolive.trip.TripService.updateBoarding(ctx, pos)
            },
            onDismiss = { vm.confirmBoarding(st.boarding) },
        )
    }

    // 하차역 선택 (현재 노선 역 목록, 진행 방향 순서)
    destPickerFor?.let { train ->
        DestinationSheet(
            line = st.line, upLine = st.upLine,
            onDismiss = { destPickerFor = null },
        ) { dest ->
            destPickerFor = null
            onStartTrip(train, dest)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationSheet(
    line: String, upLine: Boolean,
    onDismiss: () -> Unit, onPick: (String) -> Unit,
) {
    val forward = StaticData.movesForward(line, upLine)
    val stations = StaticData.segmentOf(line).let { if (forward) it else it.reversed() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = IosCard) {
        Column(Modifier.padding(horizontal = 20.dp).fillMaxHeight(0.8f)) {
            Text("하차역 선택", style = MaterialTheme.typography.titleMedium)
            Text("${StaticData.terminusOf(line, upLine)} 진행 순서",
                style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                stations.forEach { stn ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(stn.name) }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stn.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        stn.transferInfo?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HorizontalDivider(color = IosSeparator, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun Header(
    st: LiveUiState, lineColor: Color,
    onLine: (String) -> Unit, onDirection: (Boolean) -> Unit,
) {
    Column(Modifier.statusBarsPadding().padding(vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(st.line, style = MaterialTheme.typography.headlineLarge)
                Text(StaticData.terminusOf(st.line, st.upLine),
                    style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(12.dp).clip(CircleShape).background(lineColor))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(IosRed))
                Spacer(Modifier.width(5.dp))
                Text(
                    if (st.secondsSinceRefresh < 2) "LIVE" else "LIVE ${st.secondsSinceRefresh}s",
                    color = IosRed, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        // 노선 선택 칩 (1~9호선)
        Row(
            Modifier.horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Network.lines.keys.forEach { line ->
                val c = Color(Network.lineColors[line]!!)
                val on = line == st.line
                Text(
                    line.removeSuffix("호선"),
                    color = if (on) Color.White else c,
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) c else IosCard)
                        .border(1.5.dp, c, CircleShape)
                        .clickable { onLine(line) }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                )
            }
        }
        // 방향 전환
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)).background(Color(0x1F767680)).padding(2.dp),
        ) {
            listOf(true, false).map { up ->
                val base = if (st.line == "2호선") (if (up) "내선" else "외선") else (if (up) "상행" else "하행")
                up to "$base · ${StaticData.terminusOf(st.line, up)}"
            }.forEach { (up, label) ->
                val on = st.upLine == up
                Text(
                    label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (on) IosLabel else IosSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) Color.White else Color.Transparent)
                        .clickable { onDirection(up) }
                        .padding(vertical = 7.dp),
                )
            }
        }
        Text(
            "열차 진행 방향: ${if (StaticData.movesForward(st.line, st.upLine)) "아래 ▼" else "위 ▲"} · ${StaticData.terminusOf(st.line, st.upLine)}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        st.apiError?.let { err ->
            Text(
                "⚠ $err",
                fontSize = 11.sp, color = IosRed, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (ApiKeys.isSample()) {
            Text(
                "샘플키 모드 · 최대 5건 제한 — 설정 탭에서 개인 키 입력 시 해제",
                fontSize = 11.sp, color = IosOrange, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(IosOrange.copy(alpha = .12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun TrainMap(
    line: String, upLine: Boolean, lineColor: Color, baseStation: String,
    trains: List<Train>, selectedNo: String?,
    onTrainTap: (String) -> Unit, modifier: Modifier,
) {
    val forward = StaticData.movesForward(line, upLine)
    val stations = StaticData.segmentOf(line)          // 지리 순서 고정 (위=목록 시작)
    val arrow = if (forward) "▼" else "▲"              // 진행 방향 표시

    // 열차를 역 구간별 그룹핑 (canonical index 그대로)
    val byItem: Map<Int, List<Pair<Train, Float>>> = trains
        .groupBy({ it.position.toInt().coerceIn(0, stations.lastIndex) }) { t ->
            t to (t.position - t.position.toInt())
        }

    // 전체 영역 어디서든 스크롤되는 표준 리스트
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 280.dp),
    ) {
        itemsIndexed(stations, key = { _, s2 -> s2.name }) { i, stn ->
            Box(Modifier.fillMaxWidth().height(StationGap)) {
                // 노선 트랙
                Box(
                    Modifier.padding(start = TrackX)
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(lineColor)
                )
                // 역 마커 + 이름
                Row(
                    Modifier.align(Alignment.TopStart).padding(start = TrackX - 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(if (stn.isTransfer) 20.dp else 18.dp)
                            .clip(CircleShape).background(IosCard)
                            .border(4.dp, if (stn.isTransfer) IosLabel else lineColor, CircleShape)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stn.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        stn.transferInfo?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                // 이 구간의 열차 카드 (여러 대면 세로 스택으로 겹침 방지)
                byItem[i]?.let { group ->
                    if (group.size == 1) {
                        val (t, frac) = group[0]
                        TrainCard(
                            train = t, lineColor = lineColor, baseStation = baseStation,
                            arrow = arrow, selected = t.trainNo == selectedNo,
                            onTap = { onTrainTap(t.trainNo) },
                            modifier = Modifier.align(Alignment.TopEnd)
                                .offset(y = StationGap * frac.coerceAtMost(0.45f))
                                .padding(start = 118.dp, end = 16.dp),
                        )
                    } else {
                        Column(
                            Modifier.align(Alignment.TopEnd)
                                .padding(start = 118.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            group.sortedBy { it.second }.forEach { (t, _) ->
                                TrainCard(
                                    train = t, lineColor = lineColor, baseStation = baseStation,
                                    arrow = arrow, selected = t.trainNo == selectedNo,
                                    onTap = { onTrainTap(t.trainNo) },
                                    modifier = Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainCard(
    train: Train, lineColor: Color, baseStation: String,
    arrow: String, selected: Boolean, onTap: () -> Unit, modifier: Modifier,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassWhite)
            .border(
                if (selected) 1.5.dp else 0.5.dp,
                if (selected) IosBlue else Color.White.copy(alpha = .7f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onTap)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(lineColor),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🚇", fontSize = 13.sp)
                Text(arrow, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(train.destination, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (train.isExpress) {
                    Spacer(Modifier.width(5.dp))
                    Text("급행", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(IosRed)
                            .padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Text("${train.curStation} ${if (train.isStopped) "정차" else "출발"} · ${train.trainNo}",
                style = MaterialTheme.typography.labelSmall)
        }
        // (기준역 ETA는 경로 화면에서만 제공)
    }
}

@Composable
private fun BottomBoardCard(
    train: Train, baseStation: String,
    onCongestion: () -> Unit, onBoard: () -> Unit, modifier: Modifier,
) {
    val cong = remember(train.trainNo) { StaticData.statisticalCongestion(train.trainNo) }
    Column(
        modifier.padding(horizontal = 14.dp).padding(bottom = 84.dp)
            .navigationBarsPadding()
            .clip(RoundedCornerShape(22.dp)).background(IosCard)          // 불투명
            .border(0.5.dp, IosSeparator, RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(train.destination, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            Text("열차 ${train.trainNo}", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text("현위치 ${train.curStation} ${if (train.isStopped) "정차" else "출발"}",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        // 칸별 혼잡도 미니 게이지 (10칸)
        Row(
            Modifier.clickable(onClick = onCongestion),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("혼잡도", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(8.dp))
            cong.levels.forEach { lv ->
                Box(
                    Modifier.padding(horizontal = 1.dp).size(width = 14.dp, height = 8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when (lv) {
                                com.metrolive.data.CongestionLevel.RELAXED -> IosGreen
                                com.metrolive.data.CongestionLevel.NORMAL -> IosYellow
                                com.metrolive.data.CongestionLevel.CROWDED -> IosOrange
                                else -> IosRed
                            }
                        )
                )
            }
            Spacer(Modifier.width(6.dp))
            Text("자세히 ›", fontSize = 11.sp, color = IosBlue, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onBoard,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(13.dp),
        ) { Text("탑승 시작", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
    }
}

fun Int.mmss(): String =
    if (this < 0) "--:--" else "%d:%02d".format(this / 60, this % 60)
