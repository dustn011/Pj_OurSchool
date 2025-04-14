package com.example.pj_ourschool

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class Campus : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_campus) // 시간표 레이아웃 설정

        val leftArrow: ImageView = findViewById(R.id.left_arrow)
        val timeImageView: ImageView = findViewById(R.id.time)
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