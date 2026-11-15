package com.drama.app.ai

import com.drama.app.authoring.Limits

private val unsafe = listOf(
    Regex("(속도|속력).{0,4}(올려|높여|내봐|내라|내요|내\\s?주|내자|내죠|올리자|올리죠|올립시다|높이자|높이죠)"),
    Regex("(액셀|악셀|엑셀|가속\\s?페달).{0,6}밟|풀악셀|풀액셀"),
    Regex("(세게|더|끝까지|꽉)\\s*밟(아|으세요|자|읍시다|으라)"),
    Regex("더\\s*빨리|빨리\\s*(달려|달리|가요|가자|가세요|가 주|밟)|서둘러"),
    Regex("(신호|빨간불|정지선).{0,8}(무시|그냥\\s*(가|지나)|어겨)"),
    Regex("제치고|제쳐|앞질러|앞지르자|칼치기|역주행|끼어들어|추월(해|하자|합시다|하세요)"),
    Regex("눈(을|도)?\\s*(감아|감으세요|감고)"),
    Regex("(백미러|사이드미러|거울|전방|앞).{0,4}(보지\\s*마|보지\\s*말|안\\s*봐도)"),
    Regex("(휴대폰|핸드폰|폰|화면|앱|지도|문자|메시지).{0,8}(봐|보세요|확인|터치|눌러|열어)"),
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
