package com.example.pj_ourschool

//위치 코드

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
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





class Campus : AppCompatActivity() {

    private lateinit var bindingCampus: ActivityCampusBinding
    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null

    // private lateinit var fusedLocationClient: FusedLocationProviderClient
    // private lateinit var locationCallback: LocationCallback
    // private val LOCATION_PERMISSION_REQUEST_CODE = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindingCampus = ActivityCampusBinding.inflate(layoutInflater) // ActivityMainBinding 초기화
        setContentView(bindingCampus.root)

        var keyHash = Utility.getKeyHash(this)
        Log.d("Hash", keyHash)

        // KakaoMapSDK 초기화 (앱 시작 시 1회만 호출)
        KakaoMapSdk.init(this, "8657f921e8595e3efa4a2e0663545bbe")

        mapView = bindingCampus .mapView


        // 위치 fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


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

                // 중심 좌표를 청주대학교 위치로 설정
                val center = LatLng.from(36.652236, 127.494621)
                val cameraUpdate = CameraUpdateFactory.newCenterPosition(center)
                map.moveCamera(cameraUpdate)


            }
        })

        profileImageView.setOnClickListener {
            val intent = Intent(this, Profile::class.java)
            startActivity(intent)

        }

        homeImageView.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)

        }

        timeImageView.setOnClickListener {
            // 시간표 화면으로 이동
            val intent = Intent(this, Time::class.java)
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

        leftArrow.setOnClickListener {
            finish() // 현재 액티비티 종료 (이전 화면으로 이동)
        }
    }

}
