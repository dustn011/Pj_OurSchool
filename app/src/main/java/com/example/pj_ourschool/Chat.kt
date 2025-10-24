package com.example.pj_ourschool

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.hdodenhof.circleimageview.CircleImageView // CircleImageView 임포트
import kotlinx.coroutines.launch


// BakingViewModel, ChatMessage, UiState는 동일 패키지에 정의되어 있어야 합니다.

class Chat : AppCompatActivity() {

    private val bakingViewModel: BakingViewModel by viewModels()

    // ⭐️ UI 요소 선언
    private lateinit var promptEditText: EditText
    private lateinit var goButton: Button
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var messagesContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView

    // ⭐️ 헤더 요소 선언 (Chat 액티비티에서 가져옴)
    private lateinit var leftArrow: ImageView
    private lateinit var profileImageView: CircleImageView

    // ⭐️ 프로필 이미지 관련 상수 추가 (Chat 액티비티에서 가져옴)
    private val PROFILE_IMAGE_PREF = "profile_image_pref"
    private val KEY_PROFILE_IMAGE_URI = "profile_image_uri"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ⭐️ activity_aichat 레이아웃 사용
        setContentView(R.layout.activity_chat)

        // ⭐️ 상태 표시줄 색상 설정 코드 추가
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor("#3557C8")

            // 또는 ContextCompat을 사용하여 colors.xml에 정의된 리소스를 사용할 수 있습니다.
            // window.statusBarColor = ContextCompat.getColor(this, R.color.my_header_color)
        }


        // 2. UI 요소 초기화 (findViewById) - 채팅 영역
        promptEditText = findViewById(R.id.promptEditText)
        goButton = findViewById(R.id.goButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        messagesContainer = findViewById(R.id.messagesContainer)
        chatScrollView = findViewById(R.id.chatScrollView)

        // ⭐️ UI 요소 초기화 (findViewById) - 커스텀 헤더
        leftArrow = findViewById(R.id.left_arrow)
        // CircleImageView 타입으로 초기화
        profileImageView = findViewById(R.id.Profile)


        // 3. ⭐️ 헤더 클릭 리스너 설정 (Chat 액티비티에서 통합)
        leftArrow.setOnClickListener { finish() }

        profileImageView.setOnClickListener {
            // Profile 액티비티로 이동
            val intent = Intent(this, Profile::class.java)
            startActivity(intent)
        }

        // 4. GO 버튼 클릭 리스너 설정 (채팅 기능)
        goButton.setOnClickListener {
            val prompt = promptEditText.text.toString()
            bakingViewModel.sendPrompt(prompt)
            promptEditText.setText("") // 입력 필드 초기화
        }

        // 5. UI State 구독 (ViewModel의 변화 감지)
        observeUiState()

        // 6. ⭐️ 프로필 이미지 로드
        loadProfileImageUri()
    }

    override fun onResume() {
        super.onResume()
        // 액티비티가 재개될 때 프로필 이미지를 다시 로드하여 최신 상태 유지
        loadProfileImageUri()
    }

    // ⭐️ 프로필 이미지 로드 함수 (Chat 액티비티에서 통합)
    private fun loadProfileImageUri() {
        val sharedPref = getSharedPreferences(PROFILE_IMAGE_PREF, Context.MODE_PRIVATE)
        val savedUriString = sharedPref.getString(KEY_PROFILE_IMAGE_URI, null)

        if (savedUriString != null) {
            try {
                val uri = Uri.parse(savedUriString)
                profileImageView.setImageURI(uri)
            } catch (e: Exception) {
                Log.e("AiChatActivity", "Error loading profile image URI: ${e.message}")
                profileImageView.setImageResource(R.drawable.default_profile)
                // 오류 발생 시 저장된 URI 제거하여 다시 로드 시도하지 않도록
                sharedPref.edit().remove(KEY_PROFILE_IMAGE_URI).apply()
            }
        } else {
            profileImageView.setImageResource(R.drawable.default_profile)
        }
    }


    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bakingViewModel.uiState.collect { uiState ->
                    // 로딩 상태에 따른 UI 활성화/비활성화
                    val isLoading = uiState is UiState.Loading
                    loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
                    goButton.isEnabled = !isLoading
                    promptEditText.isEnabled = !isLoading

                    // Success/Error 상태일 때만 메시지 목록 업데이트
                    val messages = when (uiState) {
                        is UiState.Success -> uiState.messages
                        is UiState.Error -> {
                            // 에러 메시지 처리 (필요시 Toast 등)
                            uiState.messages
                        }
                        is UiState.Initial -> {
                            messagesContainer.removeAllViews()
                            return@collect
                        }
                        is UiState.Loading -> return@collect
                    }

                    updateChatUi(messages)
                }
            }
        }
    }

    // 대화 기록을 기반으로 채팅 UI를 새로 그리는 핵심 함수
    private fun updateChatUi(messages: List<ChatMessage>) {
        messagesContainer.removeAllViews()

        messages.forEach { message ->
            val bubbleView = createMessageBubble(message.text, message.isUser)
            messagesContainer.addView(bubbleView)
        }

        // 스크롤을 항상 맨 아래로 이동
        chatScrollView.post {
            chatScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    // 메시지 말풍선 TextView를 생성하는 함수
    private fun createMessageBubble(text: String, isUser: Boolean): TextView {
        val padding = 12.dpToPx()
        val margin = 8.dpToPx()

        return TextView(this@Chat).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(padding, padding, padding, padding)

            // 레이아웃 매개변수 설정
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(margin)

                // 정렬 설정
                gravity = if (isUser) Gravity.END else Gravity.START

                // 최대 너비 제한 (화면 너비의 2/3)
                maxWidth = resources.displayMetrics.widthPixels * 2 / 3
            }

            // 배경 및 텍스트 색상 설정
            if (isUser) {
                setBackgroundResource(R.drawable.chat_bubble_user)
                setTextColor(ContextCompat.getColor(this@Chat, R.color.black))
            } else {
                setBackgroundResource(R.drawable.chat_bubble_bot)
                setTextColor(ContextCompat.getColor(this@Chat, R.color.white))
            }
        }
    }

    // dp 값을 픽셀(px) 값으로 변환하는 확장 함수
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}