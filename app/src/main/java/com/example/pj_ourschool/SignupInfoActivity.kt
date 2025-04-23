package com.example.pj_ourschool

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.pj_ourschool.MSSQLConnector
import kotlinx.coroutines.*

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

    private var isSchoolVerified = false // 학교 인증 성공 여부 추적 변수

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

        // 텍스트 입력 감지
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateSchoolVerifyButton()    // 학교인증 버튼 활성화/비활성화
                updateSignupButtonState()     // 회원가입 완료 버튼 활성화/비활성화 (체크박스 및 학교 인증 기준)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        editSchoolId.addTextChangedListener(watcher)
        editPassword.addTextChangedListener(watcher)

        // 뒤로 가기
        imgBack.setOnClickListener {
            finish()
        }

        // 포털 링크 이동
        val portalUrl = "https://portal.cju.ac.kr/"
        tvFindId.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(portalUrl)))
        }
        tvFindPassword.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(portalUrl)))
        }

        // 체크박스 동작
        val checkBoxes = listOf(cbAgreeAll, cbServiceTerms, cbPrivacyTerms, cbMarketingTerms)
        checkBoxes.forEach { checkBox ->
            // tint 제거
            checkBox.buttonTintList = null
            checkBox.setOnClickListener {
                handleCheckboxLogic(checkBox)
            }
        }

        // 인증 버튼 클릭
        btnSchoolVerify.setOnClickListener {
            val id = editSchoolId.text.toString().trim()
            val pw = editPassword.text.toString().trim()

            CoroutineScope(Dispatchers.IO).launch {
                val connection = MSSQLConnector.getConnection()
                var isVerified = false

                if (connection != null) {
                    val statement = connection.createStatement()
                    val query = "SELECT student_school_id, student_school_Password FROM students_info WHERE student_school_id = ? AND student_school_Password = ?"
                    val preparedStatement = connection.prepareStatement(query)
                    preparedStatement.setString(1, id)
                    preparedStatement.setString(2, pw)

                    val resultSet = preparedStatement.executeQuery()

                    if (resultSet.next()) {
                        val insertQuery = "INSERT INTO admin_info VALUES (?, ?)"
                        val insertStmt = connection.prepareStatement(insertQuery)
                        insertStmt.setString(1, id)
                        insertStmt.setString(2, pw)
                        insertStmt.executeUpdate()
                        isVerified = true
                    } else {
                        isVerified = false
                    }

                    withContext(Dispatchers.Main) {
                        isSchoolVerified = isVerified // 인증 결과 업데이트
                        showVerificationDialog(isVerified)
                        updateSignupButtonState() // 인증 결과에 따라 회원가입 버튼 상태 업데이트
                        if (!isVerified) {
                            Toast.makeText(this@SignupInfoActivity, "학교 인증에 실패했습니다. 인증 후 회원가입을 진행해주세요.", Toast.LENGTH_LONG).show()
                        }
                    }

                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SignupInfoActivity, "데이터베이스 연결에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 회원가입 완료 버튼 클릭
        btnFinishSignup.setOnClickListener {
            if (isSchoolVerified) { // 학교 인증 성공 시에만 회원가입 진행
                val SchoolId = editSchoolId.text.toString().trim()
                val Password = editPassword.text.toString().trim()

                if (SchoolId.isNotEmpty() && Password.isNotEmpty()) {
                    Toast.makeText(this, "회원가입이 완료되었습니다. 로그인해주세요!", Toast.LENGTH_LONG).show()

                    // SharedPreferences에 아이디 저장 (로그인 화면에서 자동 완성을 위해)
                    val sharedPref = getSharedPreferences("user_info", Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putString("userId", SchoolId)
                        apply()
                    }

                    // 로그인 화면으로 이동
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()

                } else {
                    Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "학교 인증을 먼저 진행해주세요.", Toast.LENGTH_SHORT).show()
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
        } else {
            cbAgreeAll.isChecked = cbServiceTerms.isChecked &&
                    cbPrivacyTerms.isChecked &&
                    cbMarketingTerms.isChecked
        }

        updateSignupButtonState()
    }

    // 회원가입 버튼 제어 (필수 체크박스 및 학교 인증 기준)
    private fun updateSignupButtonState() {
        val requiredChecked = cbServiceTerms.isChecked && cbPrivacyTerms.isChecked
        btnFinishSignup.isEnabled = requiredChecked && isSchoolVerified
        val color = if (requiredChecked && isSchoolVerified) "#0047AB" else "#CCCCCC"
        btnFinishSignup.setBackgroundColor(Color.parseColor(color))
    }

    // 학교인증 버튼 제어 (ID/PW 입력 기준)
    private fun updateSchoolVerifyButton() {
        val idFilled = editSchoolId.text.toString().isNotEmpty()
        val pwFilled = editPassword.text.toString().isNotEmpty()
        val enabled = idFilled && pwFilled
        btnSchoolVerify.isEnabled = enabled
        val color = if (enabled) "#0047AB" else "#CCCCCC"
        btnSchoolVerify.setBackgroundColor(Color.parseColor(color))
    }

    // 인증 결과 팝업
    private fun showVerificationDialog(success: Boolean) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_verifydialog, null)
        val dialogBuilder = AlertDialog.Builder(this).setView(dialogView)
        val alertDialog = dialogBuilder.create()

        val txtMessage = dialogView.findViewById<TextView>(R.id.txtDialogMessage)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        txtMessage.text = if (success) {
            "인증되었습니다!"
        } else {
            "ID/PW 오류입니다. 다시 한 번 입력해주세요."
        }

        btnConfirm.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }
}