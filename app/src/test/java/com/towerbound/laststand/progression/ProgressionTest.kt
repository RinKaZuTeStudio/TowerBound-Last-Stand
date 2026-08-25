package com.towerbound.laststand.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Run-progression curve sanity (design: XP SYSTEM + WAVE SYSTEM).
 */
class ProgressionTest {

    @Test
    fun `run xp curve is strictly increasing`() {
        var prev = 0
        for (level in 1..60) {
            val need = XpSystem.xpForLevel(level)
            assertTrue("level $level need $need <= prev $prev", need > prev)
            prev = need
        }
    }

    @Test
    fun `run xp curve stays bounded per level`() {
        // curve is 6 + (N-1)^1.35 * 4 — at level 60 the requirement must stay sane
        assertTrue(XpSystem.xpForLevel(60) < 3000)
    }

    @Test
    fun `wave scaling constants match design`() {
        // endless scaling: +13% HP and +9% damage per wave compounding
        assertEquals(0.13f, com.towerbound.laststand.waves.WaveSystem.HP_SCALE_PER_WAVE, 0.0001f)
        assertEquals(0.09f, com.towerbound.laststand.waves.WaveSystem.DMG_SCALE_PER_WAVE, 0.0001f)
    }

    @Test
    fun `compounding wave scaling grows but stays finite`() {
        var hp = 1.0
        for (wave in 1..200) {
            hp *= 1.0 + com.towerbound.laststand.waves.WaveSystem.HP_SCALE_PER_WAVE
            assertTrue(hp.isFinite())
            assertTrue(hp > 0)
        }
        // wave 200 has ~1.13^200 = huge but finite scaling; design soft-caps via staged spawning
        assertTrue(hp > 1e8)
    }

    @Test
    fun `upgrade pool exposes ids uniquely`() {
        val ids = com.towerbound.laststand.progression.UpgradePool.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(com.towerbound.laststand.progression.UpgradePool.all.size >= 20)
    }
}
