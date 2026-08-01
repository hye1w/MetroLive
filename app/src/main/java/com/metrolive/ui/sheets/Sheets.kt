package com.metrolive.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolive.data.BoardingPosition
import com.metrolive.data.CongestionLevel
import com.metrolive.data.TrainCongestion
import com.metrolive.ui.theme.*

private fun CongestionLevel.color() = when (this) {
    CongestionLevel.RELAXED -> IosGreen
    CongestionLevel.NORMAL -> IosYellow
    CongestionLevel.CROWDED -> IosOrange
    CongestionLevel.PACKED -> IosRed
}

/** C3: 칸별 혼잡도 바텀시트 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CongestionSheet(data: TrainCongestion, boarding: BoardingPosition?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = IosCard) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("칸별 혼잡도", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(data.source, style = MaterialTheme.typography.labelSmall)
            }
            Text("열차 ${data.trainNo}", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(18.dp))

            // 10칸 게이지
            Row(
                Modifier.fillMaxWidth().height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                data.carPercents.forEachIndexed { i, p ->
                    val level = data.levels[i]
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((70 * (p / 180f)).coerceAtLeast(8f).dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(level.color())
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${i + 1}",
                            fontSize = 10.sp,
                            fontWeight = if (boarding?.car == i + 1) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (boarding?.car == i + 1) IosBlue else IosSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CongestionLevel.entries.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(l.color()))
                        Spacer(Modifier.width(4.dp))
                        Text(l.label, fontSize = 11.sp, color = IosSecondary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            val rec = data.recommendedCar
            Text(
                buildString {
                    append("추천: ${rec}호차 (${data.levels[rec - 1].label})")
                    boarding?.let { append(" · 내 탑승 위치 ${it} 근처") }
                },
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IosBg)
                    .padding(12.dp),
            )
        }
    }
}

/** C1: 탑승 위치(칸-문) 입력 시트 — 탑승 시작 직후 표시 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardingPositionSheet(
    initial: BoardingPosition?,
    onConfirm: (BoardingPosition?) -> Unit,
    onDismiss: () -> Unit,
) {
    var car by remember { mutableIntStateOf(initial?.car ?: 5) }
    var door by remember { mutableIntStateOf(initial?.door ?: 2) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = IosCard) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text("탑승 위치를 알려주세요", style = MaterialTheme.typography.titleMedium)
            Text(
                "하차 시 계단 방향 안내에 사용돼요 (예: 5-3)",
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(16.dp))

            Text("칸", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IosSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..10).forEach { c ->
                    SelectCell("$c", c == car, Modifier.weight(1f)) { car = c }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("문", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IosSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { d ->
                    SelectCell("$d", d == door, Modifier.weight(1f)) { door = d }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onConfirm(null) }, modifier = Modifier.weight(1f)) {
                    Text("건너뛰기", color = IosSecondary)
                }
                Button(
                    onClick = { onConfirm(BoardingPosition(car, door)) },
                    modifier = Modifier.weight(2f).height(48.dp),
                    shape = RoundedCornerShape(13.dp),
                ) { Text("${car}-${door} 에서 탑승", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SelectCell(label: String, selected: Boolean, modifier: Modifier, onTap: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) IosBlue else IosBg)
            .border(0.5.dp, if (selected) IosBlue else IosSeparator, RoundedCornerShape(10.dp))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, textAlign = TextAlign.Center, fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else IosLabel,
        )
    }
}
