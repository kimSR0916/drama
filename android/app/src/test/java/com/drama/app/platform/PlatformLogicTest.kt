package com.drama.app.platform

import com.drama.app.core.Branch
import com.drama.app.core.HarshKind
import com.drama.app.core.Option
import com.drama.app.core.RouteHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformLogicTest {
    private val branch = Branch(Option("우회로로 빠진다", "우회로", "."), Option("기존 경로 유지", "정면 돌파", "."), ".")

    @Test fun voiceMatchesTheSpokenKeyword() {
        assertEquals(RouteHint.DETOUR, matchChoice("우회로", branch))
        assertEquals(RouteHint.KEEP, matchChoice("정면 돌파하자", branch))
        assertEquals(RouteHint.DETOUR, matchChoice("1번", branch))
        assertEquals(RouteHint.KEEP, matchChoice("두 번째", branch))
        assertNull(matchChoice("우회로든 정면돌파든", branch))
        assertNull(matchChoice("비가 오네", branch))
    }

    @Test fun harshDetectorUsesSpeedDelta() {
        val seen = mutableListOf<HarshKind>()
        val d = HarshDetector { seen += it }
        d.onSpeed(0, 50f)
        d.onSpeed(1_000, 55f)
        d.onSpeed(2_000, 40f)
        d.onSpeed(3_000, 52f)
        d.onSpeed(10_000, 0f)
        assertEquals(listOf(HarshKind.BRAKE, HarshKind.ACCEL), seen)
    }
}
