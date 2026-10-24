package com.nikira.videocompressor

import com.nikira.videocompressor.domain.BitrateCalculator
import com.nikira.videocompressor.domain.ResolutionLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BitrateCalculatorTest {
    @Test fun subtracts_audio_budget_before_calculating_video_bitrate() { assertEquals(2_458_487, BitrateCalculator.videoBitrate(18.5f, 60.0, 128_000)) }
    @Test fun resolution_choices_are_maximum_dimensions() {
        assertEquals(1080, BitrateCalculator.suggestedMaxDimension(2_000_000, ResolutionLimit.P1080))
        assertEquals(720, BitrateCalculator.suggestedMaxDimension(800_000, ResolutionLimit.AUTO))
        assertEquals(480, BitrateCalculator.suggestedMaxDimension(400_000, ResolutionLimit.AUTO))
        assertNull(BitrateCalculator.suggestedMaxDimension(1_000_000, ResolutionLimit.AUTO))
    }
}