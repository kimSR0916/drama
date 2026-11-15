package com.drama.app.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import java.time.Duration

const val CLAUDE_MODEL = "claude-opus-5-5"
private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

/** One attempt per call: a retry after a timeout would bill a long generation twice. */
class ClaudeLlm(apiKey: String, baseUrl: String?, timeout: Duration) : Llm {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { if (!baseUrl.isNullOrBlank()) baseUrl(baseUrl) }
        .timeout(timeout)
        .maxRetries(0)
        .build()

    override fun complete(system: String, turns: List<Turn>, effort: Effort, maxTokens: Long): LlmResult {
        val params = MessageCreateParams.builder()
            .model(CLAUDE_MODEL)
            .maxTokens(maxTokens)
            .system(system)
            .addBeta(FALLBACK_BETA)
            .fallbacksDefault()
            .outputConfig(
                BetaOutputConfig.builder().effort(
                    when (effort) {
                        Effort.LOW -> BetaOutputConfig.Effort.LOW
                        Effort.MEDIUM -> BetaOutputConfig.Effort.MEDIUM
                        Effort.HIGH -> BetaOutputConfig.Effort.HIGH
                    },
                ).build(),
            )
            .apply {
                turns.forEach { if (it.role == Role.USER) addUserMessage(it.text) else addAssistantMessage(it.text) }
            }
            .build()
        return try {
            val message = client.beta().messages().create(params)
            val reason = message.stopReason().orElse(null)
            val text = message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("").trim()
            when {
                reason == BetaStopReason.REFUSAL -> LlmResult.Refused
                reason == BetaStopReason.MAX_TOKENS -> LlmResult.Failed("응답이 길이 제한에 걸렸습니다.")
                text.isEmpty() -> LlmResult.Failed("빈 응답을 받았습니다.")
                else -> LlmResult.Text(text)
            }
        } catch (e: UnauthorizedException) {
            LlmResult.Failed("API 키가 올바르지 않습니다.")
        } catch (e: RateLimitException) {
            LlmResult.Failed("요청이 많아 잠시 후 다시 시도해야 합니다.")
        } catch (e: AnthropicServiceException) {
            LlmResult.Failed("AI 서버 오류 (${e.statusCode()})")
        } catch (e: AnthropicIoException) {
            LlmResult.Failed("네트워크 오류이거나 응답이 너무 오래 걸렸습니다.")
        } catch (e: AnthropicException) {
            LlmResult.Failed("AI 응답을 처리하지 못했습니다.")
        }
    }
}
