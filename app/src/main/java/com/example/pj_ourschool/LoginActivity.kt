package com.example.pj_ourschool

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pj_ourschool.databinding.ActivityLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.SQLException

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding 설정
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 이전에 저장된 아이디가 있다면 EditText에 표시
        val sharedPref = getSharedPreferences("user_info", Context.MODE_PRIVATE)
        binding.etId.setText(sharedPref.getString("userId", ""))

        // 로그인 버튼 클릭 이벤트
        binding.btnLogin.setOnClickListener {
            val userId = binding.etId.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (userId.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "학번과 비밀번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
            } else {
                // 실제 로그인 로직 (데이터베이스 연동)
                CoroutineScope(Dispatchers.IO).launch {
                    val connection = MSSQLConnector.getConnection()
                    var passwordCorrect = false
                    var userExists = false

                    try {
                        if (connection != null) {
                            // 먼저 학번이 존재하는지 확인
                            val checkUserQuery = "SELECT admin_id FROM admin_info WHERE admin_id = ?"
                            val checkUserStmt = connection.prepareStatement(checkUserQuery)
                            checkUserStmt.setString(1, userId)
                            val userResultSet = checkUserStmt.executeQuery()
                            userExists = userResultSet.next()
                            userResultSet.close()
                            checkUserStmt.close()

                            if (userExists) {
                                // 학번이 존재하면 비밀번호 확인
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
                                Log.d("LoginActivity", "userId: $userId, password: $password, userExists: $userExists, passwordCorrect: $passwordCorrect")
                                if (passwordCorrect) {
                                    Toast.makeText(this@LoginActivity, "${userId}님, 환영합니다!", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
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
                            Log.e("LoginActivity", "Database Error", e)
                        }
                    }
                }

                // 자동 로그인 체크 여부 확인 (자동 로그인 로직은 필요에 따라 구현)
                val isAutoLogin = binding.cbAutoLogin.isChecked
                if (isAutoLogin) {
                    // SharedPreferences 등에 자동 로그인 정보 저장 로직 추가 (예: 로그인 상태 저장)
                    Toast.makeText(this, "자동 로그인 설정됨", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 회원가입 클릭
        binding.tvRegister.setOnClickListener {
            Toast.makeText(this, "회원가입 화면으로 이동합니다.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SignupInfoActivity::class.java))
        }

        // 아이디/비밀번호 찾기 클릭
        binding.tvFindIdPw.setOnClickListener {
            Toast.makeText(this, "아이디/비밀번호 찾기 화면으로 이동합니다.", Toast.LENGTH_SHORT).show()
            // startActivity(Intent(this, FindIdPwActivity::class.java))
            // FindIdPwActivity는 아직 구현되지 않은 것으로 보입니다.
            // 필요하다면 해당 Activity를 생성하고 Intent를 연결해야 합니다.
        }
    }
}