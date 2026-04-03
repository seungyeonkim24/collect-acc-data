package com.example.imu_collector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 포그라운드 서비스로 실행되는 데이터 수집 서비스
 * 화면이 꺼지거나 앱을 나가도 데이터 수집이 유지됩니다.
 * WISDM 형식으로 원시 x, y, z 값을 저장합니다.
 */
class CollectorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager

    private val CHANNEL_ID     = "imu_collector_channel"
    private val NOTIFICATION_ID = 1
    private val TAG             = "IMUCollector"

    // 샘플 카운터 (UI 업데이트용)
    private var sampleCount = 0
    private var lastLogTime = 0L

    companion object {
        var currentActivity: String = ""
        var onSampleCollected: ((Int) -> Unit)? = null  // 샘플 수 콜백
    }

    override fun onCreate() {
        super.onCreate()

        // 1. 포그라운드 서비스 시작
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(currentActivity, 0))

        // 2. 가속도계 등록 (50Hz → 20ms 간격)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accel, 20_000)  // 20ms = 50Hz

        Log.d(TAG, "CollectorService 시작 — 활동: $currentActivity")
        Log.d(TAG, "저장 경로: ${RawDataLogger.getFilePath()}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // WISDM 형식으로 저장
        RawDataLogger.log(x, y, z)
        sampleCount++

        // 50샘플(약 1초)마다 UI 업데이트 + 알림 갱신
        val now = System.currentTimeMillis()
        if (now - lastLogTime >= 1000L) {
            lastLogTime = now
            val elapsedSec = sampleCount / 50
            updateNotification(currentActivity, elapsedSec)
            onSampleCollected?.invoke(sampleCount)
            Log.d(TAG, "수집 중: $sampleCount 샘플 (약 ${elapsedSec}초)")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        RawDataLogger.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "CollectorService 종료 — 총 수집: $sampleCount 샘플")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── 알림 관련 ─────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "IMU 데이터 수집",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "가속도계 데이터 수집 중" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(activity: String, elapsedSec: Int): Notification {
        val m = elapsedSec / 60
        val s = elapsedSec % 60
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IMU 수집 중: $activity")
            .setContentText("경과: %02d:%02d".format(m, s))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(activity: String, elapsedSec: Int) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(activity, elapsedSec))
    }
}
