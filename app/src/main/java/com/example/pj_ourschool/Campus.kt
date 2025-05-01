package com.example.pj_ourschool

//위치 코드

import android.Manifest
import android.R.attr.label
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import com.example.pj_ourschool.databinding.ActivityCampusBinding
import com.kakao.sdk.common.util.Utility
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import android.widget.Toast
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException


class Campus : AppCompatActivity() {

    private lateinit var bindingCampus: ActivityCampusBinding
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null
    private var labelLayer: LabelLayer? = null
    private var currentMarker: Label? = null
    private var currentLocationMarker: Label? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000
    // 자신의 카카오 지도api rest키 써야합니다-----------------------
    private val REST_API_KEY = "2cb1433a2459d08c0fb7c0351fddd03d"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindingCampus = ActivityCampusBinding.inflate(layoutInflater)
        setContentView(bindingCampus.root)

        val keyHash = Utility.getKeyHash(this)
        Log.d("Hash", keyHash)

        KakaoMapSdk.init(this, "8657f921e8595e3efa4a2e0663545bbe")

        mapView = bindingCampus.mapView
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val leftArrow: ImageView = findViewById(R.id.left_arrow)
        val timeImageView: ImageView = findViewById(R.id.time)
        val busImageView: ImageView = findViewById(R.id.bus)
        val chatImageView: ImageView = findViewById(R.id.chat)
        val profileImageView: ImageView = findViewById(R.id.Profile)
        val homeImageView: ImageView = findViewById(R.id.home)

        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {
                Log.d("KakaoMap", "onMapDestroy")
            }

            override fun onMapError(e: Exception?) {
                Log.e("KakaoMap", "onMapError", e)
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(map: KakaoMap) {
                kakaoMap = map
                labelLayer = map.labelManager?.layer

                // 위치 권한 확인 및 요청
                if (ContextCompat.checkSelfPermission(this@Campus, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    getCurrentLocation()
                } else {
                    ActivityCompat.requestPermissions(
                        this@Campus,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }

                kakaoMap?.setOnMapClickListener { _, position, _, _ ->
                    addMarker(position)
                }

                // 마커 클릭 이벤트
                kakaoMap?.setOnLabelClickListener { _, _, label ->
                    val lat = label.position.latitude
                    val lng = label.position.longitude

                    getAddressFromLatLng(lat, lng) { address ->
                        Toast.makeText(this@Campus, "주소: $address", Toast.LENGTH_SHORT).show()
                    }
                    false
                }
            }

        })

        profileImageView.setOnClickListener { startActivity(Intent(this, Profile::class.java)) }
        homeImageView.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        timeImageView.setOnClickListener { startActivity(Intent(this, Time::class.java)) }
        busImageView.setOnClickListener { startActivity(Intent(this, ShuttleBus::class.java)) }
        chatImageView.setOnClickListener { startActivity(Intent(this, Chat::class.java)) }
        leftArrow.setOnClickListener { finish() }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        val currentLatLng = LatLng.from(it.latitude, it.longitude)
                        addCurrentLocationMarker(currentLatLng)

                        // 줌 레벨을 17.0f로 설정하여 현재 위치로 카메라 이동
                        val cameraUpdate = CameraUpdateFactory.newCenterPosition(currentLatLng, 19)
                        kakaoMap?.moveCamera(cameraUpdate)
                    } ?: run {
                        Toast.makeText(this, "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "위치 정보 요청 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "위치 권한이 없습니다.", Toast.LENGTH_SHORT).show()
        }

    }


    private fun addCurrentLocationMarker(position: LatLng) {
        labelLayer?.let {
            currentLocationMarker?.remove()

            val markerStyle = LabelStyle.from(R.drawable.map_icon)
                .setAnchorPoint(0.5f, 1.0f)

            val labelOptions = LabelOptions.from(position)
                .setStyles(markerStyle)
                .setTag("currentLocation")

            currentLocationMarker = it.addLabel(labelOptions)
        }
    }

    private fun addMarker(position: LatLng) {
        labelLayer?.let {
            currentMarker?.remove()

            val markerStyle = LabelStyle.from(R.drawable.map_icon)
                .setAnchorPoint(0.5f, 1.0f)

            val labelOptions = LabelOptions.from(position)
                .setStyles(markerStyle)
                .setTag("customMarker")

            currentMarker = it.addLabel(labelOptions)
        }
    }

    // 역지오코딩
    private fun getAddressFromLatLng(lat: Double, lng: Double, callback: (String) -> Unit) {
        val client = OkHttpClient()
        val url = "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=$lng&y=$lat"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "KakaoAK $REST_API_KEY") // REST API 키 필요
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("KakaoAPI", "주소 요청 실패: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let {
                    val json = JSONObject(it)
                    val documents = json.getJSONArray("documents")
                    if (documents.length() > 0) {
                        val address = documents.getJSONObject(0)
                            .getJSONObject("address")
                            .getString("address_name")

                        runOnUiThread {
                            callback(address)
                        }
                    } else {
                        runOnUiThread {
                            callback("주소 정보 없음")
                        }
                    }
                }
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    getCurrentLocation()
                } else {
                    Toast.makeText(this, "위치 정보 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
                return
            }
            else -> {
                // 다른 권한 요청 결과는 무시
            }
        }
    }
}