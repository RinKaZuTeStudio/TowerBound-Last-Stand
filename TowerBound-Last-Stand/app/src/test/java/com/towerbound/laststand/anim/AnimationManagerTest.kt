package com.towerbound.laststand.anim

import com.towerbound.laststand.assets.AnimState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationControllerTest {

    @Test
    fun `idle loops and accumulates loops`() {
        val anim = AnimationController(AnimState.IDLE)
        repeat(60) { anim.update(1f / 60f) } // 1 second @5fps = 5 frames
        assertEquals(AnimState.IDLE, anim.state)
        assertEquals(1, anim.completedLoops) // 4-frame cycle
    }

    @Test
    fun `non looping state finishes and fires completion`() {
        val anim = AnimationController(AnimState.ATTACK)
        var completed = false
        anim.onComplete { completed = true }
        // ATTACK: 5 frames @ 16fps = 0.3125s
        repeat(30) { anim.update(1f / 60f) }
        assertTrue(completed)
        assertTrue(anim.finished)
        assertEquals(AnimState.ATTACK.frameCount - 1, anim.frameIndex())
    }

    @Test
    fun `attack impact event fires once per cycle`() {
        val anim = AnimationController(AnimState.ATTACK)
        anim.addEvent(2, AnimEvent.ON_ATTACK_IMPACT)
        val fired = mutableListOf<AnimEvent>()
        anim.onEvent { fired.add(it) }
        repeat(30) { anim.update(1f / 60f) }
        assertEquals(listOf(AnimEvent.ON_ATTACK_IMPACT), fired)
    }

    @Test
    fun `looping events fire every cycle`() {
        val anim = AnimationController(AnimState.IDLE)
        anim.addEvent(1, AnimEvent.ON_HIT)
        val count = intArrayOf(0)
        anim.onEvent { count[0]++ }
        repeat(120) { anim.update(1f / 60f) } // 2 seconds = 10 frames = 2.5 cycles
        assertEquals(3, count[0]) // cycles 0, 1 and 2 cross frame 1
    }

    @Test
    fun `play switches state and resets time`() {
        val anim = AnimationController(AnimState.IDLE)
        repeat(30) { anim.update(1f / 60f) }
        anim.play(AnimState.WALK)
        assertEquals(AnimState.WALK, anim.state)
        assertEquals(0f, anim.time, 0.0001f)
    }

    @Test
    fun `same state play does not restart unless forced`() {
        val anim = AnimationController(AnimState.IDLE)
        repeat(10) { anim.update(1f / 60f) }
        val t = anim.time
        anim.play(AnimState.IDLE)
        assertEquals(t, anim.time, 0.0001f)
        anim.play(AnimState.IDLE, force = true)
        assertEquals(0f, anim.time, 0.0001f)
    }

    @Test
    fun `speed scaling affects playback`() {
        val slow = AnimationController(AnimState.WALK)
        val fast = AnimationController(AnimState.WALK)
        fast.speed = 2f
        repeat(30) {
            slow.update(1f / 60f)
            fast.update(1f / 60f)
        }
        assertTrue(fast.completedLoops > slow.completedLoops)
    }

    @Test
    fun `blend fades in after transition`() {
        val anim = AnimationController(AnimState.IDLE)
        anim.play(AnimState.WALK)
        assertEquals(0f, anim.blend(), 0f)
        anim.update(0.12f)
        assertEquals(1f, anim.blend(), 0.01f)
    }

    @Test
    fun `frame index stays in range`() {
        for (state in AnimState.entries) {
            val anim = AnimationController(state)
            repeat(300) {
                anim.update(1f / 30f)
                assertTrue(anim.frameIndex() in 0 until state.frameCount)
            }
        }
    }
}
