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
        Shuttle(1, "셔틀버스2", "평일", "22:10", "정문 (서브웨이 앞)"),
        Shuttle(1, "셔틀버스1", "평일", "22:20", "정문 (서브웨이 앞)"),
        Shuttle(5, "셔틀버스1", "평일", "22:10", "기숙사 (버스정류장)"),
        Shuttle(5, "셔틀버스2", "평일", "22:20", "기숙사 (버스정류장)")
    )

    fun isHoliday(dayOfWeek: String): Boolean {
        val today = Calendar.getInstance(Locale.KOREA)
        val dayOfWeekInt = today.get(Calendar.DAY_OF_WEEK)
        return dayOfWeekInt == Calendar.SATURDAY || dayOfWeekInt == Calendar.SUNDAY
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
    private lateinit var busUp1: ImageView

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

        findViewById<View>(R.id.station_1_text).viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                findViewById<View>(R.id.station_1_text).viewTreeObserver.removeOnGlobalLayoutListener(this)

                stationYCoordinates["정문"] = findViewById<View>(R.id.dot_left_1).y + findViewById<View>(R.id.dot_left_1).height / 2f
                stationYCoordinates["중문"] = findViewById<View>(R.id.dot_left_2).y + findViewById<View>(R.id.dot_left_2).height / 2f
                stationYCoordinates["보건의료대학"] = findViewById<View>(R.id.dot_left_3).y + findViewById<View>(R.id.dot_left_3).height / 2f
                stationYCoordinates["학생회관"] = findViewById<View>(R.id.dot_left_4).y + findViewById<View>(R.id.dot_left_4).height / 2f
                stationYCoordinates["예술대학"] = findViewById<View>(R.id.dot_left_5).y + findViewById<View>(R.id.dot_left_5).height / 2f
                stationYCoordinates["기숙사"] = findViewById<View>(R.id.dot_left_6).y + findViewById<View>(R.id.dot_left_6).height / 2f

                // 기숙사 (오른쪽) 정류장 좌표도 추가 (dot_right_6에 매핑)
                stationYCoordinates["기숙사(오른쪽)"] = findViewById<View>(R.id.dot_right_6).y + findViewById<View>(R.id.dot_right_6).height / 2f
                stationYCoordinates["예술대학(오른쪽)"] = findViewById<View>(R.id.dot_right_5).y + findViewById<View>(R.id.dot_right_5).height / 2f
                stationYCoordinates["학생회관(오른쪽)"] = findViewById<View>(R.id.dot_right_4).y + findViewById<View>(R.id.dot_right_4).height / 2f
                stationYCoordinates["보건의료대학(오른쪽)"] = findViewById<View>(R.id.dot_right_3).y + findViewById<View>(R.id.dot_right_3).height / 2f
                stationYCoordinates["중문(오른쪽)"] = findViewById<View>(R.id.dot_right_2).y + findViewById<View>(R.id.dot_right_2).height / 2f
                stationYCoordinates["정문(오른쪽)"] = findViewById<View>(R.id.dot_right_1).y + findViewById<View>(R.id.dot_right_1).height / 2f


                if (busDown1BaseY == -1f) {
                    busDown1BaseY = busDown1.y
                }
                if (busUp1BaseY == -1f) {
                    busUp1BaseY = busUp1.y
                }

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
                // 총 소요 시간 = (각 정류장 정차 시간 * 정류장 수) + (중간 지점 정차 시간 * (정류장 수 - 1))
                val totalDurationSeconds = (numberOfStationsInRoute * STATIONARY_DURATION_SECONDS) +
                        ((numberOfStationsInRoute - 1) * STATIONARY_DURATION_SECONDS) // TRAVEL_DURATION_BETWEEN_POINTS_SECONDS가 0이므로 실질적으로 중간 지점 정차 시간만큼만 추가됨

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

        // '셔틀버스1'은 정문에서 출발하는 노선 (왼쪽 라인)
        val shuttleDown = activeShuttles["셔틀버스1"]
        // '셔틀버스2'는 기숙사에서 출발하는 노선 (오른쪽 라인)
        val shuttleUp = activeShuttles["셔틀버스2"]

        // 셔틀버스1 (정문 출발) 업데이트
        updateSingleShuttlePosition(shuttleDown, busDown1, stationOrderDown, "셔틀버스1", busDown1BaseY, findViewById(R.id.dot_left_1))

        // 셔틀버스2 (기숙사 출발) 업데이트
        updateSingleShuttlePosition(shuttleUp, busUp1, stationOrderUp, "셔틀버스2", busUp1BaseY, findViewById(R.id.dot_right_6))
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
        baseBusY: Float,
        initialDot: ImageView // 해당 노선의 시작점 닷 이미지뷰
    ) {
        val now = Calendar.getInstance()
        val currentTimeMillis = now.timeInMillis

        if (shuttle == null) {
            busImage.visibility = View.GONE
            return
        }

        // 셔틀버스 이미지 초기 위치 설정
        if (busImage.visibility == View.GONE) { // 한 번만 초기화
            val initialDotCenterY = initialDot.y + initialDot.height / 2f
            val busHeight = busImage.height.toFloat()
            val busCenterYToImageTopOffset = busHeight / 2f
            val targetBusImageTopYAbsolute = initialDotCenterY - busCenterYToImageTopOffset
            val initialTranslationY = targetBusImageTopYAbsolute - baseBusY
            busImage.translationY = initialTranslationY
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
            // TRAVEL_DURATION_BETWEEN_POINTS_SECONDS 가 0이므로,
            // 이 로직은 각 정류장 사이에 '중간 지점'이라는 가상의 정차 지점을 만들고,
            // 거기서도 STATIONARY_DURATION_SECONDS 만큼 멈추는 것으로 해석됩니다.
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
                // 최종 목적지에 도착한 후의 처리
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
            // 현재 시간이 어떤 위치에도 해당하지 않으면 (예: 운행 시작 전이거나 운행이 완전히 종료된 후)
            // 셔틀버스를 화면에서 숨깁니다.
            busImage.visibility = View.GONE
        }

        // 상세 로그 (디버깅용)
        Log.d("ShuttleBus", "--- ${busName} ${shuttle.departureTime} RESULT ---")
        Log.d("ShuttleBus", "Final Calculated Dot Center Y (Absolute): $calculatedDotCenterY")
        Log.d("ShuttleBus", "Bus Image Current Visibility: ${if (busImage.visibility == View.VISIBLE) "VISIBLE" else "GONE"}")
    }
}