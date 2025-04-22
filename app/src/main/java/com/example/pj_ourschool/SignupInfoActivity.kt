package com.example.pj_ourschool

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SignupInfoActivity : AppCompatActivity() {

    private lateinit var editName: EditText
    private lateinit var editGrade: EditText
    private lateinit var editClass: EditText
    private lateinit var btnFinishSignup: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_info) // 다음 화면의 레이아웃 파일 연결

        editName = findViewById(R.id.editName)
        editGrade = findViewById(R.id.editGrade)
        editClass = findViewById(R.id.editClass)
        btnFinishSignup = findViewById(R.id.btnFinishSignup)

        btnFinishSignup.setOnClickListener {
            val name = editName.text.toString().trim()
            val grade = editGrade.text.toString().trim()
            val className = editClass.text.toString().trim()

            if (name.isNotEmpty() && grade.isNotEmpty() && className.isNotEmpty()) {
                // TODO: 회원가입 처리 로직 구현 (예: 데이터베이스에 저장)
                val signupSuccessMessage = "${name}님, ${grade}학년 ${className}반으로 가입되었습니다!"
                Toast.makeText(this, signupSuccessMessage, Toast.LENGTH_LONG).show()

                // 예시: 가입 완료 후 메인 액티비티로 이동
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish() // 현재 액티비티 종료
            } else {
                Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}