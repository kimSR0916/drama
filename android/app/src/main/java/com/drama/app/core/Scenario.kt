package com.drama.app.core

enum class ScenarioSource { CUSTOM, BUILTIN }

enum class RouteHint { DETOUR, KEEP }

data class Reactions(
    val harshAccel: String,
    val harshBrake: String,
    val harshTurn: String,
    val congestion: String,
)

data class Persona(val name: String, val tone: String, val reactions: Reactions)

data class Choice(val id: String, val label: String, val reply: String, val routeHint: RouteHint? = null)

data class Beat(val id: String, val line: String, val choices: List<Choice> = emptyList())

data class Chapter(
    val id: String,
    val title: String,
    val opening: String,
    val beats: List<Beat>,
    val cliffhanger: String,
    val finale: String,
)

data class Scenario(
    val id: String,
    val source: ScenarioSource,
    val title: String,
    val premise: String,
    val persona: Persona,
    val chapters: List<Chapter>,
) {
    val totalBeats: Int get() = chapters.sumOf { it.beats.size }

    fun beatsBefore(cursor: Cursor): Int =
        chapters.take(cursor.chapter).sumOf { it.beats.size } + cursor.beat

    fun isFinished(cursor: Cursor): Boolean = cursor.chapter >= chapters.size

    fun fits(cursor: Cursor): Boolean {
        val chapter = chapters.getOrNull(cursor.chapter) ?: return false
        return cursor.beat < chapter.beats.size
    }

    fun advance(cursor: Cursor, played: Int): Cursor {
        val chapter = chapters.getOrNull(cursor.chapter) ?: return cursor
        val beat = cursor.beat + played
        return if (beat >= chapter.beats.size) Cursor(cursor.chapter + 1, 0) else Cursor(cursor.chapter, beat)
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
    val updatedAt: Long,
)
