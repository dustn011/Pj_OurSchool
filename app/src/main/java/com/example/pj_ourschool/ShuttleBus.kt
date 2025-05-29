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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

// java.time 패키지 임포트 (API 레벨 26 이상 필요)
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import com.example.pj_ourschool.MSSQLConnector // MSSQLConnector 임포트

// 셔틀 데이터 클래스 (DB 스키마 및 쿼리 결과에 맞춤)
data class Shuttle(
    val id: Int,
    val name: String, // bus_type에 해당 (예: "셔틀버스1", "셔틀버스2")
    val dayOfWeek: String, // DB에서 직접 오지 않지만, 기존 로직 호환성을 위해 유지
    val departureTime: String, // HH:mm 형식 (dispatch_time_hm)
    val departureStation: String, // "정문" 또는 "기숙사" (stationYCoordinates 키와 일치하도록 수정)
    val cancelDays: String? // service_date에 해당 (취소 요일 목록, 콤마로 구분)
)

// ShuttleService 객체 수정: 데이터베이스에서 스케줄을 로드하고 요일 기반 결행 로직 적용
object ShuttleService {
    private const val TAG = "ShuttleService" // ShuttleService 태그 추가

    // 제공된 쿼리를 사용하여 모든 셔틀 스케줄을 가져옴 (Kotlin 코드에서 필터링)
    suspend fun getShuttleSchedulesFromDb(): List<Shuttle> = withContext(Dispatchers.IO) {
        val schedules = mutableListOf<Shuttle>()
        var connection: Connection? = null
        var resultSet: ResultSet? = null
        var statement: PreparedStatement? = null

        try {
            Log.d(TAG, "DB 연결 시도 중...")
            connection = MSSQLConnector.getConnection() // 외부 MSSQLConnector 사용
            if (connection != null) {
                Log.d(TAG, "데이터베이스 연결 성공.")
                // 모든 셔틀 스케줄을 가져오도록 쿼리 수정 (TOP 1, 시간 조건 제거)
                val query = """
                    SELECT b.id, b.route_segment, CONVERT(VARCHAR(5), b.dispatch_time, 108) AS dispatch_time_hm, b.bus_type, b.service_date
                    FROM bus_info AS b
                    ORDER BY b.dispatch_time ASC
                """.trimIndent()
                Log.d(TAG, "실행할 SQL 쿼리: $query")
                statement = connection.prepareStatement(query)
                resultSet = statement.executeQuery()

                var rowCount = 0
                while (resultSet.next()) {
                    rowCount++
                    val id = resultSet.getInt("id")
                    val routeSegmentInt = resultSet.getInt("route_segment")
                    val departureTime = resultSet.getString("dispatch_time_hm")
                    val busName = resultSet.getString("bus_type")
                    val serviceDate = resultSet.getString("service_date")

                    val departureStation = if (routeSegmentInt == 1) { // route_segment=1 이 정문 출발이면
                        "정문"
                    } else { // route_segment=0 이 기숙사 출발이면
                        "기숙사"
                    }

                    schedules.add(Shuttle(id, busName, "평일", departureTime, departureStation, serviceDate))
                    Log.d(TAG, "로드된 셔틀 데이터 ($rowCount): ID=$id, BusType=$busName, Time=$departureTime, Station=$departureStation, CancelDays=$serviceDate")
                }
                Log.d(TAG, "DB에서 총 $rowCount 개의 셔틀 스케줄 로드 완료.")
            } else {
                Log.e(TAG, "데이터베이스 연결 실패: MSSQLConnector.getConnection()이 null을 반환했습니다.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "셔틀 스케줄 로드 중 오류 발생: ${e.message}", e)
        } finally {
            try { resultSet?.close() } catch (e: Exception) { Log.e(TAG, "resultSet 닫기 오류: ${e.message}") }
            try { statement?.close() } catch (e: Exception) { Log.e(TAG, "statement 닫기 오류: ${e.message}") }
            try { connection?.close() } catch (e: Exception) { Log.e(TAG, "connection 닫기 오류: ${e.message}") }
            Log.d(TAG, "DB 연결 자원 해제 완료.")
        }
        schedules
    }

    // 주말 여부 확인 함수 (기존 로직 유지)
    fun isHoliday(): Boolean {
        val today = Calendar.getInstance(Locale.KOREA)
        val dayOfWeekInt = today.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dayOfWeekInt == Calendar.SATURDAY || dayOfWeekInt == Calendar.SUNDAY
        Log.d(TAG, "오늘 날짜: ${SimpleDateFormat("yyyy-MM-dd E", Locale.KOREA).format(today.time)}, 주말 여부: $isWeekend")
        return isWeekend
    }

    // 셔틀이 오늘 결행하는지 확인하는 함수
    fun isShuttleCanceledToday(shuttle: Shuttle): Boolean {
        // service_date 컬럼이 null이거나 비어있거나 "NULL" 문자열이면 결행 아님
        if (shuttle.cancelDays.isNullOrEmpty() || shuttle.cancelDays.equals("NULL", ignoreCase = true)) {
            Log.d(TAG, "셔틀ID ${shuttle.id}: cancelDays가 비어있거나 NULL이므로 결행 아님.")
            return false
        }
        // 주말 결행 여부도 추가 검사 (DB service_date 컬럼에 '주말'이 명시될 수도 있지만, 명시되지 않은 경우를 위해 분리)
        if (isHoliday()) {
            Log.d(TAG, "셔틀ID ${shuttle.id}: 오늘은 주말이므로 결행 처리.")
            return true // 주말이면 무조건 결행
        }

        // 현재 요일을 한국어 약어로 가져옴 (예: 월, 화, 수)
        val currentDayOfWeek = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREA)
        // DB의 cancelDays 문자열을 콤마로 분리하여 리스트로 만듦
        val canceledDays = shuttle.cancelDays.split(",").map { it.trim() }

        val isCanceled = canceledDays.contains(currentDayOfWeek)
        Log.d(TAG, "셔틀ID ${shuttle.id}: 현재 요일 '$currentDayOfWeek', 취소 요일 '$canceledDays', 결행 여부: $isCanceled")
        return isCanceled
    }
}

class ShuttleBus : AppCompatActivity() {
    private val TAG = "ShuttleBus" // ShuttleBus 태그 추가

    private val stationYCoordinates = mutableMapOf<String, Float>()
    private val stationOrderDown = listOf("정문", "중문", "보건의료대학", "학생회관", "예술대학", "기숙사") // 정문 출발 노선
    private val stationOrderUp = listOf("기숙사", "예술대학", "학생회관", "보건의료대학", "중문", "정문") // 기숙사 출발 노선

    private lateinit var busDown1: ImageView // 정문 출발 버스 이미지
    private lateinit var busUp1: ImageView // 기숙사 출발 버스 이미지

    private var animationHandler: Handler? = null
    private var updateRunnable: Runnable? = null

    // --- 상수 정의 ---
    private val ANIMATION_INTERVAL_MS = 200L // 0.2초마다 위치 업데이트 (화면 갱신 주기)
    private val RELOAD_SCHEDULE_INTERVAL_MS = 10000L // 10초마다 DB에서 스케줄을 다시 로드 (쿼리 특성상 필요)
    private val STATIONARY_DURATION_SECONDS = 60L // 각 정류장 및 중간 지점에서 정차하는 시간 (1분)
    private val TRAVEL_DURATION_BETWEEN_POINTS_SECONDS = 0L // 정차 지점 간 이동 시간 (0초, 즉시 이동)

    // 버스 이미지들의 "기준선" Y 좌표를 저장 (translationY=0일 때의 기준)
    private var busDown1BaseY: Float = -1f
    private var busUp1BaseY: Float = -1f

    // 현재 로드된 셔틀 스케줄 데이터
    private var currentActiveShuttles: List<Shuttle> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shuttlebus)
        Log.d(TAG, "ShuttleBus onCreate 호출됨.")

        busDown1 = findViewById(R.id.bus_down_1) // 정문 출발 버스 이미지뷰
        busUp1 = findViewById(R.id.bus_up_1)     // 기숙사 출발 버스 이미지뷰

        val profileImageView: ImageView = findViewById(R.id.Profile)
        val homeImageView: ImageView = findViewById(R.id.home)
        val timeImageView: ImageView = findViewById(R.id.time)
        val campusImageView: ImageView = findViewById(R.id.campus)
        val chatImageView: ImageView = findViewById(R.id.chat)
        val leftArrow: ImageView = findViewById(R.id.left_arrow)

        profileImageView.setOnClickListener {
            startActivity(Intent(this, Profile::class.java))
        }
        homeImageView.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        timeImageView.setOnClickListener {
            startActivity(Intent(this, Time::class.java))
        }
        campusImageView.setOnClickListener {
            startActivity(Intent(this, Campus::class.java))
        }
        chatImageView.setOnClickListener {
            startActivity(Intent(this, Chat::class.java))
        }
        leftArrow.setOnClickListener {
            finish()
        }

        // 뷰의 레이아웃이 완료될 때까지 기다렸다가 좌표 초기화 및 업데이트 시작
        findViewById<View>(R.id.station_1_text).viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // 리스너는 한 번만 실행되도록 제거
                findViewById<View>(R.id.station_1_text).viewTreeObserver.removeOnGlobalLayoutListener(this)

                // 정류장 Y 좌표 초기화
                stationYCoordinates["정문"] = findViewById<View>(R.id.dot_left_1).y + findViewById<View>(R.id.dot_left_1).height / 2f
                stationYCoordinates["중문"] = findViewById<View>(R.id.dot_left_2).y + findViewById<View>(R.id.dot_left_2).height / 2f
                stationYCoordinates["보건의료대학"] = findViewById<View>(R.id.dot_left_3).y + findViewById<View>(R.id.dot_left_3).height / 2f
                stationYCoordinates["학생회관"] = findViewById<View>(R.id.dot_left_4).y + findViewById<View>(R.id.dot_left_4).height / 2f
                stationYCoordinates["예술대학"] = findViewById<View>(R.id.dot_left_5).y + findViewById<View>(R.id.dot_left_5).height / 2f
                stationYCoordinates["기숙사"] = findViewById<View>(R.id.dot_left_6).y + findViewById<View>(R.id.dot_left_6).height / 2f

                stationYCoordinates["기숙사(오른쪽)"] = findViewById<View>(R.id.dot_right_6).y + findViewById<View>(R.id.dot_right_6).height / 2f
                stationYCoordinates["예술대학(오른쪽)"] = findViewById<View>(R.id.dot_right_5).y + findViewById<View>(R.id.dot_right_5).height / 2f
                stationYCoordinates["학생회관(오른쪽)"] = findViewById<View>(R.id.dot_right_4).y + findViewById<View>(R.id.dot_right_4).height / 2f
                stationYCoordinates["보건의료대학(오른쪽)"] = findViewById<View>(R.id.dot_right_3).y + findViewById<View>(R.id.dot_right_3).height / 2f
                stationYCoordinates["중문(오른쪽)"] = findViewById<View>(R.id.dot_right_2).y + findViewById<View>(R.id.dot_right_2).height / 2f
                stationYCoordinates["정문(오른쪽)"] = findViewById<View>(R.id.dot_right_1).y + findViewById<View>(R.id.dot_right_1).height / 2f


                // 버스 이미지의 기준 Y 좌표 초기화
                if (busDown1BaseY == -1f) {
                    busDown1BaseY = busDown1.y
                }
                if (busUp1BaseY == -1f) {
                    busUp1BaseY = busUp1.y
                }

                // 주기적인 셔틀 업데이트 시작 (DB에서 스케줄 로드 포함)
                startPeriodicShuttleUpdate()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ShuttleBus onDestroy 호출됨. 애니메이션 업데이트 중지.")
        animationHandler?.removeCallbacksAndMessages(null) // 모든 콜백 제거
        animationHandler = null
        updateRunnable = null
    }

    private fun startPeriodicShuttleUpdate() {
        Log.d(TAG, "주기적인 셔틀 업데이트 시작.")
        animationHandler = Handler(Looper.getMainLooper())
        updateRunnable = object : Runnable {
            override fun run() {
                lifecycleScope.launch {
                    Log.d(TAG, "DB에서 새로운 셔틀 스케줄 로드 시도 중...")
                    val fetchedShuttles = ShuttleService.getShuttleSchedulesFromDb()
                    if (fetchedShuttles != currentActiveShuttles) {
                        currentActiveShuttles = fetchedShuttles
                        Log.d(TAG, "새로운 셔틀 스케줄 로드됨: ${currentActiveShuttles.size}개. 데이터: $currentActiveShuttles")
                    } else {
                        Log.d(TAG, "셔틀 스케줄 변경 없음.")
                    }
                    updateShuttlePositions(currentActiveShuttles)
                }
                // ANIMATION_INTERVAL_MS 대신 RELOAD_SCHEDULE_INTERVAL_MS를 사용할지 고려
                // 셔틀 위치는 0.2초마다, 스케줄 로드는 10초마다 하려면 별도의 Runnable 필요
                animationHandler?.postDelayed(this, ANIMATION_INTERVAL_MS)
            }
        }
        animationHandler?.post(updateRunnable!!)

        // 선택 사항: 스케줄 로드 주기와 애니메이션 업데이트 주기를 분리하려면 아래와 같이 다른 Runnable 사용
        /*
        val reloadScheduleRunnable = object : Runnable {
            override fun run() {
                lifecycleScope.launch {
                    Log.d(TAG, "DB에서 새로운 셔틀 스케줄 로드 시도 중 (주기적)...")
                    val fetchedShuttles = ShuttleService.getShuttleSchedulesFromDb()
                    if (fetchedShuttles != currentActiveShuttles) {
                        currentActiveShuttles = fetchedShuttles
                        Log.d(TAG, "새로운 셔틀 스케줄 로드됨: ${currentActiveShuttles.size}개.")
                        // 스케줄이 변경되면 즉시 위치 업데이트
                        updateShuttlePositions(currentActiveShuttles)
                    } else {
                        Log.d(TAG, "셔틀 스케줄 변경 없음.")
                    }
                }
                animationHandler?.postDelayed(this, RELOAD_SCHEDULE_INTERVAL_MS)
            }
        }
        animationHandler?.post(reloadScheduleRunnable)
        */
    }

    // 셔틀의 출발지에 따라 해당 노선의 길이를 반환하는 헬퍼 함수 추가
    private fun getRouteLength(departureStation: String): Int {
        return if (departureStation == "기숙사") {
            stationOrderUp.size // 기숙사 출발이면 상행 노선 길이
        } else { // 기본적으로 정문 출발로 간주
            stationOrderDown.size // 정문 출발이면 하행 노선 길이
        }
    }

    // updateShuttlePositions 함수가 파라미터로 activeShuttles를 받도록 변경
    private fun updateShuttlePositions(activeShuttles: List<Shuttle>) {
        val now = Calendar.getInstance()
        val currentTimeMillis = now.timeInMillis
        Log.d(TAG, "updateShuttlePositions 호출됨. 현재 시간: ${SimpleDateFormat("HH:mm:ss").format(now.time)}")

        // 정문 출발 셔틀과 기숙사 출발 셔틀을 분리하여 저장할 변수
        // 이제 findBestShuttle 함수를 통해 현재 표시할 셔틀을 결정합니다.
        val shuttleDepartingFromFrontGate = findBestShuttle(
            activeShuttles.filter { it.departureStation == "정문" },
            currentTimeMillis,
            now
        )
        val shuttleDepartingFromDorm = findBestShuttle(
            activeShuttles.filter { it.departureStation == "기숙사" },
            currentTimeMillis,
            now
        )

        Log.d(TAG, "정문 출발 (하행) 셔틀 할당: ${shuttleDepartingFromFrontGate?.name ?: "없음"} (Time: ${shuttleDepartingFromFrontGate?.departureTime ?: "N/A"})")
        // initialDot을 넘겨줄 때, 정문 출발 버스라면 정문 dot (dot_left_1)을 사용해야 합니다.
        updateSingleShuttlePosition(shuttleDepartingFromFrontGate, busDown1, stationOrderDown, "하행 셔틀 (정문 출발)", busDown1BaseY, findViewById(R.id.dot_left_1))

        Log.d(TAG, "기숙사 출발 (상행) 셔틀 할당: ${shuttleDepartingFromDorm?.name ?: "없음"} (Time: ${shuttleDepartingFromDorm?.departureTime ?: "N/A"})")
        // initialDot을 넘겨줄 때, 기숙사 출발 버스라면 기숙사 dot (dot_right_6)을 사용해야 합니다.
        updateSingleShuttlePosition(shuttleDepartingFromDorm, busUp1, stationOrderUp, "상행 셔틀 (기숙사 출발)", busUp1BaseY, findViewById(R.id.dot_right_6))
    }

    /**
     * 주어진 셔틀 리스트에서 현재 시간에 가장 적합한 셔틀(현재 운행 중이거나, 가장 가까운 미래 출발 셔틀)을 찾습니다.
     * 운행 중인 셔틀이 없거나, 다음 출발 셔틀이 없는 경우 null을 반환합니다.
     */
    private fun findBestShuttle(
        shuttles: List<Shuttle>,
        currentTimeMillis: Long,
        currentCalendar: Calendar
    ): Shuttle? {
        // 1. 현재 운행 중인 셔틀을 우선적으로 찾습니다. (가장 최근에 출발한 셔틀을 선택)
        var operatingShuttle: Shuttle? = null
        var operatingShuttleDepartureTime: Long = -1L

        for (shuttle in shuttles) {
            // 결행 셔틀은 제외
            if (ShuttleService.isShuttleCanceledToday(shuttle)) {
                continue
            }

            val timeParts = shuttle.departureTime.split(":")
            val hour = timeParts[0].toIntOrNull() ?: continue
            val minute = timeParts[1].toIntOrNull() ?: continue

            val departureCal = currentCalendar.clone() as Calendar
            departureCal.set(Calendar.HOUR_OF_DAY, hour)
            departureCal.set(Calendar.MINUTE, minute)
            departureCal.set(Calendar.SECOND, 0)
            departureCal.set(Calendar.MILLISECOND, 0)
            val departureMillis = departureCal.timeInMillis

            val currentRouteLength = getRouteLength(shuttle.departureStation)
            // 총 운행 종료 시간 계산 (마지막 정류장 정차 시간을 포함하여 넉넉하게 계산)
            val totalRouteDurationSeconds = (currentRouteLength * STATIONARY_DURATION_SECONDS) + (maxOf(0, currentRouteLength - 1) * STATIONARY_DURATION_SECONDS)
            val estimatedEndTimeMillis = departureMillis + (totalRouteDurationSeconds + STATIONARY_DURATION_SECONDS) * 1000L

            // 현재 운행 중인 셔틀인지 확인
            if (currentTimeMillis >= departureMillis && currentTimeMillis < estimatedEndTimeMillis) {
                // 운행 중인 셔틀 중 가장 최근에 출발한 셔틀을 선택
                if (operatingShuttle == null || departureMillis > operatingShuttleDepartureTime) {
                    operatingShuttle = shuttle
                    operatingShuttleDepartureTime = departureMillis
                }
            }
        }

        // 운행 중인 셔틀이 있다면 해당 셔틀을 반환
        if (operatingShuttle != null) {
            Log.d(TAG, "findBestShuttle: 운행 중인 셔틀 발견 - ID=${operatingShuttle.id}, Time=${operatingShuttle.departureTime}")
            return operatingShuttle
        }

        // 2. 운행 중인 셔틀이 없다면, 현재 시간 이후의 가장 빠른 출발 예정 셔틀을 찾습니다.
        var nextUpcomingShuttle: Shuttle? = null
        var minTimeDiffMillis: Long = Long.MAX_VALUE

        for (shuttle in shuttles) {
            // 결행 셔틀은 제외
            if (ShuttleService.isShuttleCanceledToday(shuttle)) {
                continue
            }

            val timeParts = shuttle.departureTime.split(":")
            val hour = timeParts[0].toIntOrNull() ?: continue
            val minute = timeParts[1].toIntOrNull() ?: continue

            val departureCal = currentCalendar.clone() as Calendar
            departureCal.set(Calendar.HOUR_OF_DAY, hour)
            departureCal.set(Calendar.MINUTE, minute)
            departureCal.set(Calendar.SECOND, 0)
            departureCal.set(Calendar.MILLISECOND, 0)
            val departureMillis = departureCal.timeInMillis

            // 아직 출발하지 않은 셔틀 중에서 가장 빠른 것을 찾음
            if (departureMillis > currentTimeMillis) {
                val timeDiff = departureMillis - currentTimeMillis
                if (timeDiff < minTimeDiffMillis) {
                    minTimeDiffMillis = timeDiff
                    nextUpcomingShuttle = shuttle
                }
            }
        }

        if (nextUpcomingShuttle != null) {
            Log.d(TAG, "findBestShuttle: 다음 출발 예정 셔틀 선택 - ID=${nextUpcomingShuttle.id}, Time=${nextUpcomingShuttle.departureTime}")
        } else {
            Log.d(TAG, "findBestShuttle: 운행 중인 셔틀도, 다음 출발 예정 셔틀도 없음.")
        }
        return nextUpcomingShuttle
    }

    // 단일 셔틀버스 이미지뷰를 업데이트하는 함수
    private fun updateSingleShuttlePosition(
        shuttle: Shuttle?,
        busImage: ImageView,
        route: List<String>,
        busName: String,
        baseBusY: Float,
        initialDot: ImageView
    ) {
        val now = Calendar.getInstance()
        val currentTimeMillis = now.timeInMillis
        Log.d(TAG, "updateSingleShuttlePosition 호출됨: $busName")

        if (shuttle == null) {
            // 셔틀이 null이면 버스 이미지를 숨김
            if (busImage.visibility == View.VISIBLE) {
                busImage.visibility = View.GONE
                Log.d(TAG, "$busName: 할당된 셔틀 없음. 버스 숨김 처리.")
            } else {
                Log.d(TAG, "$busName: 할당된 셔틀 없음. 이미 숨겨져 있음.")
            }
            return
        }

        // 셔틀버스 이미지 초기 위치 설정 (한 번만 초기화, visible 상태 아닐 때만)
        if (busImage.visibility != View.VISIBLE) { // 버스가 현재 보이지 않는 상태라면
            val initialDotCenterY = initialDot.y + initialDot.height / 2f
            val busHeight = busImage.height.toFloat()
            val busCenterYToImageTopOffset = busHeight / 2f
            val targetBusImageTopYAbsolute = initialDotCenterY - busCenterYToImageTopOffset
            val initialTranslationY = targetBusImageTopYAbsolute - baseBusY
            busImage.translationY = initialTranslationY
            busImage.visibility = View.VISIBLE // 이제 보이도록 설정
            Log.d(TAG, "$busName: 버스 이미지 초기 위치 설정 및 표시. Initial Y: $initialTranslationY")
        } else {
            Log.d(TAG, "$busName: 버스 이미지 이미 표시 중. 현재 translationY: ${busImage.translationY}")
        }

        // 출발 시간 파싱 (HH:mm 형식)
        val timeParts = shuttle.departureTime.split(":")
        if (timeParts.size < 2) {
            Log.e(TAG, "$busName: 잘못된 departureTime 형식: ${shuttle.departureTime}. 버스 숨김 처리.")
            busImage.visibility = View.GONE
            return
        }
        val hour = timeParts[0].toIntOrNull() ?: run {
            Log.e(TAG, "$busName: 시간 변환 실패: ${shuttle.departureTime}. 버스 숨김 처리.")
            busImage.visibility = View.GONE
            return
        }
        val minute = timeParts[1].toIntOrNull() ?: run {
            Log.e(TAG, "$busName: 분 변환 실패: ${shuttle.departureTime}. 버스 숨김 처리.")
            busImage.visibility = View.GONE
            return
        }

        val departureCal = now.clone() as Calendar
        departureCal.set(Calendar.HOUR_OF_DAY, hour)
        departureCal.set(Calendar.MINUTE, minute)
        departureCal.set(Calendar.SECOND, 0)
        departureCal.set(Calendar.MILLISECOND, 0)
        val departureMillis = departureCal.timeInMillis
        Log.d(TAG, "$busName: 출발 시간 설정됨 - ${SimpleDateFormat("HH:mm:ss").format(departureCal.time)}")

        val elapsedSecondsFromDeparture = maxOf(0L, (currentTimeMillis - departureMillis) / 1000L)
        Log.d(TAG, "$busName: 현재 시간 대비 출발 후 경과 시간: $elapsedSecondsFromDeparture 초")

        val busHeight = busImage.height.toFloat()
        val busCenterYToImageTopOffset = busHeight / 2f

        var calculatedDotCenterY: Float = 0f
        var hasPositionFound = false

        var accumulatedSegmentTime = 0L

        // 총 운행 시간 계산 (최대 이동 가능 시간)
        // 각 정류장 및 중간 지점에서 1분 정차, 총 정류장 수 (route.size) * 1분 + 중간 지점 수 (route.size - 1) * 1분
        val totalRouteDurationSeconds = (route.size * STATIONARY_DURATION_SECONDS) + (maxOf(0, route.size - 1) * STATIONARY_DURATION_SECONDS)
        Log.d(TAG, "$busName: 총 운행 예상 시간: $totalRouteDurationSeconds 초 (route.size: ${route.size})")


        // 운행이 이미 종료되었는지 확인 (이 로직은 findBestShuttle에서 먼저 처리되지만, 혹시 모를 경우를 대비해 유지)
        if (elapsedSecondsFromDeparture >= totalRouteDurationSeconds + STATIONARY_DURATION_SECONDS) {
            if (busImage.visibility == View.VISIBLE) {
                busImage.visibility = View.GONE
                Log.d(TAG, "$busName: 운행 종료 시간 초과 (${elapsedSecondsFromDeparture}초 >= ${totalRouteDurationSeconds + STATIONARY_DURATION_SECONDS}초). 버스 숨김 처리.")
            } else {
                Log.d(TAG, "$busName: 운행 종료 시간 초과. 이미 숨겨져 있음.")
            }
            return
        }

        for (i in route.indices) {
            val currentStation = route[i]
            val currentStationY = stationYCoordinates[currentStation]
            if (currentStationY == null) {
                Log.e(TAG, "$busName: 정류장 '$currentStation'의 Y 좌표를 찾을 수 없습니다.")
                continue
            }

            val stopAtStationStartTime = accumulatedSegmentTime
            val stopAtStationEndTime = stopAtStationStartTime + STATIONARY_DURATION_SECONDS

            Log.d(TAG, "$busName: ${currentStation} 정차 구간 ($stopAtStationStartTime ~ $stopAtStationEndTime 초)")

            if (elapsedSecondsFromDeparture >= stopAtStationStartTime && elapsedSecondsFromDeparture < stopAtStationEndTime) {
                calculatedDotCenterY = currentStationY
                hasPositionFound = true
                Log.d(TAG, "$busName: ${currentStation} 정차 중. Y=$calculatedDotCenterY")
                break
            }
            accumulatedSegmentTime = stopAtStationEndTime

            if (i < route.size - 1) {
                val nextStation = route[i + 1]
                val nextStationY = stationYCoordinates[nextStation]
                if (nextStationY == null) {
                    Log.e(TAG, "$busName: 다음 정류장 '$nextStation'의 Y 좌표를 찾을 수 없습니다.")
                    continue
                }
                val midPointY = (currentStationY + nextStationY) / 2f

                val stopAtMidPointStartTime = accumulatedSegmentTime
                val stopAtMidPointEndTime = stopAtMidPointStartTime + STATIONARY_DURATION_SECONDS

                Log.d(TAG, "$busName: ${currentStation}-${nextStation} 중간 지점 정차 구간 ($stopAtMidPointStartTime ~ $stopAtMidPointEndTime 초)")

                if (elapsedSecondsFromDeparture >= stopAtMidPointStartTime && elapsedSecondsFromDeparture < stopAtMidPointEndTime) {
                    calculatedDotCenterY = midPointY
                    hasPositionFound = true
                    Log.d(TAG, "$busName: ${currentStation}-${nextStation} 중간 지점 정차 중. Y=$calculatedDotCenterY")
                    break
                }
                accumulatedSegmentTime = stopAtMidPointEndTime
            }
        }

        if (hasPositionFound) {
            val targetBusImageTopYAbsolute = calculatedDotCenterY - busCenterYToImageTopOffset
            val newTargetTranslationY = targetBusImageTopYAbsolute - baseBusY

            busImage.translationY = newTargetTranslationY
            Log.d(TAG, "$busName: 위치 업데이트 성공. 최종 Y (translationY): $newTargetTranslationY")
            if (busImage.visibility != View.VISIBLE) {
                busImage.visibility = View.VISIBLE
                Log.d(TAG, "$busName: 위치 업데이트 후 버스 표시.")
            }
        } else {
            // 위치를 찾지 못했으면 (아직 출발 전이거나 예상치 못한 경우)
            // findBestShuttle에서 이미 필터링되므로 이 else 블록에 들어올 일은 거의 없지만,
            // 만약을 위해 버스를 숨기는 로직을 유지
            if (busImage.visibility == View.VISIBLE) {
                busImage.visibility = View.GONE
                Log.d(TAG, "$busName: 현재 경과 시간 (${elapsedSecondsFromDeparture}초)에 해당하는 위치를 찾을 수 없음. 버스 숨김 처리.")
            } else {
                Log.d(TAG, "$busName: 현재 경과 시간에 해당하는 위치를 찾을 수 없음. 이미 숨겨져 있음.")
            }
        }
    }
}