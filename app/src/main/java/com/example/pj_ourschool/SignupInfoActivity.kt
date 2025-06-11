package com.example.pj_ourschool

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
// MSSQLConnector 클래스가 같은 패키지에 있거나 import 경로가 맞는지 확인해주세요.
import com.example.pj_ourschool.MSSQLConnector

class SignupInfoActivity : AppCompatActivity() {

    private lateinit var editSchoolId: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnFinishSignup: Button
    private lateinit var btnSchoolVerify: Button
    private lateinit var imgBack: ImageView

    private lateinit var cbAgreeAll: CheckBox
    private lateinit var cbServiceTerms: CheckBox
    private lateinit var cbPrivacyTerms: CheckBox
    private lateinit var cbMarketingTerms: CheckBox

    private lateinit var tvFindId: TextView
    private lateinit var tvFindPassword: TextView

    // 휴대폰 인증 관련 UI 요소
    private lateinit var editPhoneNumber: EditText
    private lateinit var btnSendCode: Button
    private lateinit var editVerificationCode: EditText
    private lateinit var btnVerify: Button

    private var isSchoolVerified = false // 학교 인증 성공 여부 추적 변수
    private var isCodeSent = false // 인증번호 발송 여부 추적 변수 (인증번호 입력 및 인증 버튼 활성화 조건)
    private var isPhoneVerified = false // 휴대폰 인증 성공 여부 추적 변수

    // 로그 태그 정의
    private val TAG = "SignupInfoActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_info)

        // View 바인딩
        editSchoolId = findViewById(R.id.editSchoolId)
        editPassword = findViewById(R.id.editPassword)
        btnFinishSignup = findViewById(R.id.btnFinishSignup)
        btnSchoolVerify = findViewById(R.id.btnSchoolVerify)
        imgBack = findViewById(R.id.imgBack)

        cbAgreeAll = findViewById(R.id.cbAgreeAll)
        cbServiceTerms = findViewById(R.id.cbServiceTerms)
        cbPrivacyTerms = findViewById(R.id.cbPrivacyTerms)
        cbMarketingTerms = findViewById(R.id.cbMarketingTerms)

        tvFindId = findViewById(R.id.tvFindId)
        tvFindPassword = findViewById(R.id.tvFindPassword)

        // 휴대폰 인증 관련 UI 요소 바인딩
        editPhoneNumber = findViewById(R.id.editPhoneNumber)
        btnSendCode = findViewById(R.id.btnSendCode)
        editVerificationCode = findViewById(R.id.editVerificationCode)
        btnVerify = findViewById(R.id.btnVerify)


        // 초기 버튼 상태 설정
        updateSchoolVerifyButton()
        updateSendCodeButton()
        updateVerifyButton()
        updateSignupButtonState()

        // 텍스트 입력 감지 (학교 ID/PW)
        val schoolAuthWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateSchoolVerifyButton()
                updateSignupButtonState()
                updateSendCodeButton() // 학교 ID/PW 변경 시 인증번호 발송 버튼 상태도 업데이트
                isSchoolVerified = false // ID/PW 변경 시 학교 인증 초기화
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        editSchoolId.addTextChangedListener(schoolAuthWatcher)
        editPassword.addTextChangedListener(schoolAuthWatcher)

        // 텍스트 입력 감지 (휴대폰 번호)
        val phoneNumWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateSendCodeButton() // 휴대폰 번호 입력 시 인증번호 발송 버튼 상태 업데이트
                isCodeSent = false // 번호 변경 시 인증번호 발송 여부 초기화
                isPhoneVerified = false // 번호 변경 시 휴대폰 인증 초기화
                updateVerifyButton()
                updateSignupButtonState()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        editPhoneNumber.addTextChangedListener(phoneNumWatcher)

        // 텍스트 입력 감지 (인증번호)
        val verificationCodeWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateVerifyButton() // 인증번호 입력 시 인증 버튼 상태 업데이트
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        editVerificationCode.addTextChangedListener(verificationCodeWatcher)

        // 뒤로 가기
        imgBack.setOnClickListener {
            finish()
        }

        // 포털 링크 이동
        val portalUrl = "https://portal.cju.ac.kr/" // 실제 학교 포털 URL로 변경 필요
        tvFindId.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(portalUrl)))
        }
        tvFindPassword.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(portalUrl)))
        }

        // 체크박스 동작
        val checkBoxes = listOf(cbAgreeAll, cbServiceTerms, cbPrivacyTerms, cbMarketingTerms)
        checkBoxes.forEach { checkBox ->
            checkBox.setOnClickListener {
                handleCheckboxLogic(checkBox)
            }
        }

        // 학교 인증 버튼 클릭
        btnSchoolVerify.setOnClickListener {
            val id = editSchoolId.text.toString().trim()
            val pw = editPassword.text.toString().trim()

            Log.d(TAG, "Attempting school verification for ID: $id")
            // 비밀번호는 보안상 로그에 직접 출력하지 않는 것이 좋습니다.
            Log.d(TAG, "Password: $pw") // 제거 권장, 디버깅 용도로만 잠시 사용

            CoroutineScope(Dispatchers.IO).launch {
                val connection = MSSQLConnector.getConnection()
                var verificationResult: String = ""

                if (connection != null) {
                    try {
                        // 1. students_info 테이블에서 학번/비밀번호 일치 여부 확인 (학교 인증)
                        val query = "SELECT student_school_id, student_school_Password FROM students_info WHERE student_school_id = ? AND student_school_Password = ?"
                        val preparedStatement = connection.prepareStatement(query)
                        preparedStatement.setString(1, id)
                        preparedStatement.setString(2, pw)

                        val resultSet = preparedStatement.executeQuery()

                        if (resultSet.next()) {
                            // 학교 인증 성공. 이제 admin_info에 이미 가입된 회원인지 확인
                            Log.d(TAG, "School ID and Password MATCHED in students_info.")

                            val checkAdminQuery = "SELECT admin_id FROM admin_info WHERE admin_id = ?"
                            val checkAdminStmt = connection.prepareStatement(checkAdminQuery)
                            checkAdminStmt.setString(1, id)
                            val adminResultSet = checkAdminStmt.executeQuery()

                            if (adminResultSet.next()) {
                                // 이미 admin_info에 존재 (이미 가입한 회원)
                                Log.d(TAG, "User ID: $id already registered in admin_info.")
                                verificationResult = "이미 가입한 회원입니다."
                                isSchoolVerified = false // 이미 가입되었으므로 학교 인증 상태를 false로 유지하여 회원가입 진행을 막음
                            } else {
                                // admin_info에 존재하지 않음 (학교 인증 완료 상태로 설정)
                                Log.d(TAG, "User ID: $id not yet registered in admin_info. School verification successful.")
                                verificationResult = "학교 인증이 완료되었습니다!"
                                isSchoolVerified = true // 학교 인증 성공
                            }
                            adminResultSet.close()
                            checkAdminStmt.close()
                        } else {
                            // 학번/비밀번호 불일치. 학번만 맞는지 확인하는 추가 쿼리 실행
                            Log.d(TAG, "School ID and Password did NOT match in students_info. Checking ID only.")
                            val checkIdOnlyQuery = "SELECT student_school_id FROM students_info WHERE student_school_id = ?"
                            val checkIdOnlyPreparedStatement = connection.prepareStatement(checkIdOnlyQuery)
                            checkIdOnlyPreparedStatement.setString(1, id)
                            val idOnlyResultSet = checkIdOnlyPreparedStatement.executeQuery()

                            if (idOnlyResultSet.next()) {
                                // 학번은 일치하지만 비밀번호가 틀림
                                Log.d(TAG, "School ID '$id' found, but password was incorrect.")
                                verificationResult = "비밀번호 오류입니다. 다시 한 번 입력해주세요."
                            } else {
                                // 학번도 일치하지 않음
                                Log.d(TAG, "School ID '$id' not found in students_info.")
                                verificationResult = "ID/PW 오류입니다. 다시 한 번 입력해주세요."
                            }
                            idOnlyResultSet.close()
                            checkIdOnlyPreparedStatement.close()

                            isSchoolVerified = false // 인증 실패
                        }

                        resultSet.close()
                        preparedStatement.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Log.e(TAG, "Database error during school verification: ${e.message}")
                        verificationResult = "데이터베이스 오류가 발생했습니다: ${e.message}"
                        isSchoolVerified = false
                    } finally {
                        try {
                            connection.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        showVerificationDialog(verificationResult)
                        updateSignupButtonState() // 인증 결과에 따라 회원가입 버튼 상태 업데이트
                        updateSendCodeButton() // 학교 인증 결과에 따라 인증번호 발송 버튼 상태 업데이트
                        // 학교 인증 실패 시 휴대폰 인증 관련 상태 초기화
                        if (!isSchoolVerified) {
                            isCodeSent = false
                            isPhoneVerified = false
                            updateVerifyButton()
                        }
                    }

                } else {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "Failed to connect to database for school verification.")
                        Toast.makeText(this@SignupInfoActivity, "데이터베이스 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        isSchoolVerified = false
                        updateSignupButtonState()
                        updateSendCodeButton()
                    }
                }
            }
        }

        // 인증번호 발송 버튼 클릭
        btnSendCode.setOnClickListener {
            if (!isSchoolVerified) {
                Toast.makeText(this, "학교 인증을 먼저 완료해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val schoolId = editSchoolId.text.toString().trim()
            val phoneNumber = editPhoneNumber.text.toString().trim()

            if (phoneNumber.isEmpty()) {
                Toast.makeText(this, "휴대폰 번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d(TAG, "Attempting to send verification code for ID: $schoolId, Phone: $phoneNumber")

            CoroutineScope(Dispatchers.IO).launch {
                val connection = MSSQLConnector.getConnection()
                var message = ""
                if (connection != null) {
                    try {
                        // DB에서 해당 학번의 전화번호 조회 (students_info 테이블)
                        val query = "SELECT phone_number FROM students_info WHERE student_school_id = ?"
                        val preparedStatement = connection.prepareStatement(query)
                        preparedStatement.setString(1, schoolId)
                        val resultSet = preparedStatement.executeQuery()

                        if (resultSet.next()) {
                            val dbPhoneNumber = resultSet.getString("phone_number")
                            if (dbPhoneNumber == phoneNumber) {
                                Log.d(TAG, "Phone number matched DB record. Sending code.")
                                // 전화번호가 일치하면 인증번호 발송 로직 (여기에 실제 인증번호 발송 코드 추가)
                                // TODO: generateAndSendVerificationCode(phoneNumber) 실제 인증번호 발송 로직 구현
                                message = "인증번호가 발송되었습니다."
                                isCodeSent = true // 인증번호 발송 성공
                            } else {
                                Log.d(TAG, "Input phone number '$phoneNumber' does not match DB record '$dbPhoneNumber'.")
                                message = "입력하신 휴대폰 번호가 학교 포털에 등록된 번호와 다릅니다."
                                isCodeSent = false
                            }
                        } else {
                            Log.d(TAG, "School ID '$schoolId' not found when checking phone number. (Should not happen if school verified)")
                            message = "해당 학번에 등록된 정보가 없습니다."
                            isCodeSent = false
                        }
                        resultSet.close()
                        preparedStatement.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Log.e(TAG, "Error sending verification code: ${e.message}")
                        message = "인증번호 발송 중 오류가 발생했습니다: ${e.message}"
                        isCodeSent = false
                    } finally {
                        try {
                            connection.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to connect to database for sending verification code.")
                    message = "데이터베이스 연결에 실패했습니다."
                    isCodeSent = false
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SignupInfoActivity, message, Toast.LENGTH_SHORT).show()
                    updateVerifyButton()
                    updateSignupButtonState()
                }
            }
        }

        // 인증 버튼 클릭 (휴대폰 인증)
        btnVerify.setOnClickListener {
            if (!isCodeSent) {
                Toast.makeText(this, "인증번호 발송을 먼저 해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val verificationCode = editVerificationCode.text.toString().trim()

            if (verificationCode.isEmpty()) {
                Toast.makeText(this, "인증번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // TODO: 여기에 실제 인증번호 확인 로직 구현

            // 예시: 서버로 인증번호를 보내서 확인하거나, 앱 내부에서 생성된 번호와 비교
            val correctCode = "123456" // 예시: 실제로는 발송된 번호와 비교해야 함
            Log.d(TAG, "Verifying code. Input: $verificationCode, Expected (mock): $correctCode")
            if (verificationCode == correctCode) {
                isPhoneVerified = true
                Toast.makeText(this, "휴대폰 인증이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Phone verification successful.")
            } else {
                isPhoneVerified = false
                Toast.makeText(this, "인증번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Phone verification failed: incorrect code.")
            }
            updateSignupButtonState()
        }


        // 회원가입 완료 버튼 클릭
        btnFinishSignup.setOnClickListener {
            if (!isSchoolVerified) {
                Toast.makeText(this, "학교 인증을 먼저 진행해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isPhoneVerified) {
                Toast.makeText(this, "휴대폰 인증을 먼저 진행해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!cbServiceTerms.isChecked || !cbPrivacyTerms.isChecked) {
                Toast.makeText(this, "필수 약관에 동의해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            val SchoolId = editSchoolId.text.toString().trim()
            val Password = editPassword.text.toString().trim()

            if (SchoolId.isNotEmpty() && Password.isNotEmpty()) {
                // 최종 회원가입 시점에 admin_info에 삽입
                CoroutineScope(Dispatchers.IO).launch {
                    val connection = MSSQLConnector.getConnection()
                    if (connection != null) {
                        try {
                            // admin_id와 admin_pw만 삽입
                            val insertQuery = "INSERT INTO admin_info (admin_id, admin_pw) VALUES (?, ?)"
                            val insertStmt = connection.prepareStatement(insertQuery)
                            insertStmt.setString(1, SchoolId)
                            insertStmt.setString(2, Password)
                            insertStmt.executeUpdate()
                            Log.d(TAG, "User '$SchoolId' successfully inserted into admin_info.")

                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SignupInfoActivity, "회원가입이 완료되었습니다. 로그인해주세요!", Toast.LENGTH_LONG).show()
                                // SharedPreferences에 아이디 저장 (로그인 화면에서 자동 완성을 위해)
                                val sharedPref = getSharedPreferences("user_info", Context.MODE_PRIVATE)
                                with(sharedPref.edit()) {
                                    putString("userId", SchoolId)
                                    apply()
                                }

                                // 로그인 화면으로 이동
                                startActivity(Intent(this@SignupInfoActivity, LoginActivity::class.java))
                                finish()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Log.e(TAG, "Database error during final signup: ${e.message}")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SignupInfoActivity, "회원가입 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } finally {
                            try {
                                connection.close()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SignupInfoActivity, "데이터베이스 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Signup attempt failed: School ID or Password is empty.")
            }
        }
    }

    // 체크박스 처리 로직
    private fun handleCheckboxLogic(clicked: CheckBox) {
        if (clicked.id == R.id.cbAgreeAll) {
            val checked = cbAgreeAll.isChecked
            cbServiceTerms.isChecked = checked
            cbPrivacyTerms.isChecked = checked
            cbMarketingTerms.isChecked = checked
            Log.d(TAG, "Agree All checkbox changed to: $checked")
        } else {
            cbAgreeAll.isChecked = cbServiceTerms.isChecked &&
                    cbPrivacyTerms.isChecked &&
                    cbMarketingTerms.isChecked
            Log.d(TAG, "Individual checkbox changed. Agree All state: ${cbAgreeAll.isChecked}")
        }
        updateSignupButtonState()
    }

    // 회원가입 버튼 제어 (필수 체크박스, 학교 인증, 휴대폰 인증 기준)
    private fun updateSignupButtonState() {
        val requiredChecked = cbServiceTerms.isChecked && cbPrivacyTerms.isChecked
        btnFinishSignup.isEnabled = requiredChecked && isSchoolVerified && isPhoneVerified
        val color = if (requiredChecked && isSchoolVerified && isPhoneVerified) "#0047AB" else "#CCCCCC"
        btnFinishSignup.setBackgroundColor(Color.parseColor(color))
        Log.d(TAG, "Signup button enabled state: ${btnFinishSignup.isEnabled}")
    }

    // 학교인증 버튼 제어 (ID/PW 입력 기준)
    private fun updateSchoolVerifyButton() {
        val idFilled = editSchoolId.text.toString().isNotEmpty()
        val pwFilled = editPassword.text.toString().isNotEmpty()
        val enabled = idFilled && pwFilled
        btnSchoolVerify.isEnabled = enabled
        val color = if (enabled) "#0047AB" else "#CCCCCC"
        btnSchoolVerify.setBackgroundColor(Color.parseColor(color))
        Log.d(TAG, "School verify button enabled state: $enabled")
    }

    // 인증번호 발송 버튼 제어 (학교 인증 완료 & 휴대폰 번호 입력 기준)
    private fun updateSendCodeButton() {
        val phoneNumFilled = editPhoneNumber.text.toString().isNotEmpty()
        val enabled = isSchoolVerified && phoneNumFilled
        btnSendCode.isEnabled = enabled
        val color = if (enabled) "#0047AB" else "#CCCCCC"
        btnSendCode.setBackgroundColor(Color.parseColor(color))
        Log.d(TAG, "Send Code button enabled state: $enabled")
    }

    // 인증 버튼 제어 (인증번호 발송 완료 & 인증번호 입력 기준)
    private fun updateVerifyButton() {
        val verificationCodeFilled = editVerificationCode.text.toString().isNotEmpty()
        val enabled = isCodeSent && verificationCodeFilled
        btnVerify.isEnabled = enabled
        val color = if (enabled) "#0047AB" else "#CCCCCC"
        btnVerify.setBackgroundColor(Color.parseColor(color))
        Log.d(TAG, "Verify button enabled state: $enabled")
    }

    // 인증 결과 팝업
    private fun showVerificationDialog(message: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_verifydialog, null)
        val dialogBuilder = AlertDialog.Builder(this).setView(dialogView)
        val alertDialog = dialogBuilder.create()

        val txtMessage = dialogView.findViewById<TextView>(R.id.txtDialogMessage)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        txtMessage.text = message
        Log.d(TAG, "Showing verification dialog with message: '$message'")

        btnConfirm.setOnClickListener {
            alertDialog.dismiss()
            Log.d(TAG, "Verification dialog dismissed.")
        }

        alertDialog.show()
    }
}