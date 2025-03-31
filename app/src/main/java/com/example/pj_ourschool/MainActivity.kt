package com.example.pj_ourschool

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val timeImageView: ImageView = findViewById(R.id.time)
        val campusImageView: ImageView = findViewById(R.id.campus)
        val busImageView: ImageView = findViewById(R.id.bus)
        val chatImageView: ImageView = findViewById(R.id.chat)
        val plusScreen1: CardView = findViewById(R.id.plusScreen1)
        val plusScreen2: CardView = findViewById(R.id.plusScreen2)
        val plusScreen3: CardView = findViewById(R.id.plusScreen3)

        timeImageView.setOnClickListener {
            // 시간표 화면으로 이동
            val intent = Intent(this, Time::class.java)
            startActivity(intent)
            Toast.makeText(this, "시간표 클릭", Toast.LENGTH_SHORT).show()
        }
        campusImageView.setOnClickListener {
            // 캠퍼스맵 화면으로 이동
            val intent = Intent(this, Campus::class.java)
            startActivity(intent)
            Toast.makeText(this, "캠퍼스맵 클릭", Toast.LENGTH_SHORT).show()
        }

        busImageView.setOnClickListener {
            // 셔틀버스 화면으로 이동
            val intent = Intent(this, ShuttleBus::class.java)
            startActivity(intent)
            Toast.makeText(this, "셔틀버스 클릭", Toast.LENGTH_SHORT).show()
        }

        chatImageView.setOnClickListener {
            // 채팅 화면으로 이동
            val intent = Intent(this, Chat::class.java)
            startActivity(intent)
            Toast.makeText(this, "채팅 클릭", Toast.LENGTH_SHORT).show()
        }
    }
}