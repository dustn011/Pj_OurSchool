package com.example.pj_ourschool

import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ShuttleBus : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shuttlebus) // 시간표 레이아웃 설정

        val leftArrow: ImageView = findViewById(R.id.left_arrow)
        val timeImageView: ImageView = findViewById(R.id.time)
        val campusImageView: ImageView = findViewById(R.id.campus)
        val chatImageView: ImageView = findViewById(R.id.chat)
        val homeImageView: ImageView = findViewById(R.id.home)
        val profileImageView: ImageView = findViewById(R.id.Profile)
        val shuttleLinkButton: Button = findViewById(R.id.btn_shuttle_link)

        // 웹사이트 링크 열기
        shuttleLinkButton.setOnClickListener {
            val url = "https://www.cju.ac.kr/www/contents.do?key=4474"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

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

        chatImageView.setOnClickListener {
            // 채팅 화면으로 이동
            val intent = Intent(this, Chat::class.java)
            startActivity(intent)

        }


        leftArrow.setOnClickListener { finish() }
    }
}