package com.example.pj_ourschool

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.util.Linkify // ⭐️ Linkify 임포트 추가
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
import de.hdodenhof.circleimageview.CircleImageView
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

    // ⭐️ 헤더 요소 선언
    private lateinit var leftArrow: ImageView
    private lateinit var profileImageView: CircleImageView

    // ⭐️ 로딩 메시지 뷰 참조 변수 추가
    private var loadingMessageView: View? = null

    // ⭐️ 프로필 이미지 관련 상수 추가
    private val PROFILE_IMAGE_PREF = "profile_image_pref"
    private val KEY_PROFILE_IMAGE_URI = "profile_image_uri"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // ⭐️ 상태 표시줄 색상 설정 (헤더와 통일)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = Color.parseColor("#3557C8")
        }


        // 2. UI 요소 초기화 (findViewById) - 채팅 영역
        promptEditText = findViewById(R.id.promptEditText)
        goButton = findViewById(R.id.goButton)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        messagesContainer = findViewById(R.id.messagesContainer)
        chatScrollView = findViewById(R.id.chatScrollView)

        // ⭐️ UI 요소 초기화 (findViewById) - 커스텀 헤더
        leftArrow = findViewById(R.id.left_arrow)
        profileImageView = findViewById(R.id.Profile)


        // 3. ⭐️ 헤더 클릭 리스너 설정
        leftArrow.setOnClickListener { finish() }

        profileImageView.setOnClickListener {
            // Profile 액티비티로 이동
            val intent = Intent(this, Profile::class.java)
            startActivity(intent)
        }

        // 4. GO 버튼 클릭 리스너 설정 (채팅 기능)
        goButton.setOnClickListener {
            val prompt = promptEditText.text.toString().trim()
            if (prompt.isBlank()) return@setOnClickListener

            // 1. 입력 필드 초기화
            promptEditText.setText("")

            // ⭐️⭐️ 학사일정 키워드 확인 및 응답 로직 (ViewModel 사용) ⭐️⭐️
            if (prompt.contains("학사일정") || prompt.contains("일정")) {

                val predefinedResponse = "청주대 학사일정에 대해 궁금하시군요. 아래 링크에서 확인하실 수 있습니다: https://www.cju.ac.kr/www/selectBbsNttList.do?bbsNo=881&key=4577"

                // ⭐️ ViewModel 함수 호출: 모든 대화 기록을 ViewModel이 관리하도록 함
                bakingViewModel.addPredefinedResponse(prompt, predefinedResponse)

                // ViewModel 구독을 통해 UI가 업데이트되므로 return
                return@setOnClickListener
            }
            // ⭐️⭐️ 학사일정 키워드 로직 끝 ⭐️⭐️

            // 2. (일반 질문) ViewModel에 프롬프트 전송
            bakingViewModel.sendPrompt(prompt)
        }


        // 5. UI State 구독 (ViewModel의 변화 감지)
        observeUiState()

        // 6. ⭐️ 프로필 이미지 로드
        loadProfileImageUri()
    }

    override fun onResume() {
        super.onResume()
        loadProfileImageUri()
    }

    // ⭐️ 프로필 이미지 로드 함수
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

                    val isLoading = uiState is UiState.Loading

                    // 중앙 로딩 표시기 사용 안 함
                    loadingIndicator.visibility = View.GONE

                    // 입력 필드 및 버튼 제어
                    goButton.isEnabled = !isLoading
                    promptEditText.isEnabled = !isLoading

                    // ⭐️⭐️ 로딩 메시지 뷰 관리 로직 (잠시만 기다려 주세요) ⭐️⭐️
                    if (isLoading) {
                        // 로딩 뷰가 없으면 새로 생성하여 메시지 목록에 추가
                        if (loadingMessageView == null) {
                            loadingMessageView = createLoadingMessageView()
                            messagesContainer.addView(loadingMessageView)
                            chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
                        }
                    } else {
                        // 로딩이 끝나면 로딩 뷰 제거
                        loadingMessageView?.let {
                            messagesContainer.removeView(it)
                        }
                        loadingMessageView = null
                    }
                    // ⭐️⭐️ 로딩 메시지 뷰 관리 로직 끝 ⭐️⭐️


                    // Success/Error 상태일 때만 메시지 목록 업데이트
                    val messages = when (uiState) {
                        is UiState.Success -> uiState.messages
                        is UiState.Error -> uiState.messages
                        is UiState.Initial -> {
                            messagesContainer.removeAllViews()
                            return@collect
                        }
                        is UiState.Loading -> return@collect // 로딩 중에는 updateChatUi 호출 안 함
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

            // ⭐️⭐️ 링크 활성화 로직 추가 ⭐️⭐️
            if (!isUser) { // AI가 보낸 메시지에만 링크 활성화
                Linkify.addLinks(this, Linkify.WEB_URLS)
                // 링크가 흰색 배경에서 잘 보이도록 setLinkTextColor(Color.BLUE) 등을 추가할 수 있습니다.
            }
            // ⭐️⭐️ 링크 활성화 로직 끝 ⭐️⭐️


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

    // ⭐️⭐️ 로딩 메시지 뷰를 생성하는 함수 ⭐️⭐️
    private fun createLoadingMessageView(): View {
        val context = this@Chat
        val margin = 8.dpToPx()
        val padding = 12.dpToPx()

        // 1. 전체 컨테이너 (왼쪽 정렬)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(margin)
            }
        }

        // 2. ProgressBar (스피너)
        val progressBar = ProgressBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                30.dpToPx(),
                30.dpToPx()
            ).apply {
                marginEnd = 8.dpToPx()
            }
        }

        // 3. 텍스트 뷰 ("잠시만 기다려 주세요")
        val textView = TextView(context).apply {
            text = "잠시만 기다려 주세요..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(padding, padding, padding, padding)

            // AI 메시지와 동일한 말풍선 스타일 적용
            setBackgroundResource(R.drawable.chat_bubble_bot)
            setTextColor(ContextCompat.getColor(context, R.color.white))
        }

        container.addView(progressBar)
        container.addView(textView)
        return container
    }
    // ⭐️⭐️ 로딩 메시지 뷰 생성 함수 끝 ⭐️⭐️

    // dp 값을 픽셀(px) 값으로 변환하는 확장 함수
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}