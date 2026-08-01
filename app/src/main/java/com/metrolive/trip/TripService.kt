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
import kotlinx.coroutines.*

/**
 * 탑승 세션 (C1·C2)
 * - 시작 즉시 칩(진행형 알림) 생성, 세션 종료까지 항상 유지 → One UI 에서 상태바 칩/Now Bar 로 승격
 * - 잠금화면: setVisibility(VISIBILITY_PUBLIC) 이지만 축약(칩) 우선. 탭 → MainActivity 중앙 카드(showWhenLocked)
 * - 하차 1정거장 전: 강 진동 + HIGH 알림 전환(문 방향/계단 방향 포함), 하차까지 그 상태 유지
 */
class TripService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repo = MetroRepository()

    private lateinit var trainNo: String
    private lateinit var destStation: String
    private var boarding: BoardingPosition? = null
    private var alerted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        trainNo = intent?.getStringExtra(EXTRA_TRAIN) ?: return START_NOT_STICKY.also { stopSelf() }
        destStation = intent.getStringExtra(EXTRA_DEST) ?: "홍대입구"
        boarding = intent.getIntExtra(EXTRA_CAR, -1).takeIf { it > 0 }
            ?.let { BoardingPosition(it, intent.getIntExtra(EXTRA_DOOR, 2)) }

        createChannels()
        startForeground(NOTI_ID, ongoingNotification(next = "…", left = -1))
        scope.launch { track() }
        return START_STICKY
    }

    /** 열차번호 기반 실시간 추적 (20초 주기) */
    private suspend fun track() {
        val destIdx = StaticData.stationIndex[destStation] ?: return
        repo.liveTrains("2호선", baseStation = destStation, upLine = true, pollMs = 20_000)
            .collect { trains ->
                val me = trains.firstOrNull { it.trainNo == trainNo } ?: return@collect
                val curIdx = me.position.toInt()
                val left = destIdx - curIdx                 // 남은 정거장 수 (내선: index 증가)
                val next = StaticData.line2Segment.getOrNull(curIdx + 1)?.name ?: destStation

                when {
                    left <= 0 -> { notifyArrived(); stopSession() }
                    left == 1 && !alerted -> { alerted = true; alertOneStopBefore(me.etaSeconds) }
                    left == 1 -> updateNotification(alertNotification(me.etaSeconds)) // 유지·갱신
                    else -> updateNotification(ongoingNotification(next, left))
                }
            }
    }

    /* ---------- 알림 ---------- */

    private fun ongoingNotification(next: String, left: Int): Notification =
        base(CH_CHIP)
            .setContentTitle("2호선 성수행 탑승 중")
            .setContentText(
                if (left < 0) "위치 확인 중…"
                else "다음역 $next · ${destStation}까지 ${left}개 역"
            )
            .setProgress(StaticData.line2Segment.size, StaticData.line2Segment.size - left, left < 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

    /** 1정거장 전 — 문 방향 + 계단 방향(탑승 위치 기반) */
    private fun alertNotification(etaSec: Int): Notification {
        val g = StaticData.exitGuide(destStation, upLine = true, boarding = boarding)
        val eta = if (etaSec > 0) "약 %d:%02d 후".format(etaSec / 60, etaSec % 60) else "곧"
        return base(CH_ALERT)
            .setContentTitle("다음 역에서 내리세요! — 1정거장 전")
            .setContentText("$destStation · $eta 도착")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$destStation · $eta 도착\n" +
                    "내리는 문 : ${g.doorSide.label} ${g.doorSide.arrow}\n" +
                    "계단 방향 : 내려서 ${g.stairSide.label} ${g.stairSide.arrow} (${g.stairDistanceM}m) · ${g.exitNo}" +
                    (boarding?.let { "\n내 탑승 위치 $it 기준" } ?: "\n탑승 위치 미입력 · 역 중앙 기준")
                )
            )
            .setOngoing(true)          // 알림 후에도 하차까지 유지 (C1)
            .setColor(0xFFFF3B30.toInt())
            .setColorized(true)
            .build()
    }

    private fun alertOneStopBefore(etaSec: Int) {
        vibrate()
        updateNotification(alertNotification(etaSec))
    }

    private fun notifyArrived() {
        vibrate(short = true)
        val n = base(CH_ALERT)
            .setContentTitle("$destStation 도착")
            .setContentText("안전하게 내리세요. 세션을 종료합니다.")
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
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentIntent(pi)                                  // 칩/알림 탭 → 중앙 카드
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)   // 잠금화면 칩 (C2)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
    }

    private fun updateNotification(n: Notification) = nm().notify(NOTI_ID, n)

    private fun vibrate(short: Boolean = false) {
        val v = getSystemService(Vibrator::class.java)
        val pattern = if (short) longArrayOf(0, 200) else longArrayOf(0, 400, 150, 400, 150, 600)
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun createChannels() {
        val nm = nm()
        nm.createNotificationChannel(
            NotificationChannel(CH_CHIP, "탑승 진행", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "하차 알림", NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true) }
        )
    }

    private fun stopSession() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun nm() = getSystemService(NotificationManager::class.java)
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTI_ID = 1001
        private const val CH_CHIP = "trip_chip"
        private const val CH_ALERT = "trip_alert"
        const val EXTRA_TRAIN = "train"; const val EXTRA_DEST = "dest"
        const val EXTRA_CAR = "car"; const val EXTRA_DOOR = "door"

        fun start(ctx: Context, trainNo: String, dest: String, boarding: BoardingPosition?) {
            val i = Intent(ctx, TripService::class.java)
                .putExtra(EXTRA_TRAIN, trainNo).putExtra(EXTRA_DEST, dest)
                .putExtra(EXTRA_CAR, boarding?.car ?: -1)
                .putExtra(EXTRA_DOOR, boarding?.door ?: 2)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }
}
