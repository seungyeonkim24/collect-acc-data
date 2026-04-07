package com.example.imu_collector

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WISDM 형식으로 원시 가속도계 데이터를 저장합니다.
 *
 * 저장 형식 (WISDM 호환):
 *   subject_id, activity_code, timestamp, x, y, z;
 *   예: 1,A,1742727942000,-0.364,8.793,1.055;
 *
 * 저장 경로:
 *   /sdcard/Android/data/com.example.imu_collector/files/
 *   파일명: data_1_accel_watch_yyyyMMdd_HHmmss.txt
 *
 * 활동 코드:
 *   A = Walking
 *   B = Jogging
 *   D = Sitting
 *   E = Standing
 */
object RawDataLogger {

    private const val TAG = "IMUCollector"
    private const val SUBJECT_ID = 1  // 단일 사용자

    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private var currentFile: File? = null
    private var currentActivity: String = ""

    // 활동명 → WISDM 코드 매핑
    val ACTIVITY_CODES = mapOf(
        "Walking"  to "A",
        "Sitting"  to "D",
        "Standing" to "E"
    )

    /**
     * 수집 시작 시 호출 — 새 파일 생성
     * @param context Context
     * @param activity 활동명 (예: "Walking")
     */
    fun init(context: Context, activity: String) {
        currentActivity = activity
        val code      = ACTIVITY_CODES[activity] ?: "A"
        val timestamp = fileNameFormat.format(Date())
        val fileName  = "data_${SUBJECT_ID}_${code}_accel_watch_${timestamp}.txt"
        val dir       = context.getExternalFilesDir(null) ?: context.filesDir
        currentFile   = File(dir, fileName)
        Log.d(TAG, "데이터 파일 생성: ${currentFile?.absolutePath}")
    }

    /**
     * 센서 데이터 한 줄 기록
     * @param x, y, z 가속도계 값 (m/s²)
     */
    fun log(x: Float, y: Float, z: Float) {
        val file = currentFile ?: return
        val code = ACTIVITY_CODES[currentActivity] ?: return
        val timestamp = System.currentTimeMillis()

        val line = "$SUBJECT_ID,$code,$timestamp,${x},${y},${z};\n"

        try {
            FileWriter(file, true).use { it.write(line) }
        } catch (e: Exception) {
            Log.e(TAG, "파일 쓰기 오류: ${e.message}")
        }
    }

    /**
     * 현재 파일 경로 반환
     */
    fun getFilePath(): String = currentFile?.absolutePath ?: "미초기화"

    /**
     * 현재까지 수집된 샘플 수 반환
     */
    fun getSampleCount(): Long {
        return currentFile?.length()?.div(50) ?: 0L  // 대략적인 줄 수
    }

    fun close() {
        currentFile = null
        currentActivity = ""
    }
}
