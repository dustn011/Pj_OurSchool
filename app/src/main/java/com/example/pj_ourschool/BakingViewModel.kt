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

class BakingViewModel : ViewModel() {

    private val _uiState: MutableStateFlow<UiState> =
        MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        // ⭐️ Constants 객체를 통해 하드코딩된 API 키를 사용합니다.
        apiKey = Constants.GEMINI_API_KEY
    )

    // 대화 기록 저장을 위한 mutableList
    private val history = mutableListOf<Content>()
    private var isBotResponding = false

    fun sendPrompt(prompt: String) {
        if (prompt.isBlank() || isBotResponding) return

        // 현재 메시지 목록을 가져옵니다.
        val currentMessages = when (_uiState.value) {
            is UiState.Success -> (_uiState.value as UiState.Success).messages
            is UiState.Error -> (_uiState.value as UiState.Error).messages
            else -> emptyList()
        }

        // 1. 사용자 메시지 기록에 추가 및 UI 업데이트
        val updatedUserMessages = currentMessages + ChatMessage(prompt, isUser = true)
        _uiState.value = UiState.Loading
        _uiState.value = UiState.Success(updatedUserMessages)

        // 2. API 호출
        viewModelScope.launch(Dispatchers.IO) {
            isBotResponding = true
            try {
                // history를 포함하여 API 호출 (대화 기록 유지)
                val newContent = content { text(prompt) }
                val fullHistory = history + newContent

                val response = generativeModel.generateContent(*fullHistory.toTypedArray())

                response.text?.let { outputContent ->
                    // 3. 챗봇 응답 기록에 추가
                    val botMessage = ChatMessage(outputContent, isUser = false)
                    val finalMessages = updatedUserMessages + botMessage

                    _uiState.value = UiState.Success(finalMessages)

                    // 4. Gemini 모델의 히스토리 업데이트 (다음 대화를 위해)
                    history.add(newContent)
                    history.add(content { text(outputContent) })

                } ?: run {
                    handleError("API 응답이 비어있습니다.", updatedUserMessages)
                }
            } catch (e: Exception) {
                // 에러 발생 시 현재 메시지 목록을 유지하면서 에러 상태로 전환
                handleError(e.localizedMessage ?: "알 수 없는 오류", updatedUserMessages)
            } finally {
                isBotResponding = false
            }
        }
    }

    private fun handleError(errorMsg: String, currentMessages: List<ChatMessage>) {
        // UiState.Error는 errorMessage와 대화 기록을 모두 가집니다.
        _uiState.value = UiState.Error(errorMsg, currentMessages)
    }
}