package com.towerbound.laststand.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateManagerTest {

    @Test
    fun `startup chain transitions are legal`() {
        val sm = StateManager()
        assertEquals(GameState.BOOT, sm.current)
        assertTrue(sm.transition(GameState.SPLASH))
        assertTrue(sm.transition(GameState.LOADING))
        assertTrue(GameStateTransitions.isAllowed(GameState.MAIN_MENU, GameState.HERO_SELECT))
    }

    @Test
    fun `illegal transitions are rejected`() {
        val sm = StateManager()
        assertFalse(sm.transition(GameState.GAMEPLAY)) // BOOT -> GAMEPLAY illegal
        assertEquals(GameState.BOOT, sm.current)
        assertTrue(sm.transition(GameState.SPLASH))
        assertFalse(sm.transition(GameState.GAMEPLAY)) // SPLASH -> GAMEPLAY illegal
        assertEquals(GameState.SPLASH, sm.current)
    }

    @Test
    fun `gameplay loop transitions`() {
        val sm = StateManager()
        sm.transition(GameState.SPLASH); sm.transition(GameState.LOADING); sm.transition(GameState.MAIN_MENU)
        sm.transition(GameState.LOADING_GAME); sm.transition(GameState.GAMEPLAY)
        assertTrue(sm.transition(GameState.LEVEL_UP))
        assertTrue(sm.transition(GameState.GAMEPLAY))
        assertTrue(sm.transition(GameState.PAUSED))
        assertTrue(sm.transition(GameState.GAMEPLAY))
        assertTrue(sm.transition(GameState.GAME_OVER))
        assertTrue(sm.transition(GameState.RESULTS))
        assertTrue(sm.transition(GameState.MAIN_MENU))
    }

    @Test
    fun `listeners receive enter and exit events`() {
        val sm = StateManager()
        val events = mutableListOf<String>()
        sm.addListener(object : StateManager.Listener {
            override fun onStateEnter(state: GameState) { events.add("enter:$state") }
            override fun onStateExit(state: GameState) { events.add("exit:$state") }
        })
        sm.transition(GameState.SPLASH)
        assertEquals(listOf("exit:BOOT", "enter:SPLASH"), events)
    }

    @Test
    fun `simulates flag matches design`() {
        assertTrue(GameState.GAMEPLAY.simulates)
        assertTrue(GameState.BOSS_FIGHT.simulates)
        assertFalse(GameState.PAUSED.simulates)
        assertFalse(GameState.MAIN_MENU.simulates)
    }
}

class RngTest {

    @Test
    fun `same seed produces identical sequences`() {
        val a = Rng(42L)
        val b = Rng(42L)
        repeat(100) {
            assertEquals(a.nextFloat(), b.nextFloat(), 0f)
            assertEquals(a.nextInt(1000), b.nextInt(1000))
        }
    }

    @Test
    fun `different seeds diverge`() {
        val a = Rng(1L)
        val b = Rng(2L)
        var diff = 0
        repeat(50) { if (a.nextInt(100) != b.nextInt(100)) diff++ }
        assertTrue(diff > 20)
    }

    @Test
    fun `nextFloat stays in unit range`() {
        val rng = Rng(7L)
        repeat(10000) {
            val f = rng.nextFloat()
            assertTrue(f >= 0f && f < 1f)
        }
    }

    @Test
    fun `nextInt respects bounds`() {
        val rng = Rng(9L)
        repeat(10000) {
            val v = rng.nextInt(5)
            assertTrue(v in 0..4)
        }
    }

    @Test
    fun `chance edge cases`() {
        val rng = Rng(3L)
        assertFalse(rng.chance(0f))
        assertTrue(rng.chance(1f))
    }

    @Test
    fun `shuffled keeps elements`() {
        val rng = Rng(11L)
        val src = (1..100).toList()
        val out = rng.shuffled(src)
        assertEquals(src.size, out.size)
        assertEquals(src.sum(), out.sum())
        assertEquals(src.toSet(), out.toSet())
    }

    @Test
    fun `sample returns distinct elements`() {
        val rng = Rng(13L)
        val out = rng.sample((1..20).toList(), 5)
        assertEquals(5, out.size)
        assertEquals(5, out.toSet().size)
    }
}

class GameClockTest {

    @Test
    fun `fixed timestep advances one step per frame at 60fps`() {
        val clock = GameClock()
        var steps = 0
        repeat(10) {
            val n = clock.advance(1f / 60f)
            steps += n
        }
        assertEquals(10, steps)
        assertEquals(10f / 60f, clock.simTime, 0.001f)
    }

    @Test
    fun `pause blocks simulation`() {
        val clock = GameClock()
        clock.pause()
        assertEquals(0, clock.advance(1f))
        assertEquals(0f, clock.simTime, 0f)
        clock.unpause()
        assertEquals(1, clock.advance(1f / 60f))
    }

    @Test
    fun `hit stop freezes then resumes`() {
        val clock = GameClock()
        clock.hitStop(0.1f)
        assertEquals(0, clock.advance(1f / 60f))
        var guard = 0
        while (clock.hitStopRemaining > 0f && guard < 100) { clock.advance(1f / 60f); guard++ }
        assertTrue(guard < 15) // ~6 frames of 0.1s hit stop
        assertEquals(1, clock.advance(1f / 60f))
    }

    @Test
    fun `slow motion scales steps`() {
        val clock = GameClock()
        clock.slowMotion(0.5f, 1f)
        val steps = clock.advance(1f / 30f)
        assertEquals(1, steps)
    }

    @Test
    fun `huge frame gap is clamped`() {
        val clock = GameClock()
        val steps = clock.advance(10f)
        assertTrue(steps <= GameClock.MAX_STEPS_PER_FRAME)
    }
}
