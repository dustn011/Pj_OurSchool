package com.example.pj_ourschool

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint // Paint 클래스를 위해 import 추가
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class Profile : AppCompatActivity() {

    private lateinit var profileImageView: ImageView
    private lateinit var getGalleryImage: ActivityResultLauncher<Intent> // 갤러리 결과 런처
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String> // 권한 요청 런처

    private val PROFILE_IMAGE_PREF = "profile_image_pref" // SharedPreferences 파일 이름
    private val KEY_PROFILE_IMAGE_URI = "profile_image_uri" // 프로필 이미지 URI 저장 키

    private var loggedInUserId: String? = null // 로그인된 사용자 ID를 저장할 변수

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile) // 내 정보 레이아웃 설정

        val leftArrow: ImageView = findViewById(R.id.left_arrow)
        val userIdTextView: TextView = findViewById(R.id.text_id) // 아이디를 표시할 TextView
        val logoutTextView: TextView = findViewById(R.id.text_logout) // 로그아웃 TextView
        val changePasswordTextView: TextView = findViewById(R.id.text_change_password) // 비밀번호 변경 TextView
        profileImageView = findViewById(R.id.profile_image) // 프로필 이미지 ImageView

        // SharedPreferences에서 아이디를 읽어옵니다.
        val sharedPref = getSharedPreferences("user_info", Context.MODE_PRIVATE)
        // 로그인 시 저장했던 "userId" 키로 학번(admin_id)을 가져옵니다.
        loggedInUserId = sharedPref.getString(LoginActivity.KEY_USER_ID, "사용자") // LoginActivity에 정의된 KEY_USER_ID 사용
        userIdTextView.text = "아이디: $loggedInUserId"

        // 로그아웃 텍스트뷰에 밑줄 추가
        logoutTextView.paintFlags = logoutTextView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        // 비밀번호 변경 텍스트뷰에 밑줄 추가 (이 줄이 추가됩니다)
        changePasswordTextView.paintFlags = changePasswordTextView.paintFlags or Paint.UNDERLINE_TEXT_FLAG


        leftArrow.setOnClickListener { finish() }

        // --- 갤러리 관련 설정 시작 ---
        getGalleryImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.let { uri ->
                    profileImageView.setImageURI(uri)
                    saveProfileImageUri(uri)
                    Toast.makeText(this, "프로필 이미지가 변경되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openGallery()
            } else {
                Toast.makeText(this, "갤러리 접근 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

        profileImageView.setOnClickListener {
            checkAndRequestPermission()
        }
        // --- 갤러리 관련 설정 끝 ---

        // 4. 저장된 프로필 이미지 URI 불러와서 설정
        loadProfileImageUri()

        // 비밀번호 변경 텍스트뷰 클릭 리스너 설정
        changePasswordTextView.setOnClickListener {
            if (loggedInUserId != null && loggedInUserId != "사용자") {
                val intent = Intent(this, ChangePasswordActivity::class.java)
                intent.putExtra(ChangePasswordActivity.EXTRA_USER_ID, loggedInUserId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "사용자 정보를 찾을 수 없습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                finish()
            }
        }

        // 로그아웃 텍스트뷰 클릭 리스너 설정
        logoutTextView.setOnClickListener {
            val editor = sharedPref.edit()
            editor.remove(LoginActivity.KEY_USER_ID) // 아이디 정보 삭제
            editor.remove(LoginActivity.KEY_AUTO_LOGIN) // 자동 로그인 상태 관련 키 삭제
            editor.remove(LoginActivity.KEY_USER_PASSWORD) // 비밀번호 (만약 저장했다면)
            editor.apply()

            // 다음 줄을 제거하거나 주석 처리하여 로그아웃 시 프로필 이미지 URI가 삭제되지 않도록 합니다.
            // val profileImagePref = getSharedPreferences(PROFILE_IMAGE_PREF, Context.MODE_PRIVATE)
            // profileImagePref.edit().remove(KEY_PROFILE_IMAGE_URI).apply()


            // 로그인 화면으로 이동
            val intent = Intent(this, LoginActivity::class.java)
            // 새로운 태스크로 시작하고 기존 스택 모두 제거 (뒤로 가기로 로그인 화면 오지 않도록)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish() // 현재 액티비티 종료
        }
    }

    /**
     * 갤러리 접근 권한 확인 및 요청
     */
    private fun checkAndRequestPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                openGallery()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "프로필 사진 설정을 위해 갤러리 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(permission)
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    /**
     * 갤러리를 엽니다.
     */
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        getGalleryImage.launch(intent)
    }

    /**
     * 선택된 프로필 이미지 URI를 SharedPreferences에 저장합니다.
     * @param uri 저장할 이미지의 URI
     */
    private fun saveProfileImageUri(uri: Uri) {
        val sharedPref = getSharedPreferences(PROFILE_IMAGE_PREF, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(KEY_PROFILE_IMAGE_URI, uri.toString())
            apply()
        }
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * SharedPreferences에서 프로필 이미지 URI를 불러와 ImageView에 설정합니다.
     */
    private fun loadProfileImageUri() {
        val sharedPref = getSharedPreferences(PROFILE_IMAGE_PREF, Context.MODE_PRIVATE)
        val savedUriString = sharedPref.getString(KEY_PROFILE_IMAGE_URI, null)

        if (savedUriString != null) {
            try {
                val uri = Uri.parse(savedUriString)
                profileImageView.setImageURI(uri)
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Error loading profile image URI: ${e.message}")
                profileImageView.setImageResource(R.drawable.default_profile)
                sharedPref.edit().remove(KEY_PROFILE_IMAGE_URI).apply()
            }
        } else {
            profileImageView.setImageResource(R.drawable.default_profile)
        }
    }
}