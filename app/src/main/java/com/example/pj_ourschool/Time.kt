package com.example.pj_ourschool

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class Time : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_table) // 시간표 레이아웃 설정

        val campusImageView: ImageView = findViewById(R.id.campus)
        val busImageView: ImageView = findViewById(R.id.bus)
        val chatImageView: ImageView = findViewById(R.id.chat)
        val profileImageView: ImageView = findViewById(R.id.Profile)
        val homeImageView: ImageView = findViewById(R.id.home)

        profileImageView.setOnClickListener {
            val intent = Intent(this, Profile::class.java)
            startActivity(intent)

        }

        homeImageView.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
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

        val leftArrow: ImageView = findViewById(R.id.left_arrow)

        leftArrow.setOnClickListener { finish() }
    }
}