package com.gilcap.app.feature.capsulecreate

/** 확인 단계에서 알아듣는 음성 명령 */
enum class VoiceCommand {
    REGISTER, RETRY, CANCEL;

    companion object {
        // 문장 안에 들어 있으면 인정하는 말
        private val cancelWords = listOf("취소", "그만", "안해", "하지마")
        private val retryWords = listOf("다시", "재녹음")
        private val registerWords = listOf("등록", "저장", "남겨", "남길게", "좋아", "맞아", "그래")

        // 짧아서 다른 말에 섞이기 쉬운 말은 그 말만 했을 때 인정
        private val registerExact = setOf("네", "예", "응", "어", "넵", "네네")

        /**
         * STT 결과 → 명령. 못 알아들으면 null.
         * 여러 명령이 섞이면 저장하지 않는 쪽이 이긴다 (취소 > 다시 > 등록).
         */
        fun parse(spoken: String?): VoiceCommand? {
            val text = spoken.orEmpty().filter { it.isLetterOrDigit() }
            if (text.isEmpty()) return null
            return when {
                cancelWords.any { it in text } -> CANCEL
                retryWords.any { it in text } -> RETRY
                registerWords.any { it in text } || text in registerExact -> REGISTER
                else -> null
            }
        }
    }
}
