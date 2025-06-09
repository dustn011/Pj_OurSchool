package com.example.pj_ourschool

import android.os.Bundle
import android.util.Log // 로그를 위해 추가
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.ResultSet // ResultSet 추가
import java.sql.SQLException // SQLException 추가

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var currentPasswordField: EditText
    private lateinit var newPasswordField: EditText
    private lateinit var confirmPasswordField: EditText
    private lateinit var changePasswordButton: TextView

    private var currentUserId: String? = null

    // LoginActivity에 정의된 EXTRA_LOGGED_IN_USER_ID를 재사용
    companion object {
        const val EXTRA_USER_ID = LoginActivity.EXTRA_LOGGED_IN_USER_ID
        private const val TAG = "ChangePasswordActivity" // 로그 태그
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password) // XML 파일 이름 확인

        currentUserId = intent.getStringExtra(EXTRA_USER_ID)

        if (currentUserId.isNullOrEmpty()) {
            Toast.makeText(this, "사용자 정보를 가져올 수 없습니다. 다시 로그인해주세요.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        currentPasswordField = findViewById(R.id.current_password)
        newPasswordField = findViewById(R.id.new_password)
        confirmPasswordField = findViewById(R.id.confirm_password)
        changePasswordButton = findViewById(R.id.btn_change_password)

        changePasswordButton.setOnClickListener {
            handleChangePassword()
        }

        findViewById<ImageView>(R.id.back_arrow)?.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun handleChangePassword() {
        val currentPassword = currentPasswordField.text.toString()
        val newPassword = newPasswordField.text.toString()
        val confirmPassword = confirmPasswordField.text.toString()

        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "모든 비밀번호 필드를 채워주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword != confirmPassword) {
            Toast.makeText(this, "새 비밀번호와 확인 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword.length < 4) { // 최소 4자리 이상
            Toast.makeText(this, "새 비밀번호는 최소 4자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val userIdToUse = currentUserId ?: run {
            Toast.makeText(this, "사용자 정보 오류. 다시 로그인해주세요.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // DB 작업 (현재 비밀번호 확인 및 새 비밀번호 업데이트)
        lifecycleScope.launch(Dispatchers.IO) { // IO 디스패처에서 DB 작업 수행
            var connection: java.sql.Connection? = null
            var resultSet: ResultSet? = null
            var success = false
            var errorMessage: String? = null
            var currentPasswordMatches = false
            var userFound = false

            try {
                connection = MSSQLConnector.getConnection() // MSSQLConnector는 DB 연결을 담당하는 클래스
                if (connection == null) {
                    errorMessage = "데이터베이스 연결에 실패했습니다."
                } else {
                    // 1. 현재 비밀번호 확인
                    val checkPasswordQuery = "SELECT admin_pw FROM admin_info WHERE admin_id = ?"
                    val checkPasswordStmt = connection.prepareStatement(checkPasswordQuery)
                    checkPasswordStmt.setString(1, userIdToUse)
                    resultSet = checkPasswordStmt.executeQuery()

                    if (resultSet.next()) {
                        userFound = true
                        val storedPassword = resultSet.getString("admin_pw")
                        // TODO: 실제 앱에서는 해싱된 비밀번호를 비교해야 합니다.
                        if (currentPassword == storedPassword) {
                            currentPasswordMatches = true
                        }
                    } else {
                        userFound = false // 사용자 ID를 찾을 수 없음
                    }

                    resultSet?.close()
                    checkPasswordStmt.close()

                    if (!userFound) {
                        errorMessage = "사용자 정보를 찾을 수 없습니다."
                    } else if (!currentPasswordMatches) {
                        errorMessage = "현재 비밀번호가 일치하지 않습니다."
                    } else {
                        // 2. 비밀번호 업데이트
                        // TODO: 실제 앱에서는 새 비밀번호를 해싱하여 저장해야 합니다.
                        val updatePasswordQuery = "UPDATE admin_info SET admin_pw = ? WHERE admin_id = ?"
                        val updatePasswordStmt = connection.prepareStatement(updatePasswordQuery)
                        updatePasswordStmt.setString(1, newPassword) // 해싱된 비밀번호 사용 시: updatePasswordStmt.setString(1, hashedPassword)
                        updatePasswordStmt.setString(2, userIdToUse)

                        val rowsAffected = updatePasswordStmt.executeUpdate()
                        success = rowsAffected > 0
                        if (!success) {
                            errorMessage = "비밀번호 변경에 실패했습니다. (DB 업데이트 실패)"
                        }
                        updatePasswordStmt.close()
                    }
                }
            } catch (e: SQLException) {
                Log.e(TAG, "SQL Exception during password change: ${e.message}", e)
                errorMessage = "데이터베이스 오류가 발생했습니다: ${e.message}"
            } catch (e: Exception) {
                Log.e(TAG, "General Exception during password change: ${e.message}", e)
                errorMessage = "알 수 없는 오류가 발생했습니다: ${e.message}"
            } finally {
                try { resultSet?.close() } catch (e: SQLException) { Log.e(TAG, "Error closing resultSet: ${e.message}") }
                try { connection?.close() } catch (e: SQLException) { Log.e(TAG, "Error closing connection: ${e.message}") }
            }

            // UI 업데이트는 Main 스레드에서
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@ChangePasswordActivity, "비밀번호가 성공적으로 변경되었습니다.", Toast.LENGTH_LONG).show()
                    finish() // 변경 성공 시 액티비티 종료
                } else {
                    Toast.makeText(this@ChangePasswordActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}