package com.example.pj_ourschool

import android.content.Context
import android.content.Intent
import android.graphics.Paint
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
        val logoutTextView: TextView = findViewById(R.id.text_logout) // 로그아웃 TextView 추가

        // val changePasswordTextView: TextView = findViewById(R.id.text_change_password) // 비밀번호 변경 TextView 추가

        // SharedPreferences에서 아이디를 읽어옵니다.
        val sharedPref = getSharedPreferences("user_info", Context.MODE_PRIVATE)
        val userId = sharedPref.getString("userId", "user123") // 기본값 설정 가능

        // 아이디를 TextView에 설정합니다.
        userIdTextView.text = "아이디: $userId"

        logoutTextView.paintFlags = logoutTextView.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        leftArrow.setOnClickListener { finish() }

        // 로그아웃 텍스트뷰 클릭 리스너 설정
        logoutTextView.setOnClickListener {
            // SharedPreferences 에 저장된 사용자 정보 삭제 (아이디, 자동 로그인 상태 등)
            val editor = sharedPref.edit()
            editor.remove("userId") // 아이디 정보 삭제
            // 필요한 경우 자동 로그인 상태 관련 키도 삭제
            editor.apply() // 또는 editor.commit()

            // 로그인 화면으로 이동
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // 현재 액티비티 종료 (뒤로 가기 방지)
        }
    }
}