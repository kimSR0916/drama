package com.drama.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = 60_000L

class DriveTest {
    private fun begin(eta: Double, scenario: Scenario = fixture): Step {
        val ready = listOf(PreDriveEvent.SelectStory(scenario, null), PreDriveEvent.StartNew(0), PreDriveEvent.SetDestination(eta))
            .fold(PreDriveState.Selecting as PreDriveState, ::preDrive) as PreDriveState.Ready
        return startDrive(ready, 0)
    }

    private fun feed(start: Step, vararg events: DriveEvent): Pair<DriveState, List<Effect>> {
        var state = start.state
        val effects = start.effects.toMutableList()
        for (e in events) {
            val step = drive(state, e)
            state = step.state
            effects += step.effects
        }
        return state to effects
    }

    private fun spoken(effects: List<Effect>) = effects.filterIsInstance<Effect.Speak>().map { it.text }

    @Test fun startLocksUiAndOpens() {
        assertEquals(
            listOf(Effect.UxRestrictions(true), Effect.Bgm(true), Effect.Speak(Speaker.NARRATOR, "첫 번째 의뢰가 들어왔어요.")),
            begin(30.0).effects,
        )
    }

    @Test fun beatsSpreadAndChoiceShows() {
        val (state, effects) = feed(begin(41.0), DriveEvent.Tick(5 * MIN, 36.0), DriveEvent.Tick(10 * MIN, 31.0))
        assertTrue("편지가 사라졌대요." in spoken(effects))
        assertTrue(effects.any { it is Effect.ShowChoices })
        assertEquals(1, (state as DriveState.Driving).trip.played)
    }

    @Test fun driverChoiceReroutesAndTimeoutKeepsRoute() {
        val (_, picked) = feed(begin(41.0), DriveEvent.Tick(10 * MIN, 31.0), DriveEvent.DriverInput("fast", InputVia.VOICE, 10 * MIN + 1))
        assertTrue(Effect.Reroute(RouteHint.DETOUR) in picked)
        val (_, silent) = feed(begin(41.0), DriveEvent.Tick(10 * MIN, 31.0), DriveEvent.Tick(10 * MIN + 31_000, 30.0))
        assertTrue(Effect.Reroute(RouteHint.KEEP) in silent)
    }

    @Test fun harshReactionCooldownButEveryEventCounts() {
        val (state, effects) = feed(
            begin(30.0),
            DriveEvent.HarshDriving(HarshKind.BRAKE, 1000),
            DriveEvent.HarshDriving(HarshKind.BRAKE, 2000),
            DriveEvent.HarshDriving(HarshKind.ACCEL, 40_000),
        )
        assertEquals(2, spoken(effects).count { "천천히" in it || "서두르면" in it })
        assertEquals(2, (state as DriveState.Driving).trip.harsh[HarshKind.BRAKE])
    }

    @Test fun congestionPullsNextBeatForward() {
        val (_, effects) = feed(begin(30.0), DriveEvent.Congestion(1000))
        assertTrue(spoken(effects).containsAll(listOf("막히는 동안 단서를 정리해 볼까요?", "편지가 사라졌대요.")))
    }

    @Test fun smartSaveThenReportOnPark() {
        val (state, effects) = feed(
            begin(8.0),
            DriveEvent.Tick(4 * MIN, 4.0),
            DriveEvent.DriverInput("safe", InputVia.BUTTON, 4 * MIN + 1),
            DriveEvent.Tick(7 * MIN, 1.0),
            DriveEvent.GearChanged(Gear.D, 7 * MIN + 30_000),
            DriveEvent.GearChanged(Gear.P, 8 * MIN),
        )
        assertTrue(Effect.Save(SaveData("s1", Cursor(0, 1), mapOf("b1" to "safe"), 7 * MIN)) in effects)
        assertEquals(Effect.UxRestrictions(false), effects[effects.size - 2])
        val report = (state as DriveState.Parked).report
        assertEquals(100, report.safetyScore)
        assertEquals(25, report.progressPercent)
        assertEquals(RewardKind.SAFETY, report.reward?.kind)
    }

    @Test fun chapterRewardWhenChapterFinishes() {
        val (state, _) = feed(
            begin(30.0),
            DriveEvent.Congestion(MIN),
            DriveEvent.DriverInput("safe", InputVia.VOICE, MIN + 1),
            DriveEvent.Congestion(2 * MIN),
            DriveEvent.Congestion(3 * MIN),
            DriveEvent.GearChanged(Gear.P, 4 * MIN),
        )
        assertEquals(RewardCard(RewardKind.CHAPTER, "1장 사라진 편지 클리어"), (state as DriveState.Parked).report.reward)
    }

    @Test fun harshAfterArrivalStillCounts() {
        val (state, _) = feed(
            begin(15.0),
            DriveEvent.Tick(14 * MIN, 1.0),
            DriveEvent.HarshDriving(HarshKind.BRAKE, 14 * MIN + 10_000),
            DriveEvent.HarshDriving(HarshKind.BRAKE, 14 * MIN + 50_000),
            DriveEvent.GearChanged(Gear.P, 15 * MIN),
        )
        assertEquals(90, (state as DriveState.Parked).report.safetyScore)
    }

    @Test fun sosStopsEverythingSavesAndIgnoresLaterEvents() {
        val (state, effects) = feed(
            begin(30.0),
            DriveEvent.Congestion(1000),
            DriveEvent.Emergency(EmergencyCause.AIRBAG, 2000),
            DriveEvent.GearChanged(Gear.P, 4000),
        )
        assertEquals(
            listOf(Effect.StopAllAudio, Effect.Sos(EmergencyCause.AIRBAG), Effect.Save(SaveData("s1", Cursor(0, 1), emptyMap(), 2000))),
            effects.takeLast(3),
        )
        assertEquals(DriveState.Emergency(EmergencyCause.AIRBAG), state)
    }

    @Test fun neverAutoDetours() {
        val allDetour = fixture.copy(chapters = listOf(fixture.chapters[0].copy(beats = listOf(Beat("d", "갈림길", listOf(
            Choice("l", "왼쪽", "왼쪽!", RouteHint.DETOUR),
            Choice("r", "오른쪽", "오른쪽!", RouteHint.DETOUR),
        ))))))
        val (_, effects) = feed(begin(30.0, allDetour), DriveEvent.Congestion(1000), DriveEvent.Tick(31_000, 29.0))
        assertTrue(Effect.HideChoices in effects)
        assertFalse(effects.any { it is Effect.Reroute })
    }
}
