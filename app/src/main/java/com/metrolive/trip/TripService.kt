package com.metrolive.trip

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.metrolive.MainActivity
import com.metrolive.data.BoardingPosition
import com.metrolive.data.MetroRepository
import com.metrolive.data.StaticData
import com.metrolive.data.Train
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 서비스 → UI 상태 공유 (알림 탭으로 앱 열었을 때 표시) */
object TripState {
    data class Info(
        val line: String, val next: String, val dest: String,
        val left: Int, val legIdx: Int, val legsCount: Int, val alerting: Boolean,
        val trainNo: String? = null,
    )
    private val _info = MutableStateFlow<Info?>(null)
    val info: StateFlow<Info?> = _info
    internal fun set(i: Info?) { _info.value = i }

    data class LegInfo(val line: String, val from: String, val to: String)
    private val _legs = MutableStateFlow<List<LegInfo>>(emptyList())
    val legs: StateFlow<List<LegInfo>> = _legs
    internal fun setLegs(l: List<LegInfo>) { _legs.value = l }
}

/**
 * 다구간 탑승 세션 (환승 포함 경로 안내)
 * - legs: "노선|출발|도착;노선|출발|도착;…" — 순차 추적
 * - 각 구간: 탑승 열차를 실시간 위치로 자동 특정(출발역 직전 열차) 후 추적
 * - 구간 도착: 마지막 구간이면 하차 알림, 아니면 환승 알림(빠른 환승칸) 후 다음 구간으로
 * - 칩(진행형 알림) 세션 내내 유지, 1정거장 전 강 진동
 */
class TripService : Service() {

    data class Leg(val line: String, val from: String, val to: String) {
        val up get() = StaticData.legUp(line, from, to)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repo = MetroRepository()

    private var legs: List<Leg> = emptyList()
    private var legIdx = 0
    private var trainNo: String? = null
    private var boarding: BoardingPosition? = null
    private var alerted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSession(); return START_NOT_STICKY }
        if (intent?.action == ACTION_SET_BOARDING) {
            intent.getIntExtra(EXTRA_CAR, -1).takeIf { it > 0 }?.let {
                boarding = BoardingPosition(it, intent.getIntExtra(EXTRA_DOOR, 2))
            }
            return START_STICKY
        }
        val raw = intent?.getStringExtra(EXTRA_LEGS) ?: return START_NOT_STICKY.also { stopSelf() }
        legs = raw.split(";").mapNotNull {
            it.split("|").takeIf { p -> p.size == 3 }?.let { p -> Leg(p[0], p[1], p[2]) }
        }
        if (legs.isEmpty()) return START_NOT_STICKY.also { stopSelf() }
        trainNo = intent.getStringExtra(EXTRA_TRAIN)   // 알면 사용, 없으면 자동 특정
        boarding = intent.getIntExtra(EXTRA_CAR, -1).takeIf { it > 0 }
            ?.let { BoardingPosition(it, intent.getIntExtra(EXTRA_DOOR, 2)) }
        legIdx = 0; alerted = false
        TripState.setLegs(legs.map { TripState.LegInfo(it.line, it.from, it.to) })

        createChannels()
        startForeground(NOTI_ID, ongoingNotification("위치 확인 중…", -1))
        scope.launch { runTrip() }
        return START_STICKY
    }

    private suspend fun runTrip() {
        var adoptAfter = 0L                       // 환승 도보 시간만큼 탑승열차 특정 유예
        while (legIdx < legs.size) {
            val leg = legs[legIdx]
            val index = StaticData.indexOf(leg.line)
            val seg = StaticData.segmentOf(leg.line)
            val destIdx = index[leg.to] ?: break
            val fromIdx = index[leg.from] ?: break
            val forward = destIdx > fromIdx

            val trains = repo.trainsOnce(leg.line, baseStation = leg.to, upLine = leg.up)
            val now = System.currentTimeMillis()

            // 탑승 열차 특정: 출발역 직전(진행 방향 기준)에서 접근 중인 열차
            if (trainNo == null && now >= adoptAfter && trains.isNotEmpty()) {
                trainNo = trains
                    .filter { StaticData.coversLeg(leg.line, it.destination, leg.from, leg.to) }
                    .filter { if (forward) it.position <= fromIdx + 0.05f else it.position >= fromIdx - 0.05f }
                    .let { list ->
                        if (forward) list.maxByOrNull { it.position } else list.minByOrNull { it.position }
                    }?.trainNo
            }

            val me: Train? = trains.firstOrNull { it.trainNo == trainNo }
            if (me != null) {
                val curIdx = me.position.toInt()
                val left = if (forward) destIdx - curIdx else curIdx - destIdx
                val next = seg.getOrNull(if (forward) curIdx + 1 else curIdx - 1)?.name ?: leg.to
                when {
                    left <= 0 -> {                               // 구간 도착
                        if (legIdx == legs.lastIndex) { notifyArrived(leg); stopSession(); return }
                        val nextLeg = legs[legIdx + 1]
                        notifyTransfer(leg, nextLeg)
                        legIdx++; trainNo = null; alerted = false
                        adoptAfter = System.currentTimeMillis() + 240_000  // 환승 도보 4분
                    }
                    left == 1 && !alerted -> { alerted = true; vibrate(); updateNotification(alertNotification(leg, me.etaSeconds)) }
                    left == 1 -> updateNotification(alertNotification(leg, me.etaSeconds))
                    else -> updateNotification(
                        ongoingNotification("${leg.line} · ${leg.from} → ${leg.to}", left, next)
                    )
                }
            } else {
                updateNotification(ongoingNotification(
                    if (now < adoptAfter) "${leg.line}으로 환승 중 · 탑승하면 자동 추적됩니다"
                    else "${leg.line} 열차 위치 확인 중…", -1))
            }
            delay(20_000)
        }
        stopSession()
    }

    /* ---------- 알림 ---------- */

    private fun ongoingNotification(text: String, left: Int, next: String = "…"): Notification {
        val leg = legs.getOrNull(legIdx)
        TripState.set(TripState.Info(
            leg?.line ?: "", next, leg?.to ?: "", left, legIdx, legs.size,
            alerting = false, trainNo = trainNo))
        val ticker = "다음역 $next · ${leg?.to}까지 ${left}정거장"
        val title = if (left > 0) "다음역 $next · ${leg?.to}까지 ${left}정거장"
                    else "경로 안내 중 (${legIdx + 1}/${legs.size} 구간)"
        val compat = base(CH_CHIP)
            .setTicker(ticker)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("${leg?.line ?: ""} ${legIdx + 1}/${legs.size}")
            .setShowWhen(false)
            .also { b ->
                val total = StaticData.segmentOf(leg?.line ?: "1호선").size
                if (left in 0..total) b.setProgress(total, (total - left).coerceIn(0, total), false)
            }
            .setOngoing(true).setSilent(true)
            .build()
        // Android 16+: 상태바 칩(짧은 텍스트) 승격 시도 — 미지원 기기는 일반 진행 알림
        if (Build.VERSION.SDK_INT >= 36 && left > 0) {
            runCatching {
                val b = Notification.Builder.recoverBuilder(this, compat)
                Notification.Builder::class.java
                    .getMethod("setShortCriticalText", String::class.java)
                    .invoke(b, "${leg?.to} ${left}정거장")
                Notification.Builder::class.java
                    .getMethod("requestPromotedOngoing", Boolean::class.javaPrimitiveType)
                    .invoke(b, true)
                return b.build()
            }
        }
        return compat
    }

    private fun alertNotification(leg: Leg, etaSec: Int): Notification {
        val isFinal = legIdx == legs.lastIndex
        val g = StaticData.exitGuide(leg.to, upLine = leg.up, boarding = boarding)
        val eta = if (etaSec > 0) "약 %d:%02d 후".format(etaSec / 60, etaSec % 60) else "곧"
        val body = buildString {
            append("${leg.to} · $eta 도착\n")
            append("내리는 문 : ${g.doorSide.label} ${g.doorSide.arrow}\n")
            if (isFinal) {
                append("계단 방향 : 내려서 ${g.stairSide.label} ${g.stairSide.arrow} (${g.stairDistanceM}m) · ${g.exitNo}")
            } else {
                val nl = legs[legIdx + 1]
                append("${nl.line} 환승 준비")
                StaticData.transferTip(leg.to, leg.line, nl.line)?.let {
                    append(" · 빠른 환승 ${it.car}칸"); it.platform?.let { pf -> append(" · $pf") }
                }
            }
        }
        return base(CH_ALERT)
            .setContentTitle(if (isFinal) "다음 역에서 내리세요! — 1정거장 전" else "다음 역에서 환승! — 1정거장 전")
            .setContentText("${leg.to} · $eta 도착")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true).setColor(0xFF0A84FF.toInt())
            .build()
            .also {
                TripState.set(TripState.Info(
                    leg.line, leg.to, legs.last().to, 1, legIdx, legs.size,
                    alerting = true, trainNo = trainNo))
            }
    }

    private fun notifyTransfer(leg: Leg, nextLeg: Leg) {
        vibrate(short = true)
        val tip = StaticData.transferTip(leg.to, leg.line, nextLeg.line)
        val n = base(CH_ALERT)
            .setContentTitle("${leg.to} 도착 · ${nextLeg.line} 환승")
            .setContentText(buildString {
                append("${nextLeg.to} 방면으로 갈아타세요")
                tip?.let { append(" · 빠른 환승 ${it.car}칸") }
            })
            .setAutoCancel(true)
            .build()
        nm().notify(NOTI_ID + 1, n)
    }

    private fun notifyArrived(leg: Leg) {
        vibrate(short = true)
        val n = base(CH_ALERT)
            .setContentTitle("${leg.to} 도착")
            .setContentText("안전하게 내리세요. 안내를 종료합니다.")
            .setAutoCancel(true)
            .build()
        nm().notify(NOTI_ID + 1, n)
    }

    private fun base(channel: String): NotificationCompat.Builder {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra("expand_trip_card", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, TripService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(com.metrolive.R.drawable.ic_stat_train)
            .setOnlyAlertOnce(true)
            .addAction(0, "안내 종료", stopPi)
            .setContentIntent(pi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
    }

    private fun updateNotification(n: Notification) = nm().notify(NOTI_ID, n)

    private fun vibrate(short: Boolean = false) {
        val v = getSystemService(Vibrator::class.java)
        val pattern = if (short) longArrayOf(0, 200) else longArrayOf(0, 400, 150, 400, 150, 600)
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun createChannels() {
        nm().createNotificationChannel(
            NotificationChannel(CH_CHIP, "경로 안내", NotificationManager.IMPORTANCE_LOW))
        nm().createNotificationChannel(
            NotificationChannel(CH_ALERT, "하차·환승 알림", NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true) })
    }

    private fun stopSession() {
        TripState.set(null)
        TripState.setLegs(emptyList())
        scope.coroutineContext.cancelChildren()
        stopForeground(STOP_FOREGROUND_REMOVE)
        nm().cancel(NOTI_ID + 1)
        stopSelf()
    }

    private fun nm() = getSystemService(NotificationManager::class.java)
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTI_ID = 1001
        private const val CH_CHIP = "trip_chip"
        private const val CH_ALERT = "trip_alert"
        const val ACTION_STOP = "com.metrolive.STOP_TRIP"
        const val ACTION_SET_BOARDING = "com.metrolive.SET_BOARDING"

        fun updateBoarding(ctx: Context, pos: BoardingPosition?) {
            ctx.startService(
                Intent(ctx, TripService::class.java).setAction(ACTION_SET_BOARDING)
                    .putExtra(EXTRA_CAR, pos?.car ?: -1).putExtra(EXTRA_DOOR, pos?.door ?: 2))
        }
        const val EXTRA_LEGS = "legs"; const val EXTRA_TRAIN = "train"

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, TripService::class.java).setAction(ACTION_STOP))
        }
        const val EXTRA_CAR = "car"; const val EXTRA_DOOR = "door"

        /** 실시간 탭 단일 구간 탑승 */
        fun start(ctx: Context, trainNo: String, dest: String, line: String, upLine: Boolean, boarding: BoardingPosition?) {
            // upLine은 legUp으로 재계산되므로 from만 정확하면 됨: 열차 현재 위치를 몰라 from은 dest 기준 계산 불가 →
            // 단일 구간은 from을 노선 반대편 끝으로 두어 방향만 맞춤
            val seg = StaticData.segmentOf(line)
            val from = if (StaticData.movesForward(line, upLine)) seg.first().name else seg.last().name
            startLegs(ctx, listOf(Leg(line, from, dest)), trainNo, boarding)
        }

        /** 경로 안내 (다구간) */
        fun startLegs(ctx: Context, legs: List<Leg>, trainNo: String?, boarding: BoardingPosition?) {
            val i = Intent(ctx, TripService::class.java)
                .putExtra(EXTRA_LEGS, legs.joinToString(";") { "${it.line}|${it.from}|${it.to}" })
                .putExtra(EXTRA_TRAIN, trainNo)
                .putExtra(EXTRA_CAR, boarding?.car ?: -1)
                .putExtra(EXTRA_DOOR, boarding?.door ?: 2)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
}
