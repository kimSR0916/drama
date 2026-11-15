package com.drama.app.ai

import com.drama.app.core.Slot

/**
 * Keeps generated lines ready before the engine needs them, so speech never waits on the network.
 * All methods run on the caller's thread; `background` runs the blocking call and `deliver` returns to that thread.
 */
class AiDirector(
    private val background: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) {
    var writer: LineWriter? = null
        set(value) {
            field = value
            reset()
        }

    private val ready = mutableMapOf<Slot, String>()
    private val inFlight = mutableSetOf<Slot>()
    private var generation = 0

    fun take(slot: Slot): String? = ready.remove(slot)

    fun prepare(context: DriveContext, wanted: Map<Slot, String>) {
        val w = writer ?: return
        for ((slot, scripted) in wanted) {
            if (slot in ready || slot in inFlight) continue
            inFlight += slot
            val started = generation
            background {
                val line = runCatching { w.forSlot(context, slot, scripted) }.getOrNull()
                deliver {
                    if (started == generation) {
                        inFlight -= slot
                        if (line != null) ready[slot] = line
                    }
                }
            }
        }
    }

    fun reset() {
        generation++
        ready.clear()
        inFlight.clear()
    }
}
