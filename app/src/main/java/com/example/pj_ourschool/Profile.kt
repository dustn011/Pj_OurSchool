package com.example.pj_ourschool

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class Profile : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile) // 시간표 레이아웃 설정

        val leftArrow: ImageView = findViewById(R.id.left_arrow)

        leftArrow.setOnClickListener {
            finish() // 현재 액티비티 종료 (이전 화면으로 이동)git status
        }
    }
}