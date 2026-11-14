package com.drama.app.authoring

import com.drama.app.core.Cursor
import com.drama.app.core.Option
import com.drama.app.core.SaveData
import com.drama.app.core.Scenario
import com.drama.app.core.ScenarioSource
import com.drama.app.core.SceneKind
import com.drama.app.core.Speaker
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

interface KeyValue {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
    fun keys(): Set<String>
}

class MemoryKeyValue : KeyValue {
    private val map = linkedMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun set(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    override fun keys(): Set<String> = map.keys.toSet()
}

class AccountStore(private val kv: KeyValue, private val accountId: String) {
    private fun scenarioKey(id: String) = "$accountId/scenario/$id"
    private fun saveKey(id: String) = "$accountId/save/$id"
    private fun logKey(id: String) = "$accountId/log/$id"

    fun listScenarios(): List<Scenario> =
        kv.keys().filter { it.startsWith("$accountId/scenario/") }.sorted().mapNotNull { read(it, ::decodeScenario) }

    fun getScenario(id: String): Scenario? = read(scenarioKey(id), ::decodeScenario)
    fun putScenario(s: Scenario) = kv.set(scenarioKey(s.id), encodeScenario(s).toString())
    fun deleteScenario(id: String) {
        kv.remove(scenarioKey(id))
        kv.remove(saveKey(id))
        kv.remove(logKey(id))
    }

    fun getSave(scenarioId: String): SaveData? = read(saveKey(scenarioId), ::decodeSave)
    fun putSave(save: SaveData) = kv.set(saveKey(save.scenarioId), encodeSave(save).toString())

    fun getLog(scenarioId: String): List<LogEntry> = read(logKey(scenarioId), ::decodeLog).orEmpty()
    fun putLog(scenarioId: String, entries: List<LogEntry>) = kv.set(logKey(scenarioId), encodeLog(entries).toString())

    private fun <T> read(key: String, decode: (JSONObject) -> T): T? {
        val raw = kv.get(key) ?: return null
        return try {
            decode(JSONObject(raw))
        } catch (_: JSONException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

fun encodeSave(s: SaveData): JSONObject = JSONObject()
    .put("scenarioId", s.scenarioId)
    .put("chapter", s.cursor.chapter)
    .put("beat", s.cursor.beat)
    .put("flags", JSONObject(s.flags))
    .put("bond", s.bond)
    .put("updatedAt", s.updatedAt)

fun decodeSave(o: JSONObject): SaveData {
    val flags = o.getJSONObject("flags")
    return SaveData(
        scenarioId = o.getString("scenarioId"),
        cursor = Cursor(o.getInt("chapter"), o.getInt("beat")),
        flags = flags.keys().asSequence().associateWith { flags.getString(it) },
        bond = o.optInt("bond", 0),
        updatedAt = o.getLong("updatedAt"),
    )
}

private fun option(o: Option) = JSONObject().put("label", o.label).put("keyword", o.keyword).put("reply", o.reply)

fun encodeScenario(s: Scenario): JSONObject = JSONObject()
    .put("id", s.id)
    .put("source", s.source.name)
    .put("genre", s.genre)
    .put("title", s.title)
    .put("premise", s.premise)
    .put(
        "persona",
        JSONObject().put("name", s.persona.name).put("tone", s.persona.tone).put("icon", s.persona.icon)
            .put("bondTitles", JSONArray(s.persona.bondTitles))
            .put(
                "reactions",
                JSONObject()
                    .put("harshAccel", s.persona.reactions.harshAccel)
                    .put("harshBrake", s.persona.reactions.harshBrake)
                    .put("harshTurn", s.persona.reactions.harshTurn)
                    .put("congestion", s.persona.reactions.congestion),
            ),
    )
    .put("chapters", JSONArray(s.chapters.map { c ->
        JSONObject()
            .put("title", c.title).put("opening", c.opening).put("recap", c.recap)
            .put("cliffhanger", c.cliffhanger).put("finale", c.finale).put("reward", c.reward)
            .put("scenes", JSONArray(c.beats.map { b ->
                JSONObject().put("line", b.line).put("core", b.kind == SceneKind.CORE).apply {
                    b.branch?.let { put("detour", option(it.detour)).put("keep", option(it.keep)).put("timeout", it.timeoutLine) }
                }
            }))
    }))

fun decodeScenario(o: JSONObject): Scenario {
    val source = ScenarioSource.valueOf(o.getString("source"))
    return when (val r = parseScenarioJson(o, o.getString("id"), source)) {
        is DraftResult.Ok -> r.scenario
        is DraftResult.Invalid -> throw IllegalArgumentException(r.issues.joinToString())
    }
}

data class LogEntry(val speaker: Speaker, val text: String)

const val LOG_LIMIT = 40

fun encodeLog(entries: List<LogEntry>): JSONObject =
    JSONObject().put("entries", JSONArray(entries.takeLast(LOG_LIMIT).map { JSONObject().put("speaker", it.speaker.name).put("text", it.text) }))

fun decodeLog(o: JSONObject): List<LogEntry> =
    o.getJSONArray("entries").objects().map { LogEntry(Speaker.valueOf(it.getString("speaker")), it.getString("text")) }
