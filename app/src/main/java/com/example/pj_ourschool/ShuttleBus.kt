package com.example.pj_ourschool

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

// 가상의 셔틀 스케줄 제공
object ShuttleService {
    fun getShuttleSchedules(): List<Shuttle> = listOf(
        Shuttle(1, "셔틀버스1", "평일", "21:40", "정문 (서브웨이 앞)"),

        Shuttle(5, "셔틀버스2", "평일", "21:40", "기숙사 (버스정류장)")
    )

    fun isHoliday(dayOfWeek: String): Boolean {
        return dayOfWeek == "토" || dayOfWeek == "일"
    }
}

data class Shuttle(
    val id: Int,
    val name: String,
    val dayOfWeek: String,
    val departureTime: String,
    val departureStation: String
)

class ShuttleBus : AppCompatActivity() {
    private val stationYCoordinates = mutableMapOf<String, Float>()
    private val stationOrderDown = listOf("정문", "중문", "보건의료대학", "학생회관", "예술대학", "기숙사")
    private val stationOrderUp = listOf("기숙사", "예술대학", "학생회관", "보건의료대학", "중문", "정문")

    private lateinit var busDown1: ImageView
    private lateinit var busUp1: ImageView // 'busUp1' 변수 선언 추가

    private var animationHandler: Handler? = null
    private var updateRunnable: Runnable? = null

    // --- 상수 정의 ---
    private val ANIMATION_INTERVAL_MS = 200L // 0.2초마다 위치 업데이트 (화면 갱신 주기)
    private val STATIONARY_DURATION_SECONDS = 60L // 각 정류장 및 중간 지점에서 정차하는 시간 (1분)
    private val TRAVEL_DURATION_BETWEEN_POINTS_SECONDS = 0L // 정차 지점 간 이동 시간 (0초, 즉시 이동)

    // 버스 이미지들의 "기준선" Y 좌표를 저장 (translationY=0일 때의 기준)
    private var busDown1BaseY: Float = -1f // 셔틀버스1의 기준 Y 좌표
    private var busUp1BaseY: Float = -1f   // 셔틀버스2의 기준 Y 좌표


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shuttlebus)

        busDown1 = findViewById(R.id.bus_down_1)
        busUp1 = findViewById(R.id.bus_up_1)

        val profileImageView: ImageView = findViewById(R.id.Profile)
        val homeImageView: ImageView = findViewById(R.id.home)
        val timeImageView: ImageView = findViewById(R.id.time)
        val campusImageView: ImageView = findViewById(R.id.campus)
        val chatImageView: ImageView = findViewById(R.id.chat)
        val leftArrow: ImageView = findViewById(R.id.left_arrow)

        profileImageView.setOnClickListener { startActivity(Intent(this, Profile::class.java)) }
        homeImageView.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        timeImageView.setOnClickListener { startActivity(Intent(this, Time::class.java)) }
        campusImageView.setOnClickListener { startActivity(Intent(this, Campus::class.java)) }
        chatImageView.setOnClickListener { startActivity(Intent(this, Chat::class.java)) }
        leftArrow.setOnClickListener { finish() }

        // stationYCoordinates 및 busBaseY를 초기화
        // onGlobalLayoutListener는 레이아웃 측정이 완료되어 뷰의 실제 위치와 크기를 알 수 있을 때 호출됩니다.
        findViewById<View>(R.id.station_1_text).viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // 한 번만 호출되도록 리스너 제거
                findViewById<View>(R.id.station_1_text).viewTreeObserver.removeOnGlobalLayoutListener(this)

                // --- 각 '닷' 이미지뷰의 중앙 Y 좌표를 stationYCoordinates에 저장 ---
                // XML에 정의된 dot_left_N ID를 사용하여 각 닷의 Y 좌표를 직접 가져옵니다.
                // 닷의 Y 좌표 + 닷 높이의 절반 = 닷의 중앙 Y 좌표
                stationYCoordinates["정문"] = findViewById<View>(R.id.dot_left_1).y + findViewById<View>(R.id.dot_left_1).height / 2f
                stationYCoordinates["중문"] = findViewById<View>(R.id.dot_left_2).y + findViewById<View>(R.id.dot_left_2).height / 2f
                stationYCoordinates["보건의료대학"] = findViewById<View>(R.id.dot_left_3).y + findViewById<View>(R.id.dot_left_3).height / 2f
                stationYCoordinates["학생회관"] = findViewById<View>(R.id.dot_left_4).y + findViewById<View>(R.id.dot_left_4).height / 2f
                stationYCoordinates["예술대학"] = findViewById<View>(R.id.dot_left_5).y + findViewById<View>(R.id.dot_left_5).height / 2f
                stationYCoordinates["기숙사"] = findViewById<View>(R.id.dot_left_6).y + findViewById<View>(R.id.dot_left_6).height / 2f
                // --- 닷 중앙 Y 좌표 저장 끝 ---

                // --- 여기에서 각 버스의 "기준선" Y 좌표를 정확하게 저장 ---
                // 이 시점에서는 버스 이미지의 'y' 속성이 레이아웃에서 배치된 절대 Y 위치를 나타냅니다.
                // 이 값을 '기준선'으로 삼아 translationY를 계산합니다.
                if (busDown1BaseY == -1f) {
                    busDown1BaseY = busDown1.y
                }
                if (busUp1BaseY == -1f) {
                    busUp1BaseY = busUp1.y
                }
                // --- 기준선 Y 좌표 저장 끝 ---

                startPeriodicShuttleUpdate()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        animationHandler?.removeCallbacksAndMessages(null)
        animationHandler = null
    }

    private fun startPeriodicShuttleUpdate() {
        animationHandler = Handler(Looper.getMainLooper())
        updateRunnable = object : Runnable {
            override fun run() {
                updateShuttlePositions()
                animationHandler?.postDelayed(this, ANIMATION_INTERVAL_MS)
            }
        }
        animationHandler?.post(updateRunnable!!)
    }

    private fun updateShuttlePositions() {
        val now = Calendar.getInstance()
        val currentTimeMillis = now.timeInMillis
        val currentDay = SimpleDateFormat("E", Locale.KOREA).format(now.time)

        val activeShuttles = ShuttleService.getShuttleSchedules()
            .filter { shuttle ->
                !ShuttleService.isHoliday(currentDay) && (shuttle.dayOfWeek == "평일" || shuttle.dayOfWeek == currentDay)
            }
            .mapNotNull { shuttle ->
                val (hour, minute) = shuttle.departureTime.split(":").map { it.toInt() }
                val departureCal = now.clone() as Calendar
                departureCal.set(Calendar.HOUR_OF_DAY, hour)
                departureCal.set(Calendar.MINUTE, minute)
                departureCal.set(Calendar.SECOND, 0)
                departureCal.set(Calendar.MILLISECOND, 0)
                val departureMillis = departureCal.timeInMillis

                val numberOfStationsInRoute = getRouteLength(shuttle.departureStation)
                val totalDurationSeconds = (numberOfStationsInRoute * STATIONARY_DURATION_SECONDS) +
                        ((numberOfStationsInRoute - 1) * STATIONARY_DURATION_SECONDS)

                val arrivalMillis = departureMillis + totalDurationSeconds * 1000L

                if (currentTimeMillis >= departureMillis && currentTimeMillis < arrivalMillis) {
                    shuttle to departureMillis
                } else {
                    null
                }
            }
            .groupBy { it.first.name }
            .mapValues { entry ->
                entry.value.sortedByDescending { it.second }.first().first
            }

        Log.d("ShuttleBus", "Active shuttles found: ${activeShuttles.keys}")
        activeShuttles.forEach { (busName, shuttle) ->
            Log.d("ShuttleBus", "Debug: Active Shuttle - $busName: ${shuttle.departureTime} from ${shuttle.departureStation}")
        }

        val shuttleDown = activeShuttles["셔틀버스1"]
        updateSingleShuttlePosition(shuttleDown, busDown1, stationOrderDown, "셔틀버스1", busDown1BaseY)

        val shuttleUp = activeShuttles["셔틀버스2"]
        updateSingleShuttlePosition(shuttleUp, busUp1, stationOrderUp, "셔틀버스2", busUp1BaseY)
    }

    private fun getRouteLength(departureStation: String): Int {
        return if (departureStation.contains("기숙사")) {
            stationOrderUp.size
        } else {
            stationOrderDown.size
        }
    }

    // 단일 셔틀버스 이미지뷰를 업데이트하는 함수
    private fun updateSingleShuttlePosition(
        shuttle: Shuttle?,
        busImage: ImageView,
        route: List<String>,
        busName: String,
        baseBusY: Float
    ) {
        val now = Calendar.getInstance()
        val currentTimeMillis = now.timeInMillis

        if (shuttle == null) {
            busImage.visibility = View.GONE
            return
        }

        busImage.visibility = View.VISIBLE

        val (hour, minute) = shuttle.departureTime.split(":").map { it.toInt() }
        val departureCal = now.clone() as Calendar
        departureCal.set(Calendar.HOUR_OF_DAY, hour)
        departureCal.set(Calendar.MINUTE, minute)
        departureCal.set(Calendar.SECOND, 0)
        departureCal.set(Calendar.MILLISECOND, 0)
        val departureMillis = departureCal.timeInMillis

        val elapsedSecondsFromDeparture = (currentTimeMillis - departureMillis) / 1000L

        val busHeight = busImage.height.toFloat()
        // 버스 중앙을 닷 중앙에 맞추기 위한 Y 오프셋
        // calculatedDotCenterY는 닷의 중앙 Y 좌표이므로, 버스의 상단도 그곳에 오도록 하려면
        // 닷 중앙에서 버스 높이의 절반만큼 위로 올라가야 합니다.
        val busCenterYToImageTopOffset = busHeight / 2f


        var calculatedDotCenterY: Float = 0f // 현재 버스 위치의 중앙 Y 좌표 (절대 좌표)
        var hasPositionFound = false // 현재 위치를 찾았는지 여부 플래그

        var accumulatedSegmentTime = 0L // 셔틀 출발 시간으로부터 누적된 총 시간 (초)

        for (i in route.indices) {
            val currentStation = route[i]
            val currentStationY = stationYCoordinates[currentStation] ?: continue

            // 1. 현재 정류장에서 '정차'하는 시간 구간
            val stopAtStationStartTime = accumulatedSegmentTime
            val stopAtStationEndTime = stopAtStationStartTime + STATIONARY_DURATION_SECONDS

            if (elapsedSecondsFromDeparture >= stopAtStationStartTime && elapsedSecondsFromDeparture < stopAtStationEndTime) {
                calculatedDotCenterY = currentStationY
                hasPositionFound = true
                Log.d("ShuttleBus", "${busName} ${shuttle.departureTime}: 정류장 ${currentStation} 정차 중. Elapsed: $elapsedSecondsFromDeparture / Ends: $stopAtStationEndTime")
                break
            }
            accumulatedSegmentTime = stopAtStationEndTime

            // 2. 다음 정류장으로 이동하는 '중간 지점' 정차 구간
            if (i < route.size - 1) {
                val nextStation = route[i + 1]
                val nextStationY = stationYCoordinates[nextStation] ?: continue
                val midPointY = (currentStationY + nextStationY) / 2f

                val stopAtMidPointStartTime = accumulatedSegmentTime
                val stopAtMidPointEndTime = stopAtMidPointStartTime + STATIONARY_DURATION_SECONDS

                if (elapsedSecondsFromDeparture >= stopAtMidPointStartTime && elapsedSecondsFromDeparture < stopAtMidPointEndTime) {
                    calculatedDotCenterY = midPointY
                    hasPositionFound = true
                    Log.d("ShuttleBus", "${busName} ${shuttle.departureTime}: ${currentStation} - ${nextStation} 중간 지점 정차 중. Elapsed: $elapsedSecondsFromDeparture / Ends: $stopAtMidPointEndTime")
                    break
                }
                accumulatedSegmentTime = stopAtMidPointEndTime
            } else {
                calculatedDotCenterY = currentStationY
                hasPositionFound = true
                Log.d("ShuttleBus", "${busName} ${shuttle.departureTime}: 최종 목적지 ${currentStation} 도착. Elapsed: $elapsedSecondsFromDeparture}")
            }
        }

        // --- 버스 이미지의 Y 위치 설정 ---
        if (hasPositionFound) {
            val targetBusImageTopYAbsolute = calculatedDotCenterY - busCenterYToImageTopOffset
            val newTargetTranslationY = targetBusImageTopYAbsolute - baseBusY // 해당 버스의 기준 Y 좌표 사용

            busImage.translationY = newTargetTranslationY
        } else {
            busImage.visibility = View.GONE
        }

        // 상세 로그 (디버깅용)
        Log.d("ShuttleBus", "--- ${busName} ${shuttle.departureTime} RESULT ---")
        Log.d("ShuttleBus", "Final Calculated Dot Center Y (Absolute): $calculatedDotCenterY")

        Log.d("ShuttleBus", "Bus Image Current Visibility: ${if (busImage.visibility == View.VISIBLE) "VISIBLE" else "GONE"}")
    }
}