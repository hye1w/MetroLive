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
import com.metrolive.data.Network
import com.metrolive.ui.home.HomeScreen
import com.metrolive.ui.live.LiveMapScreen
import com.metrolive.ui.live.LiveMapViewModel
import com.metrolive.ui.route.RouteScreen
import com.metrolive.ui.trip.TripScreen
import com.metrolive.ui.settings.SettingsScreen
import com.metrolive.data.StationCoords
import com.google.android.gms.location.LocationServices
import android.annotation.SuppressLint
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
                        0 -> HomeScreen(
                            onRoute = { f, t -> route = f to t },
                            onLocateMe = { cb -> locateNearest(cb) },
                        )
                        2 -> SettingsScreen()
                        1 -> LiveMapScreen(vm) { train, dest ->
                            TripService.start(
                                this@MainActivity, train.trainNo, dest,
                                st.line, st.upLine, st.boarding,
                            )
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
                        TabItem("⚙️", "설정", tab == 2) { tab = 2 }
                    }

                    // 경로 결과 (전체 화면 오버레이)
                    route?.let { (f, t) ->
                        RouteScreen(
                            from = f, to = t, onBack = { route = null },
                            onStartGuidance = { legs, chosenTrainNo ->
                                TripService.startLegs(
                                    this@MainActivity,
                                    legs.map { TripService.Leg(it.line, it.from, it.to) },
                                    trainNo = chosenTrainNo, boarding = st.boarding,
                                )
                                // 첫 구간 노선·방향으로 실시간 화면 전환
                                legs.firstOrNull()?.let { first ->
                                    vm.setLineDirection(
                                        first.line,
                                        StaticData.legUp(first.line, first.from, first.to),
                                    )
                                }
                                route = null
                                tab = 1
                                tripCardVisible = true
                            },
                        )
                    }

                    // 칩/알림 탭 → 안내 중 화면 (남은 경로 + 실시간 + 종료)
                    if (tripCardVisible) {
                        TripScreen(onClose = { tripCardVisible = false })
                    }
                }
            }
        }
    }

    /** 현 위치 → 최근접 역 (권한 없으면 무시) */
    @SuppressLint("MissingPermission")
    private fun locateNearest(onResult: (String, Int) -> Unit) {
        runCatching {
            val client = LocationServices.getFusedLocationProviderClient(this)
            client.lastLocation
                .addOnSuccessListener { loc ->
                    val hit = loc?.let { StationCoords.nearest(it.latitude, it.longitude) }
                    if (hit != null) onResult(hit.first, hit.second)
                    else onResult("시청", -1) // 위치 실패 시 기본값 유지
                }
                .addOnFailureListener { onResult("시청", -1) }
        }.onFailure { onResult("시청", -1) }
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
