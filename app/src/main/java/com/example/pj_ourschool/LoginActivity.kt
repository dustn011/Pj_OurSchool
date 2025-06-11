package com.example.pj_ourschool

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pj_ourschool.databinding.ActivityLoginBinding
import com.example.pj_ourschool.util.SignupActivity // SignupActivity 경로 확인
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.SQLException

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val USER_INFO_PREF = "user_info"

    // 다른 클래스에서 접근할 수 있도록 companion object 안에 정의합니다.
    // const val을 사용하여 컴파일 시점에 결정되는 상수로 만듭니다.
    companion object {
        const val EXTRA_LOGGED_IN_USER_ID = "loggedInUserId"
        const val KEY_USER_ID = "userId"
        const val KEY_USER_PASSWORD = "userPassword" // 보안상 위험, 실제 서비스에서는 사용 금지!
        const val KEY_AUTO_LOGIN = "autoLogin"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedPref = getSharedPreferences(USER_INFO_PREF, Context.MODE_PRIVATE)

        val savedUserId = sharedPref.getString(LoginActivity.KEY_USER_ID, "")
        binding.etId.setText(savedUserId)

        val isAutoLoginEnabled = sharedPref.getBoolean(LoginActivity.KEY_AUTO_LOGIN, false)
        binding.cbAutoLogin.isChecked = isAutoLoginEnabled

        binding.btnLogin.setOnClickListener {
            val userId = binding.etId.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (userId.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "학번과 비밀번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
            } else {
                attemptLogin(userId, password, false)
            }
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        // --- 비밀번호 찾기 클릭 이벤트 추가 ---
        binding.tvFindIdPw.setOnClickListener {
            startActivity(Intent(this, FindpwActivity::class.java)) // FindpwActivity로 이동
        }
        // --- 여기까지 추가 ---
    }

    private fun attemptLogin(userId: String, password: String, isAutoLogin: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val connection = MSSQLConnector.getConnection()
            var passwordCorrect = false
            var userExists = false

            try {
                if (connection != null) {
                    val checkUserQuery = "SELECT admin_id FROM admin_info WHERE admin_id = ?"
                    val checkUserStmt = connection.prepareStatement(checkUserQuery)
                    checkUserStmt.setString(1, userId)
                    val userResultSet = checkUserStmt.executeQuery()
                    userExists = userResultSet.next()
                    userResultSet.close()
                    checkUserStmt.close()

                    if (userExists) {
                        val query = "SELECT admin_pw FROM admin_info WHERE admin_id = ? AND admin_pw = ?"
                        val preparedStatement = connection.prepareStatement(query)
                        preparedStatement.setString(1, userId)
                        preparedStatement.setString(2, password)
                        val resultSet = preparedStatement.executeQuery()
                        passwordCorrect = resultSet.next()
                        resultSet.close()
                        preparedStatement.close()
                    }

                    connection.close()

                    withContext(Dispatchers.Main) {
                        Log.d("LoginActivity", "userId: $userId, password: [비밀번호 숨김], userExists: $userExists, passwordCorrect: $passwordCorrect, isAutoLogin: $isAutoLogin")
                        if (passwordCorrect) {
                            val sharedPref = getSharedPreferences(USER_INFO_PREF, Context.MODE_PRIVATE)
                            val editor = sharedPref.edit()
                            editor.putString(LoginActivity.KEY_USER_ID, userId)

                            val autoLoginChecked = binding.cbAutoLogin.isChecked
                            editor.putBoolean(LoginActivity.KEY_AUTO_LOGIN, autoLoginChecked)

                            if (autoLoginChecked) {
                                editor.putString(LoginActivity.KEY_USER_PASSWORD, password)
                            } else {
                                editor.remove(LoginActivity.KEY_USER_PASSWORD)
                            }
                            editor.apply()


                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            intent.putExtra(EXTRA_LOGGED_IN_USER_ID, userId)
                            startActivity(intent)
                            finish()
                        } else {
                            if (userExists) {
                                Toast.makeText(this@LoginActivity, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@LoginActivity, "존재하지 않는 회원 정보입니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginActivity, "데이터베이스 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: SQLException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "데이터베이스 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("LoginActivity", "Database Error during login", e)
                }
            }
        }
    }
}