package com.drama.app.ai

import com.drama.app.authoring.Limits

private val unsafe = listOf(
    Regex("속도.{0,4}(올려|높여|내봐|내라|올리자|올리죠|올립시다|높이자|높이죠)"),
    Regex("(세게|더|꽉|끝까지)\\s*밟"),
    Regex("신호.{0,6}무시"),
    Regex("(추월|끼어들|칼치기|역주행)"),
    Regex("눈.{0,4}감"),
    Regex("(백미러|사이드미러|거울|앞).{0,8}(보지|마세요|말고)"),
    Regex("(휴대폰|핸드폰|폰|화면).{0,8}(봐|보세요|확인해|터치|눌러)"),
    Regex("(핸들|운전대).{0,6}(놓|떼)"),
)

private val routePromise = Regex("(경로|길).{0,6}(바꿀게|바꿨|재탐색)|우회할게|돌아갈게")

/** Returns the cleaned line when it is safe to speak while driving, or null. */
fun guardLine(raw: String): String? {
    val line = raw.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.trim('"', '“', '”', '\'')?.trim() ?: return null
    if (line.isEmpty() || line.length > Limits.SPOKEN_LINE) return null
    if (unsafe.any { it.containsMatchIn(line) } || routePromise.containsMatchIn(line)) return null
    return line
}

fun unsafeLines(lines: List<String>): List<String> = lines.filter { l -> unsafe.any { it.containsMatchIn(l) } }
