package com.towerbound.laststand.core

/**
 * Controlled deterministic RNG. Centralising all randomness lets us:
 *  - replay/verify runs deterministically (debug)
 *  - separate streams (upgrade RNG, loot RNG, event RNG, sim RNG) so a
 *    gameplay roll never desyncs loot rolls
 *  - guard against unfair streaks for upgrade card offers
 *
 * Pure Kotlin — unit tested on the JVM.
 */
class Rng(seed: Long) {

    private var state: Long = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    /** SplitMix64 core — fast, high quality, deterministic across platforms. */
    private fun next(): Long {
        state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15 as two's complement add
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun nextLong(): Long = next()

    /** Uniform [0.0, 1.0). */
    fun nextFloat(): Float = (next() ushr 40).toFloat() / (1L shl 24).toFloat()

    /** Uniform int [0, bound). bound must be > 0. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        return ((next() ushr 33).toDouble() / (1L shl 31).toDouble() * bound).toInt().coerceIn(0, bound - 1)
    }

    /** Uniform int [from, to] inclusive. */
    fun nextInt(from: Int, to: Int): Int {
        if (to <= from) return from
        return from + nextInt(to - from + 1)
    }

    /** True with probability p (0..1). */
    fun chance(p: Float): Boolean = if (p <= 0f) false else if (p >= 1f) true else nextFloat() < p

    /** Pick a random element; throws on empty collection (programming error). */
    fun <T> pick(items: List<T>): T {
        if (items.isEmpty()) throw IllegalArgumentException("pick() on empty list")
        return items[nextInt(items.size)]
    }

    /** Fisher–Yates shuffle returning a new list (input untouched). */
    fun <T> shuffled(items: List<T>): List<T> {
        val out = items.toMutableList()
        for (i in out.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val tmp = out[i]; out[i] = out[j]; out[j] = tmp
        }
        return out
    }

    /** Sample n distinct elements (or fewer if the list is small). */
    fun <T> sample(items: List<T>, n: Int): List<T> {
        if (n >= items.size) return shuffled(items)
        return shuffled(items).take(n)
    }

    fun nextSeed(): Long = next()

    /** Roll a float in [from, to). */
    fun range(from: Float, to: Float): Float = from + nextFloat() * (to - from)

    /** +/- percentage jitter around a value, e.g. jitter(10f, 0.1f) => 9..11 */
    fun jitter(value: Float, pct: Float): Float = value * (1f + range(-pct, pct))

    companion object {
        fun fromSystemTime(): Rng = Rng(System.nanoTime())
    }
}
