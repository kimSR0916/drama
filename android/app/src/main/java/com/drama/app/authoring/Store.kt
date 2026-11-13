package com.drama.app.authoring

import com.drama.app.core.Beat
import com.drama.app.core.Chapter
import com.drama.app.core.Choice
import com.drama.app.core.Cursor
import com.drama.app.core.Persona
import com.drama.app.core.Reactions
import com.drama.app.core.RouteHint
import com.drama.app.core.SaveData
import com.drama.app.core.Scenario
import com.drama.app.core.ScenarioSource
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

    fun listScenarios(): List<Scenario> =
        kv.keys().filter { it.startsWith("$accountId/scenario/") }.sorted().mapNotNull { read(it, ::decodeScenario) }

    fun getScenario(id: String): Scenario? = read(scenarioKey(id), ::decodeScenario)
    fun putScenario(s: Scenario) = kv.set(scenarioKey(s.id), encodeScenario(s).toString())
    fun deleteScenario(id: String) {
        kv.remove(scenarioKey(id))
        kv.remove(saveKey(id))
    }

    fun getSave(scenarioId: String): SaveData? = read(saveKey(scenarioId), ::decodeSave)
    fun putSave(save: SaveData) = kv.set(saveKey(save.scenarioId), encodeSave(save).toString())

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
    .put("updatedAt", s.updatedAt)

fun decodeSave(o: JSONObject): SaveData {
    val flags = o.getJSONObject("flags")
    return SaveData(
        scenarioId = o.getString("scenarioId"),
        cursor = Cursor(o.getInt("chapter"), o.getInt("beat")),
        flags = flags.keys().asSequence().associateWith { flags.getString(it) },
        updatedAt = o.getLong("updatedAt"),
    )
}

fun encodeScenario(s: Scenario): JSONObject = JSONObject()
    .put("id", s.id)
    .put("source", s.source.name)
    .put("title", s.title)
    .put("premise", s.premise)
    .put(
        "persona",
        JSONObject().put("name", s.persona.name).put("tone", s.persona.tone).put(
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
            .put("id", c.id).put("title", c.title).put("opening", c.opening)
            .put("cliffhanger", c.cliffhanger).put("finale", c.finale)
            .put("beats", JSONArray(c.beats.map { b ->
                JSONObject().put("id", b.id).put("line", b.line).put("choices", JSONArray(b.choices.map { ch ->
                    JSONObject().put("id", ch.id).put("label", ch.label).put("reply", ch.reply)
                        .put("routeHint", ch.routeHint?.name ?: JSONObject.NULL)
                }))
            }))
    }))

fun decodeScenario(o: JSONObject): Scenario {
    val p = o.getJSONObject("persona")
    val r = p.getJSONObject("reactions")
    return Scenario(
        id = o.getString("id"),
        source = ScenarioSource.valueOf(o.getString("source")),
        title = o.getString("title"),
        premise = o.getString("premise"),
        persona = Persona(
            p.getString("name"),
            p.getString("tone"),
            Reactions(r.getString("harshAccel"), r.getString("harshBrake"), r.getString("harshTurn"), r.getString("congestion")),
        ),
        chapters = o.getJSONArray("chapters").objects().map { c ->
            Chapter(
                id = c.getString("id"),
                title = c.getString("title"),
                opening = c.getString("opening"),
                beats = c.getJSONArray("beats").objects().map { b ->
                    Beat(b.getString("id"), b.getString("line"), b.getJSONArray("choices").objects().map { ch ->
                        Choice(
                            ch.getString("id"),
                            ch.getString("label"),
                            ch.getString("reply"),
                            if (ch.isNull("routeHint")) null else RouteHint.valueOf(ch.getString("routeHint")),
                        )
                    })
                },
                cliffhanger = c.getString("cliffhanger"),
                finale = c.getString("finale"),
            )
        },
    )
}
