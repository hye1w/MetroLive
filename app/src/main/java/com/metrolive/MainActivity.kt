package com.metrolive

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.metrolive.data.StaticData
import com.metrolive.trip.TripService
import com.metrolive.ui.live.LiveMapScreen
import com.metrolive.ui.live.LiveMapViewModel
import com.metrolive.ui.theme.*

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        val expandTripCard = intent.getBooleanExtra("expand_trip_card", false)

        setContent {
            MetroLiveTheme {
                val vm: LiveMapViewModel = viewModel()
                var tripCardVisible by remember { mutableStateOf(expandTripCard) }
                val st by vm.state.collectAsState()

                Box(Modifier.fillMaxSize()) {
                    LiveMapScreen(vm) { train ->
                        // 탑승 시작: 세션 서비스 가동 (목적지는 M1 고정, M3에서 경로 연동)
                        TripService.start(this@MainActivity, train.trainNo, "홍대입구", st.boarding)
                        tripCardVisible = true
                    }
                    // 칩/알림 탭 시 표시되는 중앙 확장 카드 (C1·C2 공통 디자인)
                    if (tripCardVisible) {
                        TripCenterCard(
                            boarding = st.boarding?.toString(),
                            onEditBoarding = { vm.requestBoard() },
                            onDismiss = { tripCardVisible = false },
                        )
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }
}

/** 중앙 확장 카드 — 칩 탭 시 열리고, 바깥 탭으로 닫힘. 칩은 계속 유지됨 */
@Composable
fun TripCenterCard(boarding: String?, onEditBoarding: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(28.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(IosCard)
                .clickable(enabled = false) {}
                .padding(22.dp)
                .fillMaxWidth(),
        ) {
            Text("2호선 성수행 · 탑승중", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("다음역 신촌 · 홍대입구까지 3개 역 · 약 6분", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(14.dp))
            // 진행 도트
            Row(verticalAlignment = Alignment.CenterVertically) {
                StaticData.line2Segment.reversed().forEachIndexed { i, s ->
                    val done = i < 2; val cur = i == 2; val dest = i == StaticData.line2Segment.lastIndex
                    Box(
                        Modifier.size(if (cur) 13.dp else 9.dp)
                            .clip(RoundedCornerShape(50))
                            .background(when { cur -> IosCard; done -> Line2Green; dest -> IosLabel; else -> IosSeparator })
                    )
                    if (i < StaticData.line2Segment.lastIndex) {
                        Box(Modifier.weight(1f).height(3.dp).background(if (done) Line2Green else IosSeparator))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    boarding?.let { "내 탑승 위치 $it 칸" } ?: "탑승 위치 미입력",
                    fontSize = 13.sp, color = IosSecondary,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEditBoarding) {
                    Text("탑승 위치 수정", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
