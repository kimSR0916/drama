package com.drama.app.ai

enum class Effort { LOW, MEDIUM, HIGH }

enum class Role { USER, ASSISTANT }

data class Turn(val role: Role, val text: String)

sealed interface LlmResult {
    data class Text(val text: String) : LlmResult
    data object Refused : LlmResult
    data class Failed(val reason: String) : LlmResult
}

/** Blocking; call off the main thread. */
fun interface Llm {
    fun complete(system: String, turns: List<Turn>, effort: Effort, maxTokens: Long): LlmResult
}
