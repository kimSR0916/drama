package com.drama.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreDriveTest {
    private fun run(vararg events: PreDriveEvent) = events.fold(PreDriveState.Selecting as PreDriveState, ::preDrive)
    private val midSave = SaveData("s1", Cursor(0, 1), mapOf("b1" to "fast"), 1)

    @Test fun shortTripCompressesToOneEventAndCliffhanger() {
        val plan = planDrive(fixture, Cursor.START, 9.0)!!
        assertEquals(PacingMode.SHORT, plan.mode)
        assertEquals(listOf("b1"), plan.beats.map { it.id })
        assertEquals("다음에 계속.", plan.ending)
    }

    @Test fun longTripPlaysRemainingChapter() {
        val plan = planDrive(fixture, Cursor.START, 10.0)!!
        assertEquals(listOf("b1", "b2", "b3"), plan.beats.map { it.id })
        assertEquals("1장 해결!", plan.ending)
        assertNull(planDrive(fixture, Cursor(2, 0), 30.0))
    }

    @Test fun newStartWithoutHistory() {
        val s = run(PreDriveEvent.SelectStory(fixture, null), PreDriveEvent.StartNew(5), PreDriveEvent.SetDestination(25.0))
        assertTrue(s is PreDriveState.Ready)
        assertEquals(Cursor.START, (s as PreDriveState.Ready).save.cursor)
    }

    @Test fun resumeKeepsCursor() {
        val s = run(PreDriveEvent.SelectStory(fixture, midSave), PreDriveEvent.Answer(true, 5), PreDriveEvent.SetDestination(25.0))
        assertEquals(listOf("b2", "b3"), (s as PreDriveState.Ready).plan.beats.map { it.id })
    }

    @Test fun restartClearsAndDecliningBothReturnsToSelect() {
        val restarted = run(PreDriveEvent.SelectStory(fixture, midSave), PreDriveEvent.Answer(false, 5), PreDriveEvent.Answer(true, 7))
        assertEquals(SaveData("s1", Cursor.START, emptyMap(), 7), (restarted as PreDriveState.AwaitingDestination).save)
        val declined = run(PreDriveEvent.SelectStory(fixture, midSave), PreDriveEvent.Answer(false, 5), PreDriveEvent.Answer(false, 6))
        assertEquals(PreDriveState.Selecting, declined)
    }

    @Test fun finishedForeignOrStaleSaveCountsAsNoHistory() {
        listOf(midSave.copy(cursor = Cursor(2, 0)), midSave.copy(scenarioId = "x"), midSave.copy(cursor = Cursor(0, 9))).forEach {
            assertTrue(run(PreDriveEvent.SelectStory(fixture, it)) is PreDriveState.OfferNew)
        }
    }
}
