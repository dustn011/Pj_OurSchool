package com.example.pj_ourschool

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ⭐️ Constants 객체는 별도 파일에 있지만, ViewModel은 이를 사용합니다.

class BakingViewModel : ViewModel() {

    // ⭐️ StateFlow를 Success 상태의 빈 목록으로 초기화하여 메시지 목록을 항상 유지
    private val _uiState: MutableStateFlow<UiState> =
        MutableStateFlow(UiState.Success(emptyList()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = Constants.GEMINI_API_KEY
    )

    // 대화 기록 저장을 위한 mutableList (Gemini API용)
    private val history = mutableListOf<Content>()
    private var isBotResponding = false

    // ⭐️ Helper: 현재 메시지 목록을 안전하게 가져옵니다.
    private fun getCurrentMessages(): List<ChatMessage> {
        return when (val state = _uiState.value) {
            is UiState.Success -> state.messages
            is UiState.Error -> state.messages
            else -> emptyList() // Initial, Loading 상태에서는 빈 리스트 반환
        }
    }

    // ⭐️⭐️ 추가 함수: Activity 로직에서 미리 정의된 응답을 추가할 수 있도록 함 ⭐️⭐️
    fun addPredefinedResponse(prompt: String, response: String) {
        if (isBotResponding) return

        // 1. 사용자 메시지와 응답을 순차적으로 목록에 추가
        val currentMessages = getCurrentMessages()
        val updatedMessages = currentMessages +
                ChatMessage(prompt, isUser = true) +
                ChatMessage(response, isUser = false)

        // 2. UI 상태 업데이트 (Success 상태로 전환)
        _uiState.value = UiState.Success(updatedMessages)

        // 3. Gemini 히스토리에 추가 (선택 사항: 다음 AI 응답에 영향을 줄 수 있음)
        history.add(content { text(prompt) })
        history.add(content { text(response) })
    }

    fun sendPrompt(prompt: String) {
        if (prompt.isBlank() || isBotResponding) return

        val currentMessages = getCurrentMessages()

        // 1. 사용자 메시지를 목록에 추가
        val updatedUserMessages = currentMessages + ChatMessage(prompt, isUser = true)

        // 2. 사용자 메시지 UI에 즉시 반영 (Success 상태 발행)
        _uiState.value = UiState.Success(updatedUserMessages)

        // 3. 로딩 시작
        _uiState.value = UiState.Loading

        // 4. API 호출
        viewModelScope.launch(Dispatchers.IO) {
            isBotResponding = true
            try {
                val newContent = content { text(prompt) }
                val fullHistory = history + newContent

                // API 호출
                val response = generativeModel.generateContent(*fullHistory.toTypedArray())

                response.text?.let { outputContent ->
                    // 5. 챗봇 응답 기록에 추가
                    val botMessage = ChatMessage(outputContent, isUser = false)
                    val finalMessages = updatedUserMessages + botMessage

                    // 6. 성공 상태 발행
                    _uiState.value = UiState.Success(finalMessages)

                    // 7. Gemini 모델의 히스토리 업데이트
                    history.add(newContent)
                    history.add(content { text(outputContent) })

                } ?: run {
                    handleError("API 응답이 비어있습니다.", updatedUserMessages)
                }
            } catch (e: Exception) {
                handleError(e.localizedMessage ?: "알 수 없는 오류", updatedUserMessages)
            } finally {
                isBotResponding = false
            }
        }
    }

    private fun handleError(errorMsg: String, currentMessages: List<ChatMessage>) {
        _uiState.value = UiState.Error(errorMsg, currentMessages)
    }
}