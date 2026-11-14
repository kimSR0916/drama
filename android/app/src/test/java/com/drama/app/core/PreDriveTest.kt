package com.drama.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreDriveTest {
    private fun run(vararg events: PreDriveEvent) = events.fold(PreDriveState.Selecting as PreDriveState, ::preDrive)
    private val midSave = SaveData("s1", Cursor(0, 2), mapOf("s2" to "DETOUR"), 40, 1)

    @Test fun shortTripPlaysOnlyTheFirstCoreScene() {
        val plan = planDrive(fixture, Cursor.START, 8.0)!!
        assertEquals(PacingMode.SHORT, plan.mode)
        assertEquals(listOf("s2"), plan.beats.map { it.beat.id })
    }

    @Test fun longTripPlaysCoreAndSubScenesInOrder() {
        val plan = planDrive(fixture, Cursor.START, 32.0)!!
        assertEquals(listOf("s1", "s2", "s3", "s4"), plan.beats.map { it.beat.id })
        assertEquals("출발하지.", plan.opening)
        assertNull(planDrive(fixture, Cursor(2, 0), 30.0))
    }

    @Test fun resumeOpensWithRecapAndKeepsBond() {
        val s = run(PreDriveEvent.SelectStory(fixture, midSave), PreDriveEvent.Answer(true, 5), PreDriveEvent.SetDestination(25.0))
        val ready = s as PreDriveState.Ready
        assertEquals("지난 이야기: 성문을 나섰다.", ready.plan.opening)
        assertEquals(listOf("s3", "s4"), ready.plan.beats.map { it.beat.id })
        assertEquals(40, ready.save.bond)
    }

    @Test fun restartClearsProgressButKeepsBondAndDecliningBothReturnsToSelect() {
        val restarted = run(PreDriveEvent.SelectStory(fixture, midSave), PreDriveEvent.Answer(false, 5), PreDriveEvent.Answer(true, 7))
        assertEquals(SaveData("s1", Cursor.START, emptyMap(), 40, 7), (restarted as PreDriveState.AwaitingDestination).save)
        val declined = run(PreDriveEvent.SelectStory(fixture, midSave), PreDriveEvent.Answer(false, 5), PreDriveEvent.Answer(false, 6))
        assertEquals(PreDriveState.Selecting, declined)
    }

    @Test fun finishedForeignOrStaleSaveCountsAsNoHistory() {
        listOf(midSave.copy(cursor = Cursor(2, 0)), midSave.copy(scenarioId = "x"), midSave.copy(cursor = Cursor(0, 9))).forEach {
            assertTrue(run(PreDriveEvent.SelectStory(fixture, it)) is PreDriveState.OfferNew)
        }
    }
}
