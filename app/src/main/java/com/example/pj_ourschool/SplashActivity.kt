package com.example.pj_ourschool

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper // Looper 임포트 추가
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.SQLException

class SplashActivity : AppCompatActivity() {

    private val USER_INFO_PREF = "user_info"
    private val KEY_USER_ID = "userId"
    private val KEY_USER_PASSWORD = "userPassword" // 보안상 위험, 실제 서비스에서는 사용 금지!
    private val KEY_AUTO_LOGIN = "autoLogin"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 핸들러 생성 시 Looper.getMainLooper()를 명시적으로 전달하여 경고 제거
        Handler(Looper.getMainLooper()).postDelayed({
            checkAutoLogin() // 자동 로그인 체크 함수 호출
        }, SPLASH_DISPLAY_TIME.toLong())
    }

    private fun checkAutoLogin() {
        val sharedPref = getSharedPreferences(USER_INFO_PREF, Context.MODE_PRIVATE)
        val isAutoLoginEnabled = sharedPref.getBoolean(KEY_AUTO_LOGIN, false)
        val savedUserId = sharedPref.getString(KEY_USER_ID, "")
        val savedPassword = sharedPref.getString(KEY_USER_PASSWORD, "") // 보안상 위험!

        Log.d("SplashActivity", "AutoLogin: $isAutoLoginEnabled, User ID: $savedUserId, Password (hashed/masked): [비밀번호 숨김]")

        if (isAutoLoginEnabled && !savedUserId.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
            // 자동 로그인이 활성화되어 있고, 저장된 정보가 있다면 DB 검증 시도
            CoroutineScope(Dispatchers.IO).launch {
                val connection = MSSQLConnector.getConnection()
                var loginSuccess = false

                try {
                    if (connection != null) {
                        val query = "SELECT admin_pw FROM admin_info WHERE admin_id = ? AND admin_pw = ?"
                        val preparedStatement = connection.prepareStatement(query)
                        preparedStatement.setString(1, savedUserId)
                        preparedStatement.setString(2, savedPassword) // 보안상 매우 위험
                        val resultSet = preparedStatement.executeQuery()
                        loginSuccess = resultSet.next() // 결과가 있으면 로그인 성공
                        resultSet.close()
                        preparedStatement.close()
                        connection.close()
                    } else {
                        Log.e("SplashActivity", "데이터베이스 연결 실패 (자동 로그인)")
                    }
                } catch (e: SQLException) {
                    Log.e("SplashActivity", "SQL Exception during auto-login: ${e.message}", e)
                } finally {
                    try { connection?.close() } catch (e: Exception) { Log.e("SplashActivity", "Connection close error: ${e.message}") }
                }

                withContext(Dispatchers.Main) {
                    if (loginSuccess) {
                        Log.d("SplashActivity", "자동 로그인 성공. MainActivity로 이동.")
                        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    } else {
                        Log.d("SplashActivity", "자동 로그인 실패. LoginActivity로 이동.")
                        // 자동 로그인 실패 시, 자동 로그인 설정 해제 및 저장된 비밀번호 삭제
                        sharedPref.edit()
                            .putBoolean(KEY_AUTO_LOGIN, false)
                            .remove(KEY_USER_PASSWORD)
                            .apply()
                        startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                    }
                    finish() // 스플래시 액티비티 종료
                }
            }
        } else {
            // 자동 로그인 설정이 없거나 정보가 불완전하면 바로 로그인 액티비티로 이동
            Log.d("SplashActivity", "자동 로그인 조건 불충족. LoginActivity로 이동.")
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            finish() // 스플래시 액티비티 종료
        }
    }

    companion object {
        private const val SPLASH_DISPLAY_TIME = 2000 // 2초
    }
}