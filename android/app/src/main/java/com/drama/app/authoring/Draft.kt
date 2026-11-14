package com.drama.app.authoring

import com.drama.app.core.Beat
import com.drama.app.core.Branch
import com.drama.app.core.Chapter
import com.drama.app.core.Option
import com.drama.app.core.Persona
import com.drama.app.core.Reactions
import com.drama.app.core.Scenario
import com.drama.app.core.ScenarioSource
import com.drama.app.core.SceneKind
import org.json.JSONArray
import org.json.JSONObject

sealed interface DraftResult {
    data class Ok(val scenario: Scenario) : DraftResult
    data class Invalid(val issues: List<String>) : DraftResult
}

object Limits {
    const val TITLE = 40
    const val SPOKEN_LINE = 80
    const val PREMISE = 500
    const val CHAPTERS = 20
    const val SCENES_PER_CHAPTER = 12
    const val LABEL = 14
}

private class Reader(val issues: MutableList<String>) {
    fun text(o: JSONObject?, key: String, label: String, max: Int = Limits.SPOKEN_LINE, fallback: String? = null): String {
        val v = o?.optString(key, "")?.trim().orEmpty().ifEmpty { fallback.orEmpty() }
        if (v.isEmpty()) issues += "${label}을(를) 입력해 주세요."
        else if (v.length > max) issues += "${label}은(는) ${max}자 이하로 써 주세요. (지금 ${v.length}자)"
        return v
    }

    fun objects(o: JSONObject?, key: String): List<JSONObject> {
        val a: JSONArray = o?.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
}

private fun keywordIn(line: String, keyword: String) = line.replace(" ", "").contains(keyword.replace(" ", ""))

fun parseScenarioJson(o: JSONObject?, id: String, source: ScenarioSource): DraftResult {
    val issues = mutableListOf<String>()
    val r = Reader(issues)
    val title = r.text(o, "title", "제목", Limits.TITLE)
    val premise = r.text(o, "premise", "세계관 설명", Limits.PREMISE)
    val genre = o?.optString("genre", "").orEmpty().trim().ifEmpty { "커스텀" }
    val p = o?.optJSONObject("persona")
    val name = r.text(p, "name", "캐릭터 이름", Limits.TITLE)
    val tone = r.text(p, "tone", "캐릭터 말투", Limits.TITLE)
    val icon = p?.optString("icon", "")?.trim().orEmpty().ifEmpty { name.take(1) }
    val re = p?.optJSONObject("reactions")
    val reactions = Reactions(
        harshAccel = r.text(re, "harshAccel", "급가속 반응", fallback = "조금만 천천히 가요. 이야기는 도망가지 않아요."),
        harshBrake = r.text(re, "harshBrake", "급제동 반응", fallback = "깜짝이야. 괜찮아요? 앞을 잘 살펴 주세요."),
        harshTurn = r.text(re, "harshTurn", "거친 조향 반응", fallback = "커브는 부드럽게 부탁해요."),
        congestion = r.text(re, "congestion", "정체 반응", fallback = "길이 막히네요. 초조해하지 말고 천천히 가요."),
    )
    val titles = p?.optJSONArray("bondTitles")?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } }
        .orEmpty().ifEmpty { listOf("낯선 동행", "믿음직한 파트너", "둘도 없는 단짝") }

    val rawChapters = r.objects(o, "chapters")
    if (rawChapters.isEmpty()) issues += "챕터를 하나 이상 만들어 주세요."
    if (rawChapters.size > Limits.CHAPTERS) issues += "챕터는 ${Limits.CHAPTERS}개까지 만들 수 있어요."

    val chapters = rawChapters.take(Limits.CHAPTERS).mapIndexed { ci, c ->
        val at = "${ci + 1}장"
        val chapterTitle = r.text(c, "title", "$at 제목", Limits.TITLE)
        val scenes = r.objects(c, "scenes")
        if (scenes.isEmpty()) issues += "${at}에 장면을 하나 이상 넣어 주세요."
        if (scenes.size > Limits.SCENES_PER_CHAPTER) issues += "${at}의 장면은 ${Limits.SCENES_PER_CHAPTER}개까지예요."
        val beats = scenes.take(Limits.SCENES_PER_CHAPTER).mapIndexed { bi, s ->
            val where = "$at ${bi + 1}번째 장면"
            val line = r.text(s, "line", where)
            val branch = s.optJSONObject("detour")?.let { d ->
                val k = s.optJSONObject("keep")
                if (k == null) issues += "$where: 우회(A)와 유지(B) 선택지를 모두 써 주세요."
                fun option(x: JSONObject?, label: String): Option {
                    val keyword = r.text(x, "keyword", "$where $label 키워드", Limits.LABEL)
                    if (keyword.isNotEmpty() && !keywordIn(line, keyword)) issues += "$where: 질문 대사에 '$keyword'가 들어가야 음성으로 답할 수 있어요."
                    return Option(r.text(x, "label", "$where $label 버튼 문구", Limits.TITLE), keyword, r.text(x, "reply", "$where $label 결과 대사"))
                }
                Branch(option(d, "A(우회)"), option(k, "B(유지)"), r.text(s, "timeout", "$where 무응답 대사"))
            }
            Beat("c${ci + 1}s${bi + 1}", if (s.optBoolean("core", false)) SceneKind.CORE else SceneKind.SUB, line, branch)
        }
        if (beats.isNotEmpty() && beats.none { it.kind == SceneKind.CORE }) issues += "${at}에는 핵심(Core) 장면이 하나 이상 있어야 단거리 주행에서도 이야기가 진행돼요."
        Chapter(
            id = "c${ci + 1}",
            title = chapterTitle,
            opening = r.text(c, "opening", "$at 도입 대사"),
            recap = r.text(c, "recap", "$at 지난 이야기 요약", fallback = "지난 이야기에 이어서, $chapterTitle."),
            beats = beats,
            cliffhanger = r.text(c, "cliffhanger", "$at 소결말 대사", fallback = "오늘은 여기까지. 다음 운전에서 이어갈게요."),
            finale = r.text(c, "finale", "$at 결말 대사", fallback = "$chapterTitle, 여기서 마무리예요."),
            reward = r.text(c, "reward", "$at 보상 카드 이름", Limits.TITLE, fallback = "$chapterTitle 기념 카드"),
        )
    }

    return if (issues.isNotEmpty()) DraftResult.Invalid(issues)
    else DraftResult.Ok(Scenario(id, source, genre, title, premise, Persona(name, tone, icon, reactions, titles), chapters))
}

fun parseBuiltins(json: String): List<Scenario> {
    val a = JSONArray(json)
    return (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        val id = o.getString("id")
        when (val r = parseScenarioJson(o, id, ScenarioSource.BUILTIN)) {
            is DraftResult.Ok -> r.scenario
            is DraftResult.Invalid -> error("invalid builtin $id: ${r.issues}")
        }
    }
}

fun customDraftJson(title: String, premise: String, name: String, tone: String, chapterTitle: String, opening: String, lines: List<String>): JSONObject =
    JSONObject()
        .put("title", title).put("premise", premise)
        .put("persona", JSONObject().put("name", name).put("tone", tone))
        .put(
            "chapters",
            JSONArray().put(
                JSONObject().put("title", chapterTitle).put("opening", opening)
                    .put("scenes", JSONArray(lines.mapIndexed { i, l -> JSONObject().put("line", l).put("core", i == 0) })),
            ),
        )
