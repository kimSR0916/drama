package com.drama.app.platform

import com.drama.app.core.Choice
import com.drama.app.core.HarshKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformLogicTest {
    private val choices = listOf(Choice("a", "경비원", "..."), Choice("b", "우체국장", "..."))

    @Test fun voiceMatchesLabelOrOrdinal() {
        assertEquals("b", matchChoice("우체 국장 만나볼래", choices)?.id)
        assertEquals("a", matchChoice("첫 번째", choices)?.id)
        assertEquals("b", matchChoice("2번", choices)?.id)
        assertNull(matchChoice("글쎄", choices))
        assertNull(matchChoice("", choices))
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
