package com.example.pj_ourschool // 당신의 패키지 이름으로 변경해주세요

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pj_ourschool.databinding.ActivityFindpwBinding // activity_findpw.xml에 맞는 바인딩 클래스
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class FindpwActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFindpwBinding // View Binding 사용을 위한 선언
    private lateinit var auth: FirebaseAuth
    private lateinit var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks

    private var verificationId: String? = null // Firebase가 보내는 인증 ID
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null // 재전송 토큰
    private var timer: CountDownTimer? = null // 인증번호 재전송 타이머
    private val TAG = "FindpwActivity" // 로그 태그

    private var isStudentIdAndPhoneMatchedInDb = false // 학번+전화번호가 DB에 일치하는지 여부
    private var isPhoneAuthCodeSent = false // Firebase로 인증번호 발송 요청했는지 여부
    private var isPhoneAuthVerified = false // Firebase 전화번호 인증 성공 여부

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFindpwBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // 초기 UI 상태 설정: 비밀번호 변경 필드 숨기기 및 버튼 비활성화
        setupInitialUiState()

        // Firebase Phone Auth 콜백 설정
        setupPhoneAuthCallbacks()

        // --- 텍스트 입력 감지 리스너 추가 ---
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSendCodeButtonState() // 학번/전화번호 입력 시 "인증번호 발송" 버튼 상태 업데이트
                updateVerifyButtonState() // 인증번호 입력 시 "인증 확인" 버튼 상태 업데이트
            }
        }

        binding.etStudentId.addTextChangedListener(textWatcher)
        binding.etPhoneNumber.addTextChangedListener(textWatcher)
        binding.etVerificationCode.addTextChangedListener(textWatcher)
        // --- 텍스트 입력 감지 리스너 추가 끝 ---


        // --- 버튼 클릭 리스너 설정 ---

        // 인증번호 발송 버튼 클릭 리스너
        binding.btnSendCode.setOnClickListener {
            val studentId = binding.etStudentId.text.toString().trim()
            val rawPhoneNumber = binding.etPhoneNumber.text.toString().trim()

            if (studentId.isEmpty()) {
                Toast.makeText(this, "학번을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (rawPhoneNumber.isEmpty()) {
                Toast.makeText(this, "전화번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // DB에서 학번과 전화번호 일치 여부 확인
            checkStudentIdAndPhoneNumberInDb(studentId, rawPhoneNumber)
        }

        // 인증 확인 버튼 클릭 리스너
        binding.btnVerify.setOnClickListener {
            val code = binding.etVerificationCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "인증번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (verificationId != null) {
                verifyPhoneNumberWithCode(verificationId!!, code)
            } else {
                Toast.makeText(this, "인증번호 발송을 먼저 해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // 비밀번호 변경 버튼 클릭 리스너
        binding.btnChangePassword.setOnClickListener {
            if (!isPhoneAuthVerified) {
                Toast.makeText(this, "먼저 전화번호 인증을 완료해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val studentId = binding.etStudentId.text.toString().trim() // 학번 사용
            val newPassword = binding.etNewPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "새 비밀번호와 확인 비밀번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPassword != confirmPassword) {
                Toast.makeText(this, "새 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPassword.length < 6) {
                Toast.makeText(this, "비밀번호는 6자 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 비밀번호 변경 로직
            changeUserPasswordInDb(studentId, newPassword)
        }
    }

    // 초기 UI 상태 설정
    private fun setupInitialUiState() {
        binding.etNewPassword.visibility = View.GONE
        binding.etConfirmPassword.visibility = View.GONE
        binding.btnChangePassword.visibility = View.GONE

        binding.btnSendCode.isEnabled = false // 초기에는 발송 버튼 비활성화
        binding.btnSendCode.setBackgroundColor(Color.parseColor("#CCCCCC"))
        binding.etVerificationCode.isEnabled = false // 인증번호 입력칸 비활성화
        binding.btnVerify.isEnabled = false // 인증 확인 버튼 비활성화
        binding.btnVerify.setBackgroundColor(Color.parseColor("#CCCCCC"))
    }

    // 학번/전화번호 입력에 따라 인증번호 발송 버튼 활성화/비활성화
    private fun updateSendCodeButtonState() {
        val studentIdFilled = binding.etStudentId.text.toString().trim().isNotEmpty()
        val phoneNumFilled = binding.etPhoneNumber.text.toString().trim().isNotEmpty()

        val enabled = studentIdFilled && phoneNumFilled
        binding.btnSendCode.isEnabled = enabled
        val color = if (enabled) "#0047AB" else "#CCCCCC"
        binding.btnSendCode.setBackgroundColor(Color.parseColor(color))
        Log.d(TAG, "Send Code button enabled state: $enabled")
    }

    // 인증번호 입력에 따라 인증 확인 버튼 활성화/비활성화
    private fun updateVerifyButtonState() {
        val verificationCodeFilled = binding.etVerificationCode.text.toString().trim().isNotEmpty()
        // isPhoneAuthCodeSent는 Firebase에서 onCodeSent 콜백이 호출되었을 때 true로 설정되어야 함
        val enabled = isPhoneAuthCodeSent && verificationCodeFilled
        binding.btnVerify.isEnabled = enabled
        val color = if (enabled) "#0047AB" else "#CCCCCC"
        binding.btnVerify.setBackgroundColor(Color.parseColor(color))
        Log.d(TAG, "Verify button enabled state: $enabled")
    }

    // 비밀번호 변경 필드 보이기
    private fun showPasswordChangeFields() {
        binding.etNewPassword.visibility = View.VISIBLE
        binding.etConfirmPassword.visibility = View.VISIBLE
        binding.btnChangePassword.visibility = View.VISIBLE
    }

    // 전화번호 형식 변경 (+8210XXXXXXXX)
    private fun formatPhoneNumber(rawPhoneNumber: String): String {
        // 한국 전화번호 010으로 시작하는 경우 +8210으로 변경
        if (rawPhoneNumber.startsWith("010") && rawPhoneNumber.length >= 10) {
            return "+82" + rawPhoneNumber.substring(1).replace("-", "")
        }
        // 다른 국가 번호나 형식이 필요한 경우 추가 로직 구현
        return rawPhoneNumber.replace("-", "") // 하이픈만 제거된 형태로 반환
    }

    // Firebase 전화번호 인증 콜백 설정
    private fun setupPhoneAuthCallbacks() {
        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // SMS 자동 가져오기 또는 즉시 인증 성공
                Log.d(TAG, "onVerificationCompleted:$credential")
                timer?.cancel() // 타이머 중지
                Toast.makeText(this@FindpwActivity, "인증이 자동으로 완료되었습니다.", Toast.LENGTH_SHORT).show()
                isPhoneAuthVerified = true
                showPasswordChangeFields() // 비밀번호 변경 필드 보이게 함

                binding.etVerificationCode.setText(credential.smsCode) // 자동 입력
                binding.btnVerify.isEnabled = false // 인증 완료 후 버튼 비활성화
                binding.btnVerify.setBackgroundColor(Color.parseColor("#CCCCCC"))
                binding.btnSendCode.isEnabled = false // 재전송 버튼 비활성화
                binding.btnSendCode.setBackgroundColor(Color.parseColor("#CCCCCC"))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                // 인증 실패 (예: 잘못된 전화번호, 할당량 초과, 결제 문제 등)
                Log.e(TAG, "onVerificationFailed", e)
                Toast.makeText(this@FindpwActivity, "인증 실패: ${e.message}", Toast.LENGTH_LONG).show()
                isPhoneAuthCodeSent = false
                isPhoneAuthVerified = false
                updateVerifyButtonState() // 버튼 상태 업데이트
            }

            override fun onCodeSent(
                _verificationId: String,
                _token: PhoneAuthProvider.ForceResendingToken
            ) {
                // 인증번호 발송 성공
                Log.d(TAG, "onCodeSent:$_verificationId")
                verificationId = _verificationId
                resendToken = _token
                isPhoneAuthCodeSent = true
                Toast.makeText(this@FindpwActivity, "인증번호가 발송되었습니다.", Toast.LENGTH_SHORT).show()

                binding.etVerificationCode.isEnabled = true // 인증번호 입력칸 활성화
                binding.btnVerify.isEnabled = true // 인증 확인 버튼 활성화
                binding.btnVerify.setBackgroundColor(Color.parseColor("#0047AB"))

                // 타이머 시작 (예: 60초)
                startResendTimer()
            }
        }
    }

    // Firebase를 사용하여 인증번호 발송 요청
    private fun sendFirebaseVerificationCode(phoneNumber: String) {
        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)       // 전화번호
            .setTimeout(60L, TimeUnit.SECONDS) // 제한 시간
            .setActivity(this)                 // 액티비티
            .setCallbacks(callbacks)           // 콜백

        // Check if resendToken is not null before setting it
        resendToken?.let { token ->
            optionsBuilder.setForceResendingToken(token) // 재전송 토큰이 있을 경우에만 설정
        }

        val options = optionsBuilder.build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        Log.d(TAG, "Firebase verification code request sent for $phoneNumber")
    }

    // Firebase 인증번호 확인 함수
    private fun verifyPhoneNumberWithCode(verificationId: String, code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // 인증번호 일치 및 로그인 성공
                    Log.d(TAG, "Phone authentication successful")
                    Toast.makeText(this, "인증 확인 완료!", Toast.LENGTH_SHORT).show()
                    isPhoneAuthVerified = true
                    showPasswordChangeFields() // 비밀번호 변경 필드 보이게 함
                    timer?.cancel() // 타이머 중지

                    // 인증 성공 후 관련 버튼/입력창 비활성화
                    binding.btnSendCode.isEnabled = false
                    binding.btnSendCode.setBackgroundColor(Color.parseColor("#CCCCCC"))
                    binding.btnVerify.isEnabled = false
                    binding.btnVerify.setBackgroundColor(Color.parseColor("#CCCCCC"))
                    binding.etVerificationCode.isEnabled = false

                } else {
                    // 인증번호 불일치 또는 기타 인증 실패
                    Log.w(TAG, "Phone authentication failed", task.exception)
                    Toast.makeText(this, "인증번호가 일치하지 않거나 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    isPhoneAuthVerified = false
                }
            }
    }

    // 학번과 전화번호를 DB에서 확인하는 함수
    private fun checkStudentIdAndPhoneNumberInDb(studentId: String, rawPhoneNumber: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val connection = MSSQLConnector.getConnection()
            var dbPhoneNumberMatched = false
            var message = ""

            if (connection != null) {
                try {
                    val query = "SELECT phone_number FROM students_info WHERE student_school_id = ?"
                    val preparedStatement = connection.prepareStatement(query)
                    preparedStatement.setString(1, studentId)
                    val resultSet = preparedStatement.executeQuery()

                    if (resultSet.next()) {
                        val dbPhoneNumber = resultSet.getString("phone_number")
                        // DB에 저장된 전화번호와 사용자 입력 전화번호 비교 (하이픈 제거 후 비교)
                        if (dbPhoneNumber?.replace("-", "") == rawPhoneNumber.replace("-", "")) {
                            dbPhoneNumberMatched = true
                            message = "학번과 전화번호가 확인되었습니다. 인증번호를 발송합니다."
                        } else {
                            message = "입력하신 휴대폰 번호가 등록된 번호와 다릅니다."
                        }
                    } else {
                        message = "해당 학번에 등록된 정보가 없습니다."
                    }
                    resultSet.close()
                    preparedStatement.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                    message = "DB 확인 중 오류가 발생했습니다: ${e.message}"
                } finally {
                    try { connection.close() } catch (e: Exception) { e.printStackTrace() }
                }
            } else {
                message = "데이터베이스 연결에 실패했습니다."
            }

            withContext(Dispatchers.Main) {
                isStudentIdAndPhoneMatchedInDb = dbPhoneNumberMatched
                Toast.makeText(this@FindpwActivity, message, Toast.LENGTH_LONG).show()

                if (isStudentIdAndPhoneMatchedInDb) {
                    val formattedPhoneNumber = formatPhoneNumber(rawPhoneNumber)
                    sendFirebaseVerificationCode(formattedPhoneNumber)
                } else {
                    isPhoneAuthCodeSent = false // DB 매치 실패 시 SMS 발송 상태 초기화
                    // DB 매치 실패 시에도 updateVerifyButtonState()를 호출하여 인증 관련 UI 상태를 정확히 반영
                    updateVerifyButtonState()
                }
            }
        }
    }

    // DB에서 비밀번호를 변경하는 함수
    private fun changeUserPasswordInDb(studentId: String, newPassword: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val connection = MSSQLConnector.getConnection()
            var isPasswordChanged = false
            var message = ""

            if (connection != null) {
                try {
                    // admin_info 테이블의 admin_pw를 업데이트
                    val updateQuery = "UPDATE admin_info SET admin_pw = ? WHERE admin_id = ?"
                    val preparedStatement = connection.prepareStatement(updateQuery)
                    preparedStatement.setString(1, newPassword)
                    preparedStatement.setString(2, studentId)

                    val rowsAffected = preparedStatement.executeUpdate()
                    if (rowsAffected > 0) {
                        isPasswordChanged = true
                        message = "비밀번호가 성공적으로 변경되었습니다."
                    } else {
                        message = "비밀번호 변경에 실패했습니다. 사용자 정보를 찾을 수 없습니다."
                    }
                    preparedStatement.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                    message = "비밀번호 변경 중 DB 오류가 발생했습니다: ${e.message}"
                } finally {
                    try { connection.close() } catch (e: Exception) { e.printStackTrace() }
                }
            } else {
                message = "데이터베이스 연결에 실패했습니다."
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@FindpwActivity, message, Toast.LENGTH_LONG).show()
                if (isPasswordChanged) {
                    // 비밀번호 변경 성공 후 액티비티 종료 또는 로그인 화면으로 이동
                    finish()
                }
            }
        }
    }

    // 인증번호 재전송 타이머
    private fun startResendTimer() {
        timer?.cancel() // 기존 타이머가 있다면 중지
        binding.btnSendCode.isEnabled = false
        binding.btnSendCode.setBackgroundColor(Color.parseColor("#CCCCCC")) // 비활성화 색상

        timer = object : CountDownTimer(60000, 1000) { // 60초 타이머, 1초마다 업데이트
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.btnSendCode.text = "재전송 (${seconds}초)"
            }

            override fun onFinish() {
                binding.btnSendCode.isEnabled = true
                binding.btnSendCode.text = "인증번호 발송"
                binding.btnSendCode.setBackgroundColor(Color.parseColor("#0047AB")) // 활성화 색상
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel() // 액티비티 종료 시 타이머 중지
    }
}