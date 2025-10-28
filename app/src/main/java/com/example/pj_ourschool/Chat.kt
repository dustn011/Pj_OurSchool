package com.example.pj_ourschool

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.util.Linkify
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

    // ⭐️ viewModels<BakingViewModel>()으로 제네릭 타입 명시
    private val bakingViewModel: BakingViewModel by viewModels<BakingViewModel>()

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

        // ⭐️ 상태 표시줄 색상 설정
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
            val intent = Intent(this, Profile::class.java)
            startActivity(intent)
        }

        // 4. GO 버튼 클릭 리스너 설정 (채팅 기능) - ⭐️⭐️⭐️ 건물 이름/번호 검색 로직 통합 ⭐️⭐️⭐️
        goButton.setOnClickListener {
            val prompt = promptEditText.text.toString().trim()
            if (prompt.isBlank()) return@setOnClickListener

            // 1. 입력 필드 초기화
            promptEditText.setText("")

            // ⭐️⭐️ 1. 건물 번호 추출 시도
            var finalNumber: String? = null

            // A. 건물 번호(숫자) 패턴 검색 (예: "20번 건물")
            val numberRegex = Regex("(\\d+)[번]*\\s*건물")
            val numberMatch = numberRegex.find(prompt)
            finalNumber = numberMatch?.groupValues?.get(1)

            // B. 건물 이름 검색 (예: "공과대학 위치")
            if (finalNumber == null) {
                finalNumber = getBuildingNumberByName(prompt)
            }
            // ⭐️⭐️ 건물 번호 또는 이름 추출 끝 ⭐️⭐️


            if (finalNumber != null) {
                // 건물 정보가 감지되면 AI 모델 호출을 건너뛰고 버튼 응답 추가
                addBuildingNavigationResponse(prompt, finalNumber)
                return@setOnClickListener
            }


            // ⭐️⭐️ 학사일정 키워드 확인 및 응답 로직
            if (prompt.contains("학사일정") || prompt.contains("일정")) {

                val predefinedResponse =
                    "청주대 학사일정에 대해 궁금하시군요. 아래 링크에서 확인하실 수 있습니다: https://www.cju.ac.kr/www/selectBbsNttList.do?bbsNo=881&key=4577"

                bakingViewModel.addPredefinedResponse(prompt, predefinedResponse)

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
                // ⭐️ uiState의 타입을 명시적으로 UiState로 지정
                bakingViewModel.uiState.collect { uiState: UiState ->

                    val isLoading = uiState is UiState.Loading

                    loadingIndicator.visibility = View.GONE
                    goButton.isEnabled = !isLoading
                    promptEditText.isEnabled = !isLoading

                    if (isLoading) {
                        if (loadingMessageView == null) {
                            loadingMessageView = createLoadingMessageView()
                            messagesContainer.addView(loadingMessageView)
                            chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
                        }
                    } else {
                        loadingMessageView?.let {
                            messagesContainer.removeView(it)
                        }
                        loadingMessageView = null
                    }

                    val messages = when (uiState) {
                        is UiState.Success -> uiState.messages
                        is UiState.Error -> uiState.messages
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

        // ⭐️ message의 타입을 ChatMessage로 명시
        messages.forEach { message: ChatMessage ->
            val bubbleView = createMessageBubble(message.text, message.isUser)
            messagesContainer.addView(bubbleView)

            // ⭐️ 특별한 형식의 응답 (버튼)을 처리
            if (!message.isUser && message.text.startsWith(BUILDING_NAV_PREFIX)) {

                // ⭐️ 오류 해결을 위해 split 호출 방식 수정
                val parts = message.text.substringAfter(BUILDING_NAV_PREFIX).split(
                    *arrayOf(BUILDING_NAV_SEPARATOR), // *을 사용하여 Vararg로 전달
                    ignoreCase = false,
                    limit = 2
                )

                if (parts.count() == 2) {
                    val buildingNumber = parts[0]
                    val buildingName = parts[1]

                    // 기존 텍스트 버블을 숨김
                    bubbleView.visibility = View.GONE

                    // 버튼 뷰 추가
                    messagesContainer.addView(createNavigationButton(buildingNumber, buildingName))
                }
            }
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

            if (!isUser) {
                Linkify.addLinks(this, Linkify.WEB_URLS)
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(margin)
                gravity = if (isUser) Gravity.END else Gravity.START
                maxWidth = resources.displayMetrics.widthPixels * 2 / 3
            }

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

        val progressBar = ProgressBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                30.dpToPx(),
                30.dpToPx()
            ).apply {
                marginEnd = 8.dpToPx()
            }
        }

        val textView = TextView(context).apply {
            text = "잠시만 기다려 주세요..."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(padding, padding, padding, padding)

            setBackgroundResource(R.drawable.chat_bubble_bot)
            setTextColor(ContextCompat.getColor(context, R.color.white))
        }

        container.addView(progressBar)
        container.addView(textView)
        return container
    }

    // dp 값을 픽셀(px) 값으로 변환하는 확장 함수
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }


    // =========================================================================
    // ⭐️⭐️⭐️ 건물 위치 이동 버튼 관련 함수 ⭐️⭐️⭐️
    // =========================================================================

    private val BUILDING_NAV_PREFIX = "[BUILDING_NAV_REQUEST]"
    private val BUILDING_NAV_SEPARATOR = "|"

    private fun addBuildingNavigationResponse(userPrompt: String, buildingNumber: String) {

        val buildingName = getBuildingNameByNumber(buildingNumber)

        if (buildingName == null) {
            val errorResponse = "죄송해요, '$buildingNumber'번 건물 정보를 찾을 수 없어요."
            bakingViewModel.addPredefinedResponse(userPrompt, errorResponse)
            return
        }

        // 1. 텍스트 답변 (버블): 사용자 메시지(userPrompt)를 그대로 전달하여 사용자가 입력한 메시지('신관')가 보이도록 복구
        val textResponse = "'$buildingNumber'번 건물, ${buildingName}의 위치를 지도로 확인하시겠어요?"
        // ⭐️⭐️⭐️ 중요 수정 1: userPrompt를 다시 전달하여 사용자 메시지 버블이 생성되도록 함 ⭐️⭐️⭐️
        bakingViewModel.addPredefinedResponse(userPrompt, textResponse)

        // 2. 버튼 응답 (ViewModel의 messages 리스트에 특수 포맷으로 추가)
        val buttonMessageText = "$BUILDING_NAV_PREFIX$buildingNumber$BUILDING_NAV_SEPARATOR$buildingName"

        // ⭐️⭐️⭐️ 중요 수정 2: 빈 문자열("")을 전달하여 이 호출에서 추가적인 빈 사용자 버블이 생기는 것을 방지함 ⭐️⭐️⭐️
        bakingViewModel.addPredefinedResponse("", buttonMessageText)
    }

    private fun createNavigationButton(buildingNumber: String, buildingName: String): View {
        val context = this@Chat
        val margin = 8.dpToPx()
        val padding = 12.dpToPx()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(margin)
            }
        }

        val button = Button(context).apply {
            text = "${buildingName} 위치 보기 👀"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3557C8"))
            setPadding(padding * 2, padding, padding * 2, padding)

            setOnClickListener {
                val intent = Intent(context, Campus::class.java).apply {
                    putExtra("buildingNumber", buildingNumber)
                    // ⭐️⭐️⭐️ 핵심 수정: Campus로 이동할 때 하단 메뉴 숨김 플래그 추가 ⭐️⭐️⭐️
                    putExtra("hideBottomMenu", true)
                }
                startActivity(intent)
            }
        }

        container.addView(button)
        return container
    }

    /**
     * 프롬프트에서 건물 이름 키워드를 검색하여 건물 번호를 반환합니다.
     * 괄호와 공백을 무시하고 핵심 단어만으로 검색합니다.
     */
    private fun getBuildingNumberByName(prompt: String): String? {

        // 1. 사용자 입력에서 괄호와 공백을 제거하고 소문자화 (검색 키워드 정제)
        val normalizedPrompt = prompt
            .replace("[()\\s]|건물|위치|어디|찾아줘".toRegex(), "") // 건물 관련 불필요한 단어도 제거
            .toLowerCase()

        // 입력 키워드가 너무 짧으면 검색하지 않음 (오탐 방지)
        if (normalizedPrompt.length < 2) return null

        // 건물 이름 리스트 순회
        for ((number, name) in buildingInfoList) {

            // 2. 건물 이름에서도 괄호와 공백을 제거하고 소문자화 (비교 대상 정제)
            val normalizedName = name.replace("[()\\s]".toRegex(), "").toLowerCase()

            // ⭐️⭐️⭐️ 핵심 수정: 정제된 건물 이름이 정제된 사용자 입력을 포함하는지 확인 ⭐️⭐️⭐️
            // 예: "새천년종합정보관" (normalizedName)이 "새천년" (normalizedPrompt)을 포함하는가?
            if (normalizedName.contains(normalizedPrompt)) {
                return number
            }

            // ⭐️⭐️ 선택적 역방향 매칭 ⭐️⭐️
            // 사용자가 '대학원'처럼 명확한 풀네임을 입력했고, 그 풀네임이 건물명에 포함될 경우
            // 예: (입력) '대학원'이 (건물명) '대학원'에 포함되는가?
            // 위 로직에서 이미 처리되지만, 만약 위에 걸리지 않는 경우를 대비합니다.
            // if (normalizedPrompt.contains(normalizedName) && normalizedPrompt.length > 3) {
            //     return number
            // }
        }
        return null
    }

    /**
     * 건물 번호로 이름을 찾는 함수. (getBuildingNumberByName에서 사용됨)
     */
    private fun getBuildingNameByNumber(number: String): String? {
        return buildingInfoList.find { it.first == number }?.second
    }

    // ⭐️⭐️⭐️ 건물 정보 리스트 정의 ⭐️⭐️⭐️
    // 이 리스트는 Campus.kt의 리스트와 동일해야 합니다.
    private val buildingInfoList = listOf(
        Pair("01", "청석교육역사관"),
        Pair("02", "대학원"),
        Pair("03", "입학취업지원관"),
        Pair("04", "박물관"),
        Pair("05", "청석관"),
        Pair("06", "융합관"),
        Pair("07", "공과대학(구관)"),
        Pair("08", "보건의료과학대학"),
        Pair("09", "경상대학"),
        Pair("10", "교수연구동"),
        Pair("11", "중앙도서관"),
        Pair("12", "육군학군단"),
        Pair("13", "종합강의동"),
        Pair("14", "공과대학 신관"),
        Pair("16", "CJU학생지원관"),
        Pair("18", "금융센터"),
        Pair("20", "인문사회사범대학"),
        Pair("23", "PoE관"),
        Pair("26", "예술대학(구관)"),
        Pair("31", "예술대학(신관)"),
        Pair("32", "공예관"),
        Pair("35", "공군학군단"),
        Pair("36", "예지관"),
        Pair("39", "충의관"),
        Pair("42", "새천년종합정보관")
    )
}