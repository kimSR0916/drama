package com.drama.app.core

enum class ScenarioSource { CUSTOM, BUILTIN }

enum class RouteHint { DETOUR, KEEP }

enum class SceneKind { CORE, SUB }

data class Reactions(
    val harshAccel: String,
    val harshBrake: String,
    val harshTurn: String,
    val congestion: String,
)

data class Persona(
    val name: String,
    val tone: String,
    val icon: String,
    val reactions: Reactions,
    val bondTitles: List<String>,
)

data class Option(val label: String, val keyword: String, val reply: String)

data class Branch(val detour: Option, val keep: Option, val timeoutLine: String) {
    fun option(hint: RouteHint) = if (hint == RouteHint.DETOUR) detour else keep
}

data class Beat(val id: String, val kind: SceneKind, val line: String, val branch: Branch? = null)

data class Chapter(
    val id: String,
    val title: String,
    val opening: String,
    val recap: String,
    val beats: List<Beat>,
    val cliffhanger: String,
    val finale: String,
    val reward: String,
)

data class Scenario(
    val id: String,
    val source: ScenarioSource,
    val genre: String,
    val title: String,
    val premise: String,
    val persona: Persona,
    val chapters: List<Chapter>,
) {
    val totalBeats: Int get() = chapters.sumOf { it.beats.size }

    fun beatsBefore(cursor: Cursor): Int =
        chapters.take(cursor.chapter).sumOf { it.beats.size } + cursor.beat

    fun progressPercent(cursor: Cursor): Int =
        if (totalBeats == 0) 100 else Math.round(beatsBefore(cursor).coerceAtMost(totalBeats) * 100f / totalBeats)

    fun fits(cursor: Cursor): Boolean {
        val chapter = chapters.getOrNull(cursor.chapter) ?: return false
        return cursor.beat in chapter.beats.indices
    }

    fun after(cursor: Cursor, lastPlayedIndex: Int?): Cursor {
        val chapter = chapters.getOrNull(cursor.chapter) ?: return cursor
        val next = (lastPlayedIndex ?: return cursor) + 1
        return if (next >= chapter.beats.size) Cursor(cursor.chapter + 1, 0) else Cursor(cursor.chapter, next)
    }
}

data class Cursor(val chapter: Int, val beat: Int) {
    companion object {
        val START = Cursor(0, 0)
    }
}

data class SaveData(
    val scenarioId: String,
    val cursor: Cursor,
    val flags: Map<String, String>,
    val bond: Int,
    val updatedAt: Long,
)
