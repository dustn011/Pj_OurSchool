package com.example.pj_ourschool

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
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
import java.sql.DriverManager
import com.example.pj_ourschool.MSSQLConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var timeImageView: ImageView
    private lateinit var campusImageView: ImageView
    private lateinit var busImageView: ImageView
    private lateinit var chatImageView: ImageView
    private lateinit var profileImageView: ImageView
    private lateinit var plusScreen1: LinearLayout
    private lateinit var plusScreen2: CardView
    private lateinit var plusScreen3: CardView
    private lateinit var plusScreen1TextView: TextView // 추가
    private lateinit var weatherImageView: ImageView // 수정됨 (아이콘 추가)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        timeImageView = findViewById(R.id.time)
        campusImageView = findViewById(R.id.campus)
        busImageView = findViewById(R.id.bus)
        chatImageView = findViewById(R.id.chat)
        profileImageView = findViewById(R.id.Profile)
        plusScreen1 = findViewById(R.id.plusScreen1) // LinearLayout이 아니라 CardView 타입으로 선언했다면 맞춰줘야 함
        plusScreen2 = findViewById(R.id.plusScreen2)
        plusScreen3 = findViewById(R.id.plusScreen3)

        weatherImageView = findViewById(R.id.weatherImageView) // 수정됨 (아이콘 연결)
        plusScreen1TextView = findViewById(R.id.plusScreen1TextView)






        profileImageView.setOnClickListener {
            val intent = Intent(this, Profile::class.java)
            startActivity(intent)

        }

        timeImageView.setOnClickListener {
            // 시간표 화면으로 이동
            val intent = Intent(this, Time::class.java)
            startActivity(intent)

        }
        campusImageView.setOnClickListener {
            // 캠퍼스맵 화면으로 이동
            val intent = Intent(this, Campus::class.java)
            startActivity(intent)

        }

        busImageView.setOnClickListener {
            // 셔틀버스 화면으로 이동
            val intent = Intent(this, ShuttleBus::class.java)
            startActivity(intent)

        }

        chatImageView.setOnClickListener {
            // 채팅 화면으로 이동
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
            // Permission already granted
            getCurrentLocationAndFetchWeather()
        }
        // MainActivity 시작 시 데이터베이스에서 정보 가져오기 (예시)


    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission granted
                    getCurrentLocationAndFetchWeather()
                } else {
                    // Permission denied
                    Toast.makeText(this, "위치 정보 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
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

                        // Fetch weather data
                        fetchWeather(latitude, longitude)
                    } else {
                        if (retryCount < 3) {
                            // 조금 있다가 다시 시도 (재귀 호출)
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
        val apiKey = "4635b3f633d14de002f86a3f58605f61" // Replace with your API key

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

                            // Update UI
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
        plusScreen1TextView.text = "현재 온도: $temperature°C\n날씨: $translatedDescription"

        val weatherIconRes = getWeatherIcon(description) // 수정됨 (아이콘 가져오기 추가)
        weatherImageView.setImageResource(weatherIconRes) // 수정됨 (아이콘 설정)
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
            else -> english // 번역 없으면 그대로 표시
        }
    }
    private fun getWeatherIcon(description: String): Int {
        return when (description.lowercase()) {
            "clear sky" -> R.drawable.ic_sun  // 맑음
            "few clouds", "scattered clouds" -> R.drawable.ic_cloud_sun  // 구름 조금
            "broken clouds", "overcast clouds" -> R.drawable.ic_cloud  // 흐림
            "shower rain", "rain", "moderate rain", "light rain", "light intensity drizzle", "drizzle", "heavy intensity rain" -> R.drawable.ic_rain  // 비
            "thunderstorm" -> R.drawable.ic_thunder  // 천둥번개
            "snow" -> R.drawable.ic_snow  // 눈
            "mist" -> R.drawable.ic_fog  // 안개
            else -> R.drawable.ic_sun  // 기본 아이콘
        }
    }
}

// Weather API interfaces and data classes
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