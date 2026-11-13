package com.drama.app.authoring

import com.drama.app.core.Beat
import com.drama.app.core.Chapter
import com.drama.app.core.Choice
import com.drama.app.core.Persona
import com.drama.app.core.Reactions
import com.drama.app.core.RouteHint
import com.drama.app.core.Scenario
import com.drama.app.core.ScenarioSource

data class ChoiceDraft(val label: String, val reply: String, val detour: Boolean = false)
data class BeatDraft(val line: String, val choices: List<ChoiceDraft> = emptyList())
data class ChapterDraft(
    val title: String,
    val opening: String,
    val beats: List<BeatDraft>,
    val cliffhanger: String = "",
    val finale: String = "",
)
data class ReactionsDraft(
    val harshAccel: String = "",
    val harshBrake: String = "",
    val harshTurn: String = "",
    val congestion: String = "",
)
data class ScenarioDraft(
    val title: String,
    val premise: String,
    val personaName: String,
    val personaTone: String,
    val chapters: List<ChapterDraft>,
    val reactions: ReactionsDraft = ReactionsDraft(),
)

sealed interface DraftResult {
    data class Ok(val scenario: Scenario) : DraftResult
    data class Invalid(val issues: List<String>) : DraftResult
}

object Limits {
    const val TITLE = 40
    const val LINE = 120
    const val PREMISE = 500
    const val CHAPTERS = 20
    const val BEATS_PER_CHAPTER = 12
    const val CHOICES = 3
    const val CHOICE_LABEL = 12
}

fun parseScenarioDraft(draft: ScenarioDraft, id: String, source: ScenarioSource = ScenarioSource.CUSTOM): DraftResult {
    val issues = mutableListOf<String>()
    fun need(value: String, label: String, max: Int = Limits.LINE): String {
        val v = value.trim()
        if (v.isEmpty()) issues += "${label}을(를) 입력해 주세요."
        else if (v.length > max) issues += "${label}은(는) ${max}자 이하로 써 주세요."
        return v
    }

    val title = need(draft.title, "제목", Limits.TITLE)
    val premise = need(draft.premise, "세계관 설명", Limits.PREMISE)
    val name = need(draft.personaName, "캐릭터 이름", Limits.TITLE)
    val tone = need(draft.personaTone, "캐릭터 말투", Limits.TITLE)
    val r = draft.reactions
    val reactions = Reactions(
        harshAccel = r.harshAccel.trim().ifEmpty { "$name: 조금만 천천히 가요. 이야기는 도망가지 않아요." },
        harshBrake = r.harshBrake.trim().ifEmpty { "$name: 깜짝이야. 괜찮아요?" },
        harshTurn = r.harshTurn.trim().ifEmpty { "$name: 커브는 부드럽게 부탁해요." },
        congestion = r.congestion.trim().ifEmpty { "$name: 길이 막히네요. 그동안 이야기를 조금 더 해볼게요." },
    )

    if (draft.chapters.isEmpty()) issues += "챕터를 하나 이상 만들어 주세요."
    if (draft.chapters.size > Limits.CHAPTERS) issues += "챕터는 ${Limits.CHAPTERS}개까지 만들 수 있어요."

    val chapters = draft.chapters.take(Limits.CHAPTERS).mapIndexed { ci, c ->
        val at = "${ci + 1}장"
        if (c.beats.isEmpty()) issues += "${at}에 장면을 하나 이상 넣어 주세요."
        if (c.beats.size > Limits.BEATS_PER_CHAPTER) issues += "${at}의 장면은 ${Limits.BEATS_PER_CHAPTER}개까지예요."
        val chapterTitle = need(c.title, "$at 제목", Limits.TITLE)
        Chapter(
            id = "c${ci + 1}",
            title = chapterTitle,
            opening = need(c.opening, "$at 도입부"),
            beats = c.beats.take(Limits.BEATS_PER_CHAPTER).mapIndexed { bi, b ->
                val beatId = "c${ci + 1}b${bi + 1}"
                if (b.choices.size == 1) issues += "$at ${bi + 1}번째 장면의 선택지는 두 개 이상이어야 해요."
                if (b.choices.size > Limits.CHOICES) issues += "$at ${bi + 1}번째 장면의 선택지는 ${Limits.CHOICES}개까지예요."
                val choices = b.choices.take(Limits.CHOICES).mapIndexed { k, ch ->
                    Choice(
                        id = "${beatId}o${k + 1}",
                        label = need(ch.label, "$at 선택지 이름", Limits.CHOICE_LABEL),
                        reply = need(ch.reply, "$at 선택지 반응"),
                        routeHint = if (ch.detour) RouteHint.DETOUR else RouteHint.KEEP,
                    )
                }
                Beat(beatId, need(b.line, "$at ${bi + 1}번째 장면"), if (choices.size > 1) choices else emptyList())
            },
            cliffhanger = c.cliffhanger.trim().ifEmpty { "$name: 오늘은 여기까지. 다음 운전에서 이어갈게요." },
            finale = c.finale.trim().ifEmpty { "$name: $chapterTitle, 여기서 마무리예요." },
        )
    }

    return if (issues.isNotEmpty()) DraftResult.Invalid(issues)
    else DraftResult.Ok(Scenario(id, source, title, premise, Persona(name, tone, reactions), chapters))
}
