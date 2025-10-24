package com.example.pj_ourschool

/**
 * ⭐️ 대화 메시지를 저장하는 데이터 클래스입니다.
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean // true면 사용자 메시지 (오른쪽 정렬), false면 챗봇 메시지 (왼쪽 정렬)
)

/**
 * ⭐️ 대화 기록 리스트를 포함하도록 수정된 UiState 계층 구조입니다.
 */
sealed interface UiState {

    /**
     * 초기 상태 (빈 화면)
     */
    object Initial : UiState

    /**
     * 로딩 상태
     */
    object Loading : UiState

    /**
     * 텍스트 생성이 완료되었으며, 전체 대화 기록을 포함합니다.
     */
    data class Success(val messages: List<ChatMessage>) : UiState

    /**
     * 오류가 발생했으며, 에러 메시지와 발생 시점까지의 대화 기록을 포함합니다.
     */
    data class Error(val errorMessage: String, val messages: List<ChatMessage>) : UiState
}