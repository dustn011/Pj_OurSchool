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
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var timeImageView: ImageView
    private lateinit var campusImageView: ImageView
    private lateinit var busImageView: ImageView
    private lateinit var chatImageView: ImageView
    private lateinit var profileImageView: CircleImageView
    private lateinit var plusScreen1: LinearLayout
    private lateinit var plusScreen2: LinearLayout
    private lateinit var plusScreen3: CardView
    private lateinit var plusScreen1TextView: TextView
    private lateinit var weatherImageView: ImageView
    private lateinit var todayScheduleTextView: TextView

    private val PROFILE_IMAGE_PREF = "profile_image_pref"
    private val KEY_PROFILE_IMAGE_URI = "profile_image_uri"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        loadProfileImageUri()

        profileImageView.setOnClickListener {
            val intent = Intent(this, Profile::class.java)
            startActivity(intent)
        }
        timeImageView.setOnClickListener {
            val intent = Intent(this, Time::class.java)
            startActivity(intent)
        }
        campusImageView.setOnClickListener {
            val intent = Intent(this, Campus::class.java)
            startActivity(intent)
        }
        busImageView.setOnClickListener {
            val intent = Intent(this, ShuttleBus::class.java)
            startActivity(intent)
        }
        chatImageView.setOnClickListener {
            val intent = Intent(this, Chat::class.java)
            startActivity(intent)
        }

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

    override fun onResume() {
        super.onResume()
        loadProfileImageUri()
    }

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
            val connection = MSSQLConnector.getConnection()
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
        // 날짜와 요일 표시
        val currentDateFormat = SimpleDateFormat("M월 d일 (E)", Locale.KOREA)
        val currentDateAndDay = currentDateFormat.format(Date())
        val stringBuilder = StringBuilder()
        stringBuilder.append("$currentDateAndDay\n")

        Log.d("ScheduleDebug", "Date and Day added to StringBuilder: '$currentDateAndDay'")

        val classList = mutableListOf<Triple<Int, Int, String>>() // Triple로 시작 시간, 끝 시간, 수업 정보 저장

        val scheduleEntryPattern = Regex("([월화수목금])\\(((\\d+)(,\\d+)*)\\)")
        val currentDayChar = SimpleDateFormat("E", Locale.KOREA).format(Date()).first()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val currentMinute = Calendar.getInstance().get(Calendar.MINUTE)

        if (todayScheduleData.isNotEmpty()) {
            Log.d("ScheduleDebug", "todayScheduleData is NOT empty. Attempting to parse schedule.")
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
                            val endHour = 8 + endBlock + 1 // 수업 블록이 1시간이라고 가정

                            // 현재 시간과 비교하여 이미 지난 수업은 제외
                            // 수업 종료 시간 (분은 00분으로 가정)
                            val classEndTimeInMinutes = endHour * 60 + 0
                            val currentTimeInMinutes = currentHour * 60 + currentMinute

                            if (currentTimeInMinutes < classEndTimeInMinutes) {
                                val formattedTime = String.format(Locale.getDefault(), "%02d:00~%02d:00", startHour, endHour)
                                classList.add(Triple(startHour, endHour, "$formattedTime $className"))
                                Log.d("ScheduleDebug", "Added class: $className at $formattedTime (Day: $day)")
                            } else {
                                Log.d("ScheduleDebug", "Skipped passed class: $className (Ended at $endHour:00, Current time: $currentHour:$currentMinute)")
                            }
                        }
                    }
                }
            }
        }

        classList.sortBy { it.first } // 시작 시간 기준으로 정렬

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