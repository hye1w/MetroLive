package com.metrolive.ui.live

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.metrolive.BuildConfig
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
    onStartTrip: (Train) -> Unit,
) {
    val st by vm.state.collectAsState()

    Box(Modifier.fillMaxSize().background(IosBg)) {
        Column(Modifier.fillMaxSize()) {
            Header(st.secondsSinceRefresh)
            TrainMap(
                trains = st.trains,
                selectedNo = st.selectedTrainNo,
                onTrainTap = vm::selectTrain,   // C4: 카드 탭으로 열차 변경
                modifier = Modifier.weight(1f),
            )
        }
        st.selectedTrain?.let { train ->
            BottomBoardCard(
                train = train,
                onCongestion = vm::openCongestion,
                onBoard = vm::requestBoard,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    st.congestion?.let { CongestionSheet(it, boarding = st.boarding, onDismiss = vm::closeCongestion) }

    if (st.showBoardingSheet) {
        BoardingPositionSheet(
            initial = st.boarding,
            onConfirm = { pos ->
                vm.confirmBoarding(pos)
                st.selectedTrain?.let(onStartTrip)
            },
            onDismiss = { vm.confirmBoarding(st.boarding) },
        )
    }
}

@Composable
private fun Header(sinceRefresh: Int) {
    Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("2호선", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(12.dp).clip(CircleShape).background(Line2Green))
        }
        Text("내 위치 · 시청역 도보 4분", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(IosRed))
            Spacer(Modifier.width(6.dp))
            Text("LIVE", color = IosRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(
                if (sinceRefresh < 2) "방금 갱신" else "${sinceRefresh}초 전 갱신",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (BuildConfig.SEOUL_API_KEY == "sample") {
            Spacer(Modifier.height(6.dp))
            Text(
                "샘플키 모드 · 응답 최대 5건으로 제한됨 (실제 키 발급 후 자동 해제)",
                fontSize = 11.sp, color = IosOrange, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(IosOrange.copy(alpha = .12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun TrainMap(
    trains: List<Train>,
    selectedNo: String?,
    onTrainTap: (String) -> Unit,
    modifier: Modifier,
) {
    val stations = StaticData.line2Segment
    Box(modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 200.dp)) {
        // 트랙
        Box(
            Modifier
                .padding(start = TrackX)
                .width(6.dp)
                .height(StationGap * (stations.size - 1) + 24.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Line2Green)
        )
        // 역
        stations.forEachIndexed { i, s ->
            Row(
                Modifier.offset(y = StationGap * i).padding(start = TrackX - 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(if (s.isTransfer) 20.dp else 18.dp)
                        .clip(CircleShape)
                        .background(IosCard)
                        .border(4.dp, if (s.isTransfer) IosLabel else Line2Green, CircleShape)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(s.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    s.transferInfo?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        // 열차 카드 (탭 = 선택)
        trains.forEach { t ->
            val y by animateFloatAsState(t.position, tween(1200), label = "trainY")
            TrainCard(
                train = t,
                selected = t.trainNo == selectedNo,
                onTap = { onTrainTap(t.trainNo) },
                modifier = Modifier
                    .offset(y = StationGap * y)
                    .padding(start = 108.dp, end = 16.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TrainCard(train: Train, selected: Boolean, onTap: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassWhite)
            .border(
                if (selected) 1.5.dp else 0.5.dp,
                if (selected) IosBlue else Color.White.copy(alpha = .7f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onTap)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Line2Green),
            contentAlignment = Alignment.Center,
        ) { Text("🚇", fontSize = 16.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(train.destination, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (train.isExpress) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "급행", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(IosRed)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                "열차 ${train.trainNo} · ${if (train.isStopped) "정차" else "주행 중"}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(train.etaSeconds.mmss(), color = IosBlue, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text("시청 도착", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        }
    }
}

/** 하단 카드 — C4: [탑승 시작] 버튼만. 이전/다음 열차 문구 없음 */
@Composable
private fun BottomBoardCard(
    train: Train,
    onCongestion: () -> Unit,
    onBoard: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .padding(14.dp)
            .navigationBarsPadding()
            .clip(RoundedCornerShape(22.dp))
            .background(GlassWhite)
            .border(1.dp, Color.White.copy(alpha = .8f), RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("선택한 열차", color = IosBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "탑승 여유 ✓", color = Line2Green, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(Line2Green.copy(alpha = .12f))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(train.destination, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            Text("열차 ${train.trainNo}", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(train.etaSeconds.mmss(), color = IosBlue, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text("시청 ${train.platform} · 다른 열차는 위 카드를 탭해 선택", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCongestion, modifier = Modifier.weight(1f)) {
                Text("혼잡도 보기", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onBoard,
                modifier = Modifier.weight(2f).height(48.dp),
                shape = RoundedCornerShape(13.dp),
            ) { Text("탑승 시작", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
    }
}

fun Int.mmss(): String =
    if (this < 0) "--:--" else "%d:%02d".format(this / 60, this % 60)
