package com.metrolive

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.metrolive.ui.home.HomeScreen
import com.metrolive.ui.live.LiveMapScreen
import com.metrolive.ui.live.LiveMapViewModel
import com.metrolive.ui.route.RouteScreen
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
                var tab by remember { mutableIntStateOf(0) }             // 0 홈, 1 실시간
                var route by remember { mutableStateOf<Pair<String, String>?>(null) }
                var tripCardVisible by remember { mutableStateOf(expandTripCard) }
                val vm: LiveMapViewModel = viewModel()
                val st by vm.state.collectAsState()

                // 뒤로가기: 열린 화면부터 순서대로 닫고, 홈에서는 앱 종료(기본 동작)
                BackHandler(enabled = tripCardVisible || route != null || tab != 0) {
                    when {
                        tripCardVisible -> tripCardVisible = false
                        route != null -> route = null
                        tab != 0 -> tab = 0
                    }
                }

                Box(Modifier.fillMaxSize().background(IosBg)) {
                    when (tab) {
                        0 -> HomeScreen(onRoute = { f, t -> route = f to t })
                        1 -> LiveMapScreen(vm) { train ->
                            TripService.start(this@MainActivity, train.trainNo, "홍대입구", st.boarding)
                            tripCardVisible = true
                        }
                    }

                    // 하단 탭바 (iOS 스타일)
                    Row(
                        Modifier.align(Alignment.BottomCenter)
                            .navigationBarsPadding().padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(GlassWhite)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TabItem("🏠", "홈", tab == 0) { tab = 0 }
                        TabItem("🚇", "실시간", tab == 1) { tab = 1 }
                    }

                    // 경로 결과 (전체 화면 오버레이)
                    route?.let { (f, t) ->
                        RouteScreen(from = f, to = t, onBack = { route = null })
                    }

                    // 칩/알림 탭 시 중앙 확장 카드
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

@Composable
private fun TabItem(emoji: String, label: String, selected: Boolean, onTap: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp))
            .background(if (selected) IosBlue.copy(alpha = .12f) else Color.Transparent)
            .clickable(onClick = onTap)
            .padding(horizontal = 22.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = if (selected) IosBlue else IosSecondary)
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
            Text("다음역 · 도착역까지 남은 정거장은 알림에서 갱신됩니다",
                style = MaterialTheme.typography.bodyMedium)
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
