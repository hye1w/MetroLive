package com.metrolive.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolive.ApiKeys
import com.metrolive.data.FavoritesStore
import com.metrolive.ui.theme.*

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val store = remember { FavoritesStore(ctx) }
    var key by remember { mutableStateOf(if (ApiKeys.isSample()) "" else ApiKeys.current()) }
    var saved by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(IosBg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding().padding(horizontal = 20.dp).padding(bottom = 110.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("설정", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(20.dp))

        // ── API 키
        Card("실시간 API 키") {
            Text(
                if (ApiKeys.isSample()) "현재 샘플키 사용 중 (응답 최대 5건 제한)"
                else "개인 키 사용 중 · 제한 해제됨",
                fontSize = 12.sp,
                color = if (ApiKeys.isSample()) IosOrange else Line2Green,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = key, onValueChange = { key = it; saved = false },
                placeholder = { Text("열린데이터광장 실시간 지하철 인증키 붙여넣기") },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { ApiKeys.set(key); saved = true },
                    modifier = Modifier.weight(2f), shape = RoundedCornerShape(12.dp),
                ) { Text(if (saved) "저장됨 ✓" else "키 저장", fontWeight = FontWeight.Bold) }
                TextButton(
                    onClick = { ApiKeys.set(""); key = ""; saved = false },
                    modifier = Modifier.weight(1f),
                ) { Text("샘플키로", color = IosSecondary) }
            }
            Text(
                "재빌드 없이 즉시 적용됩니다. 키 발급: data.seoul.go.kr → 인증키 신청 → 실시간 지하철",
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── 기본 출발역
        Card("기본 출발역") {
            Text(store.origin(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("홈 화면 상단에서 변경하거나 📍 버튼으로 현 위치 최근접 역 설정",
                style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(16.dp))

        // ── 정보
        Card("앱 정보") {
            Text("지하철 라이브 · 개인용", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "데이터: 서울 열린데이터광장(TOPIS). 혼잡도는 통계 근사, 문/계단 방향·환승칸은 " +
                "샘플 데이터로 실차 검증하며 보정 필요. 역 좌표는 근사값.",
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(IosCard)
            .border(0.5.dp, IosSeparator, RoundedCornerShape(18.dp)).padding(16.dp),
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IosSecondary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
