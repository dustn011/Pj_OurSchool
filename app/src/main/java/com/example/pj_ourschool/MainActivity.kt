package com.example.pj_ourschool

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.sql.Connection
import java.sql.ResultSet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ShuttleData.kt 파일에서 정의된 클래스와 객체를 import 합니다.
import com.example.pj_ourschool.Shuttle
import com.example.pj_ourschool.ShuttleService


class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // UI 요소 변수
    private lateinit var timeImageView: ImageView
    private lateinit var campusImageView: ImageView
    private lateinit var busImageView: ImageView
    private lateinit var chatImageView: ImageView
    private lateinit var profileImageView: CircleImageView
    private lateinit var plusScreen1: LinearLayout
    private lateinit var plusScreen2: LinearLayout
    private lateinit var plusScreen3: LinearLayout
    private lateinit var plusScreen1TextView: TextView
    private lateinit var weatherImageView: ImageView
    private lateinit var todayScheduleTextView: TextView
    private lateinit var homepageWj: LinearLayout
    private lateinit var shceduleWj: LinearLayout
    private lateinit var infoWj: LinearLayout
    private lateinit var edelweisWj: LinearLayout
    private lateinit var portalWj: LinearLayout

    // 셔틀 위젯 관련 변수
    // shuttle_next_time_text ID를 가져와 현재 위치 표시용으로 사용합니다.
    private lateinit var shuttleCurrentLocationText: TextView
    private var shuttleUpdateHandler: Handler? = null
    private var shuttleUpdateRunnable: Runnable? = null
    private val SHUTTLE_UPDATE_INTERVAL_MS = 15000L // 15초마다 업데이트

    private val PROFILE_IMAGE_PREF = "profile_image_pref"
    private val KEY_PROFILE_IMAGE_URI = "profile_image_uri"

    // 셔틀 위치 계산에 사용되는 상수
    private val STATIONARY_DURATION_SECONDS = 60L
    private val STATION_ORDER_DOWN = listOf("정문", "중문", "보건의료대학", "학생회관", "예술대학", "기숙사")
    private val STATION_ORDER_UP = listOf("기숙사", "예술대학", "학생회관", "보건의료대학", "중문", "정문")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 요소 초기화
        timeImageView = findViewById(R.id.time)
        campusImageView = findViewById(R.id.campus)
        busImageView = findViewById(R.id.bus)
        chatImageView = findViewById(R.id.chat)
        profileImageView = findViewById(R.id.Profile)
        plusScreen1 = findViewById(R.id.plusScreen1)
        plusScreen2 = findViewById(R.id.plusScreen2)
        plusScreen3 = findViewById(R.id.plusScreen3)
        weatherImageView = findViewById(R.id.weatherImageView)
        plusScreen1TextView = findViewById(R.id.plusScreen1TextView)
        todayScheduleTextView = findViewById(R.id.todayScheduleTextView)
        homepageWj = findViewById(R.id.homepage_wj)
        shceduleWj = findViewById(R.id.shcedule_wj)
        infoWj = findViewById(R.id.info_wj)
        edelweisWj = findViewById(R.id.edelweis_wj)
        portalWj = findViewById(R.id.portal_wj)

        // 셔틀 위젯 TextView 초기화
        // XML에서 shuttle_next_time_text가 유일한 TextView로 수정되었으므로, 해당 ID를 사용합니다.
        shuttleCurrentLocationText = findViewById(R.id.shuttle_next_time_text)


        loadProfileImageUri()

        // --- 클릭 리스너 설정 ---
        profileImageView.setOnClickListener { startActivity(Intent(this, Profile::class.java)) }
        timeImageView.setOnClickListener { startActivity(Intent(this, Time::class.java)) }
        plusScreen2.setOnClickListener { startActivity(Intent(this, Time::class.java)) }
        campusImageView.setOnClickListener { startActivity(Intent(this, Campus::class.java)) }
        busImageView.setOnClickListener { startActivity(Intent(this, ShuttleBus::class.java)) }
        chatImageView.setOnClickListener { startActivity(Intent(this, Chat::class.java)) }
        plusScreen3.setOnClickListener { startActivity(Intent(this, ShuttleBus::class.java)) }

        // 위젯 클릭 리스너 (URL 이동)
        homepageWj.setOnClickListener { openUrl("https://www.cju.ac.kr/www/index.do") }
        shceduleWj.setOnClickListener { openUrl("https://www.cju.ac.kr/www/selectTnSchafsSchdulListUS.do?key=4498") }
        infoWj.setOnClickListener { openUrl("https://cju.ac.kr/www/selectBbsNttList.do?key=4577&bbsNo=881&integrDeptCode=&searchCtgry=") }
        edelweisWj.setOnClickListener { openUrl("https://hive.cju.ac.kr/common/greeting.do") }
        portalWj.setOnClickListener { openUrl("https://portal.cju.ac.kr") }

        // 날씨 정보 및 시간표 로드
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            getCurrentLocationAndFetchWeather()
        }

        loadTodayTimetable()
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        // 액티비티가 시작되면 셔틀 업데이트를 시작합니다.
        startPeriodicShuttleUpdate()
    }

    override fun onResume() {
        super.onResume()
        loadProfileImageUri()
    }

    override fun onStop() {
        super.onStop()
        // 액티비티가 중단되면 업데이트를 중지합니다.
        shuttleUpdateHandler?.removeCallbacksAndMessages(null)
        shuttleUpdateHandler = null
        shuttleUpdateRunnable = null
    }

    // =================================================================
    // 셔틀 위젯 관련 함수 (수정된 부분)
    // =================================================================

    private fun startPeriodicShuttleUpdate() {
        shuttleUpdateHandler = Handler(Looper.getMainLooper())
        shuttleUpdateRunnable = object : Runnable {
            override fun run() {
                updateShuttleWidget()
                shuttleUpdateHandler?.postDelayed(this, SHUTTLE_UPDATE_INTERVAL_MS)
            }
        }
        shuttleUpdateHandler?.post(shuttleUpdateRunnable!!)
    }

    private fun updateShuttleWidget() {
        CoroutineScope(Dispatchers.Main).launch {
            val allShuttles = ShuttleService.getShuttleSchedulesFromDb()

            val currentTimeMillis = Calendar.getInstance().timeInMillis
            val currentCalendar = Calendar.getInstance()

            // 현재 운행 중이거나 다음 예정인 셔틀을 찾습니다.
            val bestFrontShuttle = findBestShuttle(
                allShuttles.filter { it.departureStation == "정문" },
                currentTimeMillis,
                currentCalendar
            )
            val bestDormShuttle = findBestShuttle(
                allShuttles.filter { it.departureStation == "기숙사" },
                currentTimeMillis,
                currentCalendar
            )

            // UI 업데이트
            updateShuttleTexts(bestFrontShuttle, bestDormShuttle)
        }
    }

    /**
     * 주어진 셔틀 리스트에서 현재 시간에 가장 적합한 셔틀(현재 운행 중이거나, 가장 가까운 미래 출발 셔틀)을 찾습니다.
     */
    private fun findBestShuttle(
        shuttles: List<Shuttle>,
        currentTimeMillis: Long,
        currentCalendar: Calendar
    ): Shuttle? {
        // 1. 현재 운행 중인 셔틀을 우선적으로 찾습니다.
        var operatingShuttle: Shuttle? = null
        var operatingShuttleDepartureTime: Long = -1L

        for (shuttle in shuttles) {
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

            val route = if (shuttle.departureStation == "기숙사") STATION_ORDER_UP else STATION_ORDER_DOWN

            // 정류장 수 (6) * 정차 시간 (60초) + 중간 지점 수 (5) * 정차 시간 (60초)
            val stationStops = route.size.toLong() * STATIONARY_DURATION_SECONDS
            val midPointStops = maxOf(0, route.size - 1).toLong() * STATIONARY_DURATION_SECONDS

            // 셔틀이 종점 정차를 시작하는 시점까지의 총 누적 시간 (600초)
            val totalAccumulatedTimeSeconds = stationStops + midPointStops

            // 총 예상 운행 종료 시간 (마지막 정류장 정차 시간을 마치는 시점)
            // totalAccumulatedTimeSeconds (종점 정차 시작 시간) + STATIONARY_DURATION_SECONDS (종점 정차 시간)
            val estimatedEndTimeMillis = departureMillis + (totalAccumulatedTimeSeconds * 1000L)

            // (선택 사항: ShutterBus의 로직과 완벽히 일치시키려면 + 1초를 추가하여 오차 방지)
            val estimatedEndTimeMillisWithSafety = estimatedEndTimeMillis + 1000L

            if (currentTimeMillis >= departureMillis && currentTimeMillis < estimatedEndTimeMillis) {
                if (operatingShuttle == null || departureMillis > operatingShuttleDepartureTime) {
                    operatingShuttle = shuttle
                    operatingShuttleDepartureTime = departureMillis
                }
            }
        }

        if (operatingShuttle != null) {
            return operatingShuttle
        }

        // 2. 운행 중인 셔틀이 없다면, 가장 빠른 출발 예정 셔틀을 찾습니다.
        var nextUpcomingShuttle: Shuttle? = null
        var minTimeDiffMillis: Long = Long.MAX_VALUE

        for (shuttle in shuttles) {
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

            if (departureMillis > currentTimeMillis) {
                val timeDiff = departureMillis - currentTimeMillis
                if (timeDiff < minTimeDiffMillis) {
                    minTimeDiffMillis = timeDiff
                    nextUpcomingShuttle = shuttle
                }
            }
        }
        return nextUpcomingShuttle
    }

    /**
     * 셔틀 위젯의 텍스트를 업데이트합니다. (현재 위치/도착 정보만 사용)
     */
    private fun updateShuttleTexts(
        bestFrontShuttle: Shuttle?,
        bestDormShuttle: Shuttle?
    ) {
        val currentCalendar = Calendar.getInstance()
        val currentTimeMillis = currentCalendar.timeInMillis

        // --- 현재 위치/상태 텍스트 설정 (shuttle_current_location_text) ---
        val locationSb = StringBuilder()

        // 셔틀 1 (정문 출발)
        val frontLocation = calculateShuttleNextArrival(bestFrontShuttle, currentTimeMillis, currentCalendar, STATION_ORDER_DOWN, "정문")
        locationSb.append("셔틀 1 | $frontLocation\n")

        // 셔틀 2 (기숙사 출발)
        val dormLocation = calculateShuttleNextArrival(bestDormShuttle, currentTimeMillis, currentCalendar, STATION_ORDER_UP, "기숙사")
        locationSb.append("셔틀 2 | $dormLocation")

        shuttleCurrentLocationText.text = locationSb.toString()
    }

    private fun isShuttleCurrentlyOperating(shuttle: Shuttle, currentTimeMillis: Long, currentCalendar: Calendar): Boolean {
        val route = if (shuttle.departureStation == "기숙사") STATION_ORDER_UP else STATION_ORDER_DOWN
        val totalRouteDurationSeconds = (route.size * STATIONARY_DURATION_SECONDS) + (maxOf(0, route.size - 1) * 0L)

        val timeParts = shuttle.departureTime.split(":")
        val hour = timeParts[0].toIntOrNull() ?: return false
        val minute = timeParts[1].toIntOrNull() ?: return false

        val departureCal = currentCalendar.clone() as Calendar
        departureCal.set(Calendar.HOUR_OF_DAY, hour)
        departureCal.set(Calendar.MINUTE, minute)
        departureCal.set(Calendar.SECOND, 0)
        departureCal.set(Calendar.MILLISECOND, 0)
        val departureMillis = departureCal.timeInMillis

        // 총 운행 시간 (마지막 정차 시간까지)
        val estimatedEndTimeMillis = departureMillis + (totalRouteDurationSeconds + STATIONARY_DURATION_SECONDS) * 1000L

        return currentTimeMillis >= departureMillis && currentTimeMillis < estimatedEndTimeMillis
    }

    /**
     * 셔틀의 다음 도착 예정지 및 남은 시간을 계산하여 문자열로 반환합니다.
     */
    private fun calculateShuttleNextArrival(
        shuttle: Shuttle?,
        currentTimeMillis: Long,
        currentCalendar: Calendar,
        route: List<String>,
        departureStationName: String // "정문" 또는 "기숙사"
    ): String {
        if (shuttle == null || ShuttleService.isShuttleCanceledToday(shuttle)) {
            // 운행 예정이 없는 경우 (findBestShuttle이 null 반환)
            return "운행 정보 없음"
        }

        val timeParts = shuttle.departureTime.split(":")
        val hour = timeParts[0].toIntOrNull() ?: return "오류 발생"
        val minute = timeParts[1].toIntOrNull() ?: return "오류 발생"

        val departureCal = currentCalendar.clone() as Calendar
        departureCal.set(Calendar.HOUR_OF_DAY, hour)
        departureCal.set(Calendar.MINUTE, minute)
        departureCal.set(Calendar.SECOND, 0)
        departureCal.set(Calendar.MILLISECOND, 0)
        val departureMillis = departureCal.timeInMillis

        // 1. 아직 출발하지 않은 경우
        if (currentTimeMillis < departureMillis) {
            val timeUntilDepartureSec = (departureMillis - currentTimeMillis) / 1000L
            val remainingMin = maxOf(1L, timeUntilDepartureSec / 60)

            // 다음 출발 예정 시간도 함께 표시합니다.
            return "${shuttle.departureTime} 출발 예정 (${remainingMin}분 후 출발)"
        }

        // 2. 운행 중인 셔틀의 경우
        val elapsedSecondsFromDeparture = (currentTimeMillis - departureMillis) / 1000L
        var accumulatedSegmentTime = 0L // 출발 후 누적된 시간 (초)
        val segmentDuration = STATIONARY_DURATION_SECONDS // 정류장 또는 중간 지점 정차 시간 (60초)

        // 총 운행 시간 계산 (마지막 정차 시간까지)
        // (정류장 수 * 60초) + (중간 지점 수 * 60초) = (route.size * 60) + ((route.size - 1) * 60)
        val totalRouteDurationSeconds = (route.size.toLong() * STATIONARY_DURATION_SECONDS) +
                (maxOf(0, route.size - 1).toLong() * STATIONARY_DURATION_SECONDS)

        // 운행 종료된 셔틀
        if (elapsedSecondsFromDeparture >= totalRouteDurationSeconds) {
            return "운행 종료"
        }

        // --- 위치 및 다음 도착 시간 계산 ---
        for (i in route.indices) {
            val currentStation = route[i]

            // A. 정류장 정차 구간
            val stopAtStationStartTime = accumulatedSegmentTime
            val stopAtStationEndTime = stopAtStationStartTime + segmentDuration

            if (elapsedSecondsFromDeparture < stopAtStationEndTime) {
                // 현재 이 정류장에 정차 중이거나 이 정류장으로 이동 중인 경우 (이동 시간 0초 가정)
                val nextStopName = if (i < route.size - 1) route[i + 1] else "(종점)"
                val remainingSeconds = stopAtStationEndTime - elapsedSecondsFromDeparture
                val remainingMin = maxOf(1L, (remainingSeconds / 60))

                return if (i < route.size - 1) {
                    // 다음 도착 시간 (다음 중간 지점 정차 시작 시간)
                    val nextSegmentStartTime = stopAtStationEndTime + segmentDuration // 다음 중간 지점 정차 시작 시간
                    val nextArrivalSeconds = nextSegmentStartTime - elapsedSecondsFromDeparture
                    val nextArrivalMin = maxOf(1L, (nextArrivalSeconds / 60))

                    "${currentStation} 정차 중 (${nextArrivalMin}분 후 ${nextStopName} 방면으로 출발)"

                } else {
                    // 종점 정차 중
                    "${currentStation} 정차 중 (종점)"
                }
            }
            accumulatedSegmentTime = stopAtStationEndTime

            // B. 중간 지점 정차 구간 (다음 정류장으로 이동)
            if (i < route.size - 1) {
                val nextStation = route[i + 1]

                val stopAtMidPointStartTime = accumulatedSegmentTime
                val stopAtMidPointEndTime = stopAtMidPointStartTime + segmentDuration

                if (elapsedSecondsFromDeparture < stopAtMidPointEndTime) {
                    // 현재 중간 지점에 정차 중인 경우 (이동 시간 0초 가정)
                    // 다음 도착 예정 시간 (다음 정류장 정차 시작 시간)
                    val nextSegmentStartTime = stopAtMidPointEndTime + segmentDuration
                    val nextArrivalSeconds = nextSegmentStartTime - elapsedSecondsFromDeparture
                    val nextArrivalMin = maxOf(1L, (nextArrivalSeconds / 60))

                    return "${currentStation} → ${nextStation} 중간 지점 정차 중 (${nextArrivalMin}분 후 ${nextStation} 도착)"
                }
                accumulatedSegmentTime = stopAtMidPointEndTime
            }
        }

        return "운행 중 (위치 계산 오류)"
    }


    // =================================================================
    // 기존 로직 (날씨, 시간표, 권한 등)
    // =================================================================

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    getCurrentLocationAndFetchWeather()
                } else {
                    Toast.makeText(this, "위치 정보 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
                return
            }
            else -> {}
        }
    }

    private fun getCurrentLocationAndFetchWeather(retryCount: Int = 0) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val latitude = location.latitude
                        val longitude = location.longitude
                        fetchWeather(latitude, longitude)
                    } else {
                        if (retryCount < 3) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                getCurrentLocationAndFetchWeather(retryCount + 1)
                            }, 2000)
                        } else {
                            Toast.makeText(this, "위치 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "위치 정보 요청 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun fetchWeather(latitude: Double, longitude: Double) {
        val apiKey = "4635b3f633d14de002f86a3f58605f61"

        val weatherApiService = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)

        weatherApiService.getWeather(latitude, longitude, apiKey)
            .enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    if (response.isSuccessful) {
                        val weatherResponse = response.body()
                        weatherResponse?.let {
                            val temperature = it.main.temp
                            val description = it.weather[0].description
                            updateWeatherUI(temperature, description)
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "날씨 정보를 가져오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun updateWeatherUI(temperature: Double, description: String) {
        val translatedDescription = translateWeatherDescription(description)
        plusScreen1TextView.text = "현재 온도: ${String.format("%.1f", temperature)}°C\n날씨: $translatedDescription"
        val weatherIconRes = getWeatherIcon(description)
        weatherImageView.setImageResource(weatherIconRes)
    }

    private fun translateWeatherDescription(english: String): String {
        return when (english.lowercase()) {
            "clear sky" -> "맑음"
            "few clouds" -> "구름 조금"
            "scattered clouds" -> "구름 많음"
            "broken clouds" -> "흐림"
            "overcast clouds" -> "흐림"
            "shower rain" -> "소나기"
            "moderate rain" -> "비"
            "drizzle" -> "가벼운 비"
            "light rain" -> "가벼운 비"
            "light intensity drizzle" -> "가벼운 비"
            "heavy intensity rain" -> "강한 비"
            "rain" -> "비"
            "thunderstorm" -> "천둥번개"
            "snow" -> "눈"
            "mist" -> "안개"
            else -> english
        }
    }

    private fun getWeatherIcon(description: String): Int {
        return when (description.lowercase()) {
            "clear sky" -> R.drawable.ic_sun
            "few clouds", "scattered clouds" -> R.drawable.ic_cloud_sun
            "broken clouds", "overcast clouds" -> R.drawable.ic_cloud
            "shower rain", "rain", "moderate rain", "light rain", "light intensity drizzle", "drizzle", "heavy intensity rain" -> R.drawable.ic_rain
            "thunderstorm" -> R.drawable.ic_thunder
            "snow" -> R.drawable.ic_snow
            "mist" -> R.drawable.ic_fog
            else -> R.drawable.ic_sun
        }
    }

    private fun loadTodayTimetable() {
        CoroutineScope(Dispatchers.Main).launch {
            val todayTimetable = fetchTodayTimetableFromMSSQL()
            updateTodayScheduleUI(todayTimetable)
        }
    }

    private suspend fun fetchTodayTimetableFromMSSQL(): List<Map<String, String?>> =
        withContext(Dispatchers.IO) {
            val resultList = mutableListOf<Map<String, String?>>()
            // MSSQLConnector는 외부 연결 정보에 의존합니다.
            val connection: Connection? = MSSQLConnector.getConnection()
            val sharedPref = getSharedPreferences("user_info", Context.MODE_PRIVATE)
            val userId = sharedPref.getString("userId", "")

            val currentDay = SimpleDateFormat("E", Locale.KOREA).format(Date())
            val dayOfWeek = when (currentDay) {
                "월" -> "월"
                "화" -> "화"
                "수" -> "수"
                "목" -> "목"
                "금" -> "금"
                "토" -> "토"
                "일" -> "일"
                else -> ""
            }

            if (dayOfWeek == "토" || dayOfWeek == "일" || userId.isNullOrEmpty()) {
                connection?.close()
                return@withContext resultList
            }

            try {
                if (connection != null) {
                    val query = """
                        SELECT
                            ci.class_name,
                            ci.class_schedule
                        FROM student_schedule AS ss
                        JOIN class_info AS ci
                          ON ss.class_code = ci.class_code
                         AND ss.class_section = ci.class_section
                        WHERE ss.student_id = ?
                          AND ci.class_schedule LIKE ?;
                    """.trimIndent()
                    val preparedStatement = connection.prepareStatement(query)
                    preparedStatement.setString(1, userId)
                    preparedStatement.setString(2, "%${dayOfWeek}(%")
                    val resultSet: ResultSet = preparedStatement.executeQuery()

                    val metaData = resultSet.metaData
                    val columnCount = metaData.columnCount

                    while (resultSet.next()) {
                        val rowData = mutableMapOf<String, String?>()
                        for (i in 1..columnCount) {
                            val columnName = metaData.getColumnName(i)
                            val columnValue = resultSet.getString(i)
                            rowData[columnName] = columnValue
                        }
                        resultList.add(rowData)
                    }
                    resultSet.close()
                    preparedStatement.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "시간표 정보를 가져오는 데 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                connection?.close()
            }
            resultList
        }

    private fun updateTodayScheduleUI(todayScheduleData: List<Map<String, String?>>) {
        val currentDateFormat = SimpleDateFormat("M월 d일 (E)", Locale.KOREA)
        val currentDateAndDay = currentDateFormat.format(Date())
        val stringBuilder = StringBuilder()
        stringBuilder.append("$currentDateAndDay\n")

        val classList = mutableListOf<Triple<Int, Int, String>>()

        val scheduleEntryPattern = Regex("([월화수목금])\\(((\\d+)(,\\d+)*)\\)")
        val currentDayChar = SimpleDateFormat("E", Locale.KOREA).format(Date()).first()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)

        if (todayScheduleData.isNotEmpty()) {
            for (result in todayScheduleData) {
                val className = result["class_name"] as? String ?: continue
                val classSchedule = result["class_schedule"] as? String ?: ""

                val scheduleEntries = scheduleEntryPattern.findAll(classSchedule)
                for (match in scheduleEntries) {
                    val day = match.groupValues[1]
                    val timeStr = match.groupValues[2]

                    if (day.first() == currentDayChar) {
                        val times = timeStr.split(",").mapNotNull { it.toIntOrNull() }.sorted()

                        if (times.isNotEmpty()) {
                            val startBlock = times.first()
                            val endBlock = times.last()
                            val startHour = 8 + startBlock
                            val endHour = 8 + endBlock + 1

                            val classEndTimeInMinutes = endHour * 60 + 0
                            val currentTimeInMinutes = currentHour * 60 + currentMinute

                            if (currentTimeInMinutes < classEndTimeInMinutes) {
                                val formattedTime = String.format(Locale.getDefault(), "%02d:00~%02d:00", startHour, endHour)
                                classList.add(Triple(startHour, endHour, "$formattedTime $className"))
                            }
                        }
                    }
                }
            }
        }

        classList.sortBy { it.first }

        if (classList.isNotEmpty()) {
            classList.forEach {
                stringBuilder.append("${it.third}\n")
            }
        } else {
            stringBuilder.append("현재 진행 중이거나 예정된 수업이 없습니다.")
        }

        todayScheduleTextView.text = stringBuilder.toString().trimEnd()
    }

    private fun loadProfileImageUri() {
        val sharedPref = getSharedPreferences(PROFILE_IMAGE_PREF, Context.MODE_PRIVATE)
        val savedUriString = sharedPref.getString(KEY_PROFILE_IMAGE_URI, null)

        if (savedUriString != null) {
            try {
                val uri = Uri.parse(savedUriString)
                profileImageView.setImageURI(uri)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading profile image URI: ${e.message}")
                profileImageView.setImageResource(R.drawable.default_profile)
                sharedPref.edit().remove(KEY_PROFILE_IMAGE_URI).apply()
            }
        } else {
            profileImageView.setImageResource(R.drawable.default_profile)
        }
    }
}

// Weather API 인터페이스와 데이터 클래스는 기존 코드 유지
interface WeatherApiService {
    @GET("data/2.5/weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") appid: String,
        @Query("units") units: String = "metric"
    ): Call<WeatherResponse>
}

data class WeatherResponse(
    val weather: List<Weather>,
    val main: Main
)

data class Weather(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

data class Main(
    val temp: Double,
    val feels_like: Double,
    val temp_min: Double,
    val temp_max: Double,
    val pressure: Int,
    val humidity: Int
)