package com.example.pj_ourschool

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class Profile : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile) // 내 정보 레이아웃 설정

        val leftArrow: ImageView = findViewById(R.id.left_arrow)
        val userIdTextView: TextView = findViewById(R.id.text_id) // 아이디를 표시할 TextView

        // SharedPreferences에서 아이디를 읽어옵니다.
        val sharedPref = getSharedPreferences("user_info", Context.MODE_PRIVATE)
        val userId = sharedPref.getString("userId", "user123") // 기본값 설정 가능

        // 아이디를 TextView에 설정합니다.
        userIdTextView.text = "아이디: $userId"

        leftArrow.setOnClickListener { finish() }
    }
}