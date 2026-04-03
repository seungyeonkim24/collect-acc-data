package com.example.imu_collector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text

class MainActivity : ComponentActivity() {

    // UI 상태
    private var isCollecting    = mutableStateOf(false)
    private var selectedActivity = mutableStateOf("Walking")
    private var elapsedSeconds  = mutableStateOf(0)
    private var sampleCount     = mutableStateOf(0)

    // 타이머
    private var timerThread: Thread? = null

    // Wake Lock
    private var wakeLock: PowerManager.WakeLock? = null

    // 활동 목록
    private val activities = listOf("Walking", "Jogging", "Sitting", "Standing")

    // 런타임 권한 요청
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) startCollection()
        else selectedActivity.value = "권한 필요"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 샘플 수 콜백 연결
        CollectorService.onSampleCollected = { count ->
            runOnUiThread { sampleCount.value = count }
        }

        setContent {
            CollectorApp(
                isCollecting     = isCollecting.value,
                selectedActivity = selectedActivity.value,
                elapsedSeconds   = elapsedSeconds.value,
                sampleCount      = sampleCount.value,
                activities       = activities,
                onActivitySelect = { selectedActivity.value = it },
                onStart          = { checkPermissionsAndStart() },
                onStop           = { stopCollection() }
            )
        }
    }

    // ── 권한 확인 및 수집 시작 ────────────────────────────────

    private fun checkPermissionsAndStart() {
        val permissions = arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startCollection()
        else requestPermissionLauncher.launch(permissions)
    }

    private fun startCollection() {
        val activity = selectedActivity.value

        // RawDataLogger 초기화 (파일 생성)
        RawDataLogger.init(this, activity)

        // CollectorService에 현재 활동 전달 후 시작
        CollectorService.currentActivity = activity
        startForegroundService(Intent(this, CollectorService::class.java))

        isCollecting.value   = true
        elapsedSeconds.value = 0
        sampleCount.value    = 0

        // Wake Lock 획득
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "imu_collector:data_collection"
        ).also { it.acquire() }

        // 타이머 시작
        timerThread = Thread {
            while (isCollecting.value) {
                Thread.sleep(1000)
                runOnUiThread { elapsedSeconds.value++ }
            }
        }.also { it.start() }
    }

    private fun stopCollection() {
        stopService(Intent(this, CollectorService::class.java))
        isCollecting.value = false
        timerThread        = null

        // Wake Lock 해제
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let { if (it.isHeld) it.release() }
        CollectorService.onSampleCollected = null
    }
}

// ── UI ────────────────────────────────────────────────────────

@Composable
fun CollectorApp(
    isCollecting: Boolean,
    selectedActivity: String,
    elapsedSeconds: Int,
    sampleCount: Int,
    activities: List<String>,
    onActivitySelect: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (!isCollecting) {
            // ── 대기 화면 ──────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text     = "활동 선택",
                    fontSize = 16.sp,
                    color    = Color.White
                )

                // 활동 선택 버튼 2×2 그리드
                val activityEmojis = mapOf(
                    "Walking"  to "🚶",
                    "Jogging"  to "🏃",
                    "Sitting"  to "🪑",
                    "Standing" to "🧍"
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        listOf("Walking", "Jogging"),
                        listOf("Sitting", "Standing")
                    ).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { activity ->
                                val isSelected = activity == selectedActivity
                                Button(
                                    onClick  = { onActivitySelect(activity) },
                                    colors   = ButtonDefaults.buttonColors(
                                        backgroundColor = if (isSelected)
                                            Color(0xFF4CAF50) else Color(0xFF333333)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text     = activityEmojis[activity] ?: "",
                                            fontSize = 16.sp,
                                            color    = Color.White
                                        )
                                        Text(
                                            text     = activity,
                                            fontSize = 10.sp,
                                            color    = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 수집 시작 버튼
                Button(
                    onClick  = onStart,
                    colors   = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF2196F3)
                    ),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(
                        text     = "수집 시작",
                        fontSize = 14.sp,
                        color    = Color.White
                    )
                }
            }

        } else {
            // ── 수집 중 화면 ────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                // 현재 활동
                Text(
                    text      = "● 수집 중",
                    fontSize  = 13.sp,
                    color     = Color(0xFF4CAF50),
                    textAlign = TextAlign.Center
                )

                Text(
                    text      = selectedActivity,
                    fontSize  = 20.sp,
                    color     = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 경과 시간
                InfoRow(
                    label = "경과",
                    value = formatTime(elapsedSeconds)
                )

                // 샘플 수
                InfoRow(
                    label = "샘플",
                    value = "${sampleCount}개"
                )

                // 목표 진행률 (3분 = 180초 기준)
                val targetSec  = 180
                val progress   = (elapsedSeconds.toFloat() / targetSec).coerceIn(0f, 1f)
                val remaining  = (targetSec - elapsedSeconds).coerceAtLeast(0)

                InfoRow(
                    label = "목표",
                    value = if (elapsedSeconds >= targetSec) "완료 ✅"
                            else "남은 시간: ${remaining}초"
                )

                // 진행 바
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(6.dp)
                        .background(Color(0xFF333333), RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(
                                if (progress >= 1f) Color(0xFF4CAF50)
                                else Color(0xFF2196F3),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 중단 버튼
                Button(
                    onClick  = onStop,
                    colors   = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFE53935)
                    ),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(
                        text     = "수집 중단",
                        fontSize = 14.sp,
                        color    = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFFAAAAAA))
        Text(text = value,  fontSize = 13.sp, color = Color.White)
    }
}

fun formatTime(seconds: Int): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)
