package com.example.pj_ourschool

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.hdodenhof.circleimageview.CircleImageView // CircleImageView 임포트 확인
import android.content.Context // Context 임포트 추가
import android.net.Uri // Uri 임포트 추가
import android.util.Log // Log 임포트 추가

class Chat : AppCompatActivity() {

    // 프로필 이미지 관련 상수 추가
    private val PROFILE_IMAGE_PREF = "profile_image_pref"
    private val KEY_PROFILE_IMAGE_URI = "profile_image_uri"

    // profileImageView 타입을 ImageView에서 CircleImageView로 변경
    private lateinit var profileImageView: CircleImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val leftArrow: ImageView = findViewById(R.id.left_arrow)
        val timeImageView: ImageView = findViewById(R.id.time)
        val campusImageView: ImageView = findViewById(R.id.campus)
        val busImageView: ImageView = findViewById(R.id.bus)
        profileImageView = findViewById(R.id.Profile) // CircleImageView 타입으로 초기화
        val homeImageView: ImageView = findViewById(R.id.home)

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

        leftArrow.setOnClickListener { finish() }

        // **프로필 이미지 로드**
        loadProfileImageUri()
    }

    override fun onResume() {
        super.onResume()
        // 액티비티가 재개될 때 프로필 이미지를 다시 로드하여 최신 상태 유지
        loadProfileImageUri()
    }

    // --- 프로필 이미지 로드 함수 추가 ---
    private fun loadProfileImageUri() {
        val sharedPref = getSharedPreferences(PROFILE_IMAGE_PREF, Context.MODE_PRIVATE)
        val savedUriString = sharedPref.getString(KEY_PROFILE_IMAGE_URI, null)

        if (savedUriString != null) {
            try {
                val uri = Uri.parse(savedUriString)
                profileImageView.setImageURI(uri)
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error loading profile image URI: ${e.message}")
                profileImageView.setImageResource(R.drawable.default_profile)
                // 오류 발생 시 저장된 URI 제거하여 다시 로드 시도하지 않도록
                sharedPref.edit().remove(KEY_PROFILE_IMAGE_URI).apply()
            }
        } else {
            profileImageView.setImageResource(R.drawable.default_profile)
        }
    }
}