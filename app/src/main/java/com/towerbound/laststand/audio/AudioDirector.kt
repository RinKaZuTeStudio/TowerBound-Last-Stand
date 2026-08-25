package com.towerbound.laststand.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.meta.GameSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Fully procedural audio engine (design: PROCEDURAL AUDIO — zero asset
 * files). One streaming AudioTrack mixes:
 *
 *  - a generative music bed (evolving minor-chord pad + slow bass pulse +
 *    noise shimmer, seeded per run for variety)
 *  - polyphonic one-shot SFX voices synthesized from primitive oscillators
 *    (sine / saw / square / noise) with envelope shaping
 *
 * The mixer thread owns no locks on the hot path: settings are read from
 * volatile fields, voices live in a simple pool.
 */
object AudioDirector {

    private const val SAMPLE_RATE = 22050
    private const val CHUNK_FRAMES = 1024

    /** Music synthesizer chord progression (semitones from A2 root). */
    private val CHORDS = arrayOf(
        intArrayOf(0, 3, 7, 10),    // Am7
        intArrayOf(-4, 0, 3, 7),    // Fmaj
        intArrayOf(-9, -5, -2, 2),  // Dm-ish
        intArrayOf(-2, 2, 5, 9)     // Em-ish
    )

    enum class MusicMood { MENU, BATTLE, BOSS, GAME_OVER }

    @Volatile var musicVolume: Float = 0.6f
        private set
    @Volatile var sfxVolume: Float = 0.9f
        private set
    @Volatile var muted: Boolean = false

    @Volatile private var mood: MusicMood = MusicMood.MENU
    @Volatile private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    /* ---------------- voice pool ---------------- */

    private class Voice {
        var active = false
        var t = 0f               // seconds since onset
        var dur = 0.2f
        var freq = 440f
        var freqEnd = 440f
        var wave = 0             // 0 sine 1 saw 2 square 3 noise
        var attack = 0.005f
        var release = 0.1f
        var gain = 1f
        var sweep = 0f           // pitch LFO depth
        var sweepRate = 0f
        var phase = 0f
    }

    private val voices = Array(24) { Voice() }

    /** SFX presets. */
    enum class Sfx {
        CLICK, TAP, HIT, CRIT, PLAYER_HURT, ENEMY_DEATH, EXPLOSION,
        LEVEL_UP, CARD_PICK, COIN, ORB, ABILITY, ULTIMATE, DASH,
        BOSS_ROAR, WAVE_START, TOWER_HIT, GAME_OVER, VICTORY, ERROR
    }

    /* ---------------- lifecycle ---------------- */

    fun start(context: Context) {
        if (running) return
        running = true
        val minBuf = android.media.AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        ) / 2   // minBuf is in bytes; halve => frames (16-bit stereo = 4 bytes/frame)… actually keep conservative
        val bufFrames = max(CHUNK_FRAMES * 4, minBuf)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufFrames * 2 * 2)
            .build()
        this.track = track
        track.play()
        thread = Thread({ mixLoop() }, "TowerboundAudio").apply {
            priority = Thread.MAX_PRIORITY - 2
            start()
        }
        GameLog.i("Audio", "started (sr=$SAMPLE_RATE, buffer=$bufFrames)")
    }

    fun stop() {
        running = false
        thread?.join(600)
        thread = null
        try { track?.stop() } catch (_: Throwable) {}
        track?.release()
        track = null
        GameLog.i("Audio", "stopped")
    }

    fun applySettings(settings: GameSettings) {
        musicVolume = settings.musicVolume
        sfxVolume = settings.sfxVolume
        muted = settings.muted
    }

    fun setMood(m: MusicMood) {
        if (mood != m) {
            mood = m
            GameLog.d("Audio", "mood -> $m")
        }
    }

    /* ---------------- mixer ---------------- */

    private fun mixLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val track = this.track ?: return
        val chunk = ShortArray(CHUNK_FRAMES * 2)
        val musicL = FloatArray(CHUNK_FRAMES)
        val musicR = FloatArray(CHUNK_FRAMES)

        var runningFrames = 0L

        while (running) {
            // --- music layer ---
            synthMusicChunk(musicL, musicR, runningFrames)
            runningFrames += CHUNK_FRAMES

            // --- mix ---
            for (i in 0 until CHUNK_FRAMES) {
                var l = musicL[i]
                var r = musicR[i]
                for (v in voices) {
                    if (!v.active) continue
                    val s = sampleVoice(v)
                    l += s
                    r += s * 0.94f
                    v.t += 1f / SAMPLE_RATE
                    if (v.t >= v.dur) v.active = false
                }
                l = (l * 0.9f).coerceIn(-1f, 1f)
                r = (r * 0.9f).coerceIn(-1f, 1f)
                chunk[i * 2] = (l * 32767f).toInt().toShort()
                chunk[i * 2 + 1] = (r * 32767f).toInt().toShort()
            }

            // honor mute by writing silence cheaply
            if (muted) {
                track.write(silentChunk(chunk.size), 0, chunk.size)
            } else {
                track.write(chunk, 0, chunk.size)
            }
        }
    }

    private var silenceCache: ShortArray? = null
    private fun silentChunk(size: Int): ShortArray {
        if (silenceCache?.size != size) silenceCache = ShortArray(size)
        return silenceCache!!
    }

    /* ---------------- music synthesis ---------------- */

    private var chordIndex = 0
    private var chordPhase = 0f
    private var musicSeed = 12345L
    private var shimmerNoise = 0f

    fun setMusicSeed(seed: Long) { musicSeed = seed }

    private fun synthMusicChunk(outL: FloatArray, outR: FloatArray, frameBase: Long) {
        val mVol = if (muted) 0f else musicVolume
        val boss = mood == MusicMood.BOSS
        val over = mood == MusicMood.GAME_OVER
        val tempo = when (mood) { MusicMood.MENU -> 0.10f; MusicMood.BATTLE -> 0.16f; MusicMood.BOSS -> 0.22f; MusicMood.GAME_OVER -> 0.06f }
        val chordLen = (SAMPLE_RATE / tempo).toInt().coerceAtLeast(CHUNK_FRAMES)

        if (frameBase % chordLen == 0L && frameBase > 0) {
            chordIndex = ((chordIndex + 1) + (musicSeed % 3L).toInt()) % CHORDS.size
        }
        val chord = CHORDS[chordIndex]
        val baseFreq = if (boss) 55f else 65.4f   // A1 / C2
        val detune = 1f + ((musicSeed % 11L) - 5L) * 0.0015f

        for (i in outL.indices) {
            val t = (frameBase + i).toFloat() / SAMPLE_RATE
            chordPhase += tempo / SAMPLE_RATE

            var padL = 0f
            var padR = 0f
            // 3 partials per chord note with slow shimmer
            for (n in chord.indices) {
                val f = baseFreq * 2f.pow(n) * semitone(chord[n]) * detune
                val amp = 0.16f / (n + 1.5f)
                val shimmer = 0.6f + 0.4f * sin(t * (0.35f + n * 0.11f) + n * 2.1f)
                padL += amp * shimmer * (sin(2f * PI.toFloat() * f * t) * 0.7f + saw(f * 0.5f, t) * 0.3f)
                padR += amp * (0.5f + 0.5f * sin(t * (0.29f + n * 0.13f) + 1.0f)) *
                    (sin(2f * PI.toFloat() * f * 1.003f * t) * 0.7f + saw(f * 0.501f, t) * 0.3f)
            }

            // bass pulse on chord root
            val pulse = max(0f, sin(2f * PI.toFloat() * chordPhase)) *
                max(0f, sin(2f * PI.toFloat() * chordPhase))   // squared = sharper pulse
            val bass = 0.28f * pulse * sin(2f * PI.toFloat() * baseFreq * t) *
                (if (boss) 1.2f else 1f).coerceAtMost(1.4f)

            // noise shimmer (breath) — gentle high pad
            shimmerNoise = shimmerNoise * 0.985f + (rand() - 0.5f) * 0.03f
            val air = shimmerNoise * (if (over) 1.6f else 0.55f)

            var l = (padL * 0.5f + bass * 0.8f + air * 0.35f)
            var r = (padR * 0.5f + bass * 0.8f + air * 0.35f)

            // game over: falling detune drone
            if (over) {
                val drone = 0.4f * sin(2f * PI.toFloat() * (baseFreq * 0.5f * (1f - 0.02f * sin(t * 0.4f))) * t)
                l += drone; r += drone
            }

            outL[i] = l * mVol * 0.5f
            outR[i] = r * mVol * 0.5f
        }
    }

    /* ---------------- voice synthesis ---------------- */

    private fun sampleVoice(v: Voice): Float {
        if (v.t < 0f) return 0f   // scheduled in the future (arpeggio delays)
        val frac = (v.t / v.dur).coerceIn(0f, 1f)
        // envelope: attack -> hold -> release curve
        val env = when {
            v.t < v.attack -> v.t / v.attack
            frac > 1f - v.release -> (1f - frac) / v.release
            else -> 1f
        }
        val f = v.freq + (v.freqEnd - v.freq) * frac +
            (if (v.sweep > 0f) v.sweep * sin(2f * PI.toFloat() * v.sweepRate * v.t) else 0f)
        v.phase += f / SAMPLE_RATE
        if (v.phase > 1f) v.phase -= 1f

        val raw = when (v.wave) {
            0 -> sin(2f * PI.toFloat() * v.phase)
            1 -> saw(v.freq, v.t)
            2 -> (if (v.phase < 0.5f) 1f else -1f)
            else -> rand() * 2f - 1f
        }
        return raw * env * v.gain * (if (muted) 0f else sfxVolume) * 0.22f
    }

    private fun saw(freq: Float, t: Float): Float {
        val phase = (freq * t) % 1f
        return 2f * phase - 1f
    }

    private fun semitone(semis: Int): Float = 2f.pow(semis / 12f)

    private fun Float.pow(x: Float): Float = Math.pow(this.toDouble(), x.toDouble()).toFloat()
    private fun Float.pow(n: Int): Float = Math.pow(this.toDouble(), n.toDouble()).toFloat()

    /* deterministic cheap noise */
    private var noiseState = 0x9E3779B9L
    private fun rand(): Float {
        noiseState = noiseState * 6364136223846793005L + 1442695040888963407L
        return ((noiseState ushr 33) and 0xFFFFFF).toFloat() / 0xFFFFFF.toFloat()
    }

    /* ---------------- SFX trigger API ---------------- */

    fun play(sfx: Sfx) {
        if (muted || sfxVolume <= 0f) return
        when (sfx) {
            Sfx.CLICK -> trigger(freq = 1250f, freqEnd = 900f, dur = 0.05f, wave = 2, gain = 0.5f, release = 0.5f)
            Sfx.TAP -> trigger(freq = 640f, freqEnd = 720f, dur = 0.05f, wave = 0, gain = 0.4f)
            Sfx.HIT -> { trigger(freq = 240f, freqEnd = 110f, dur = 0.08f, wave = 1, gain = 0.65f, release = 0.6f) }
            Sfx.CRIT -> {
                trigger(freq = 520f, freqEnd = 130f, dur = 0.16f, wave = 1, gain = 0.8f, release = 0.7f)
                trigger(freq = 1750f, freqEnd = 660f, dur = 0.10f, wave = 0, gain = 0.35f)
            }
            Sfx.PLAYER_HURT -> { trigger(freq = 300f, freqEnd = 90f, dur = 0.22f, wave = 2, gain = 0.75f, release = 0.8f) }
            Sfx.ENEMY_DEATH -> {
                trigger(freq = 380f, freqEnd = 70f, dur = 0.20f, wave = 1, gain = 0.6f, release = 0.8f)
                trigger(freq = 0f, freqEnd = 0f, dur = 0.14f, wave = 3, gain = 0.30f)
            }
            Sfx.EXPLOSION -> {
                trigger(freq = 0f, freqEnd = 0f, dur = 0.5f, wave = 3, gain = 0.9f, release = 0.95f, attack = 0.002f)
                trigger(freq = 120f, freqEnd = 32f, dur = 0.55f, wave = 1, gain = 0.85f, release = 0.95f)
            }
            Sfx.LEVEL_UP -> {
                trigger(freq = 523f, freqEnd = 523f, dur = 0.09f, wave = 0, gain = 0.5f)
                trigger(freq = 659f, freqEnd = 659f, dur = 0.09f, wave = 0, gain = 0.5f, delayFrames = SAMPLE_RATE / 12)
                trigger(freq = 784f, freqEnd = 784f, dur = 0.14f, wave = 0, gain = 0.55f, delayFrames = SAMPLE_RATE / 6)
            }
            Sfx.CARD_PICK -> trigger(freq = 880f, freqEnd = 1320f, dur = 0.12f, wave = 0, gain = 0.55f)
            Sfx.COIN -> {
                trigger(freq = 1568f, freqEnd = 1568f, dur = 0.06f, wave = 0, gain = 0.45f)
                trigger(freq = 2093f, freqEnd = 2093f, dur = 0.09f, wave = 0, gain = 0.45f, delayFrames = SAMPLE_RATE / 14)
            }
            Sfx.ORB -> trigger(freq = 1100f, freqEnd = 1650f, dur = 0.07f, wave = 0, gain = 0.28f)
            Sfx.ABILITY -> {
                trigger(freq = 300f, freqEnd = 900f, dur = 0.22f, wave = 0, gain = 0.6f)
                trigger(freq = 0f, freqEnd = 0f, dur = 0.18f, wave = 3, gain = 0.25f)
            }
            Sfx.ULTIMATE -> {
                trigger(freq = 130f, freqEnd = 520f, dur = 0.5f, wave = 1, gain = 0.8f, attack = 0.03f, release = 0.6f)
                trigger(freq = 0f, freqEnd = 0f, dur = 0.6f, wave = 3, gain = 0.5f, release = 0.9f)
            }
            Sfx.DASH -> trigger(freq = 700f, freqEnd = 240f, dur = 0.12f, wave = 3, gain = 0.35f, release = 0.9f)
            Sfx.BOSS_ROAR -> {
                trigger(freq = 90f, freqEnd = 45f, dur = 0.9f, wave = 1, gain = 0.95f, attack = 0.05f, release = 0.8f)
                trigger(freq = 55f, freqEnd = 30f, dur = 1.0f, wave = 2, gain = 0.7f, attack = 0.06f, release = 0.9f)
            }
            Sfx.WAVE_START -> {
                trigger(freq = 392f, freqEnd = 392f, dur = 0.12f, wave = 0, gain = 0.5f)
                trigger(freq = 523f, freqEnd = 523f, dur = 0.16f, wave = 0, gain = 0.5f, delayFrames = SAMPLE_RATE / 8)
            }
            Sfx.TOWER_HIT -> { trigger(freq = 160f, freqEnd = 60f, dur = 0.3f, wave = 2, gain = 0.8f, release = 0.85f) }
            Sfx.GAME_OVER -> {
                trigger(freq = 220f, freqEnd = 55f, dur = 1.4f, wave = 1, gain = 0.9f, attack = 0.02f, release = 0.7f)
                trigger(freq = 110f, freqEnd = 41f, dur = 1.6f, wave = 0, gain = 0.7f, attack = 0.05f, release = 0.8f)
            }
            Sfx.VICTORY -> {
                trigger(freq = 523f, freqEnd = 523f, dur = 0.1f, wave = 0, gain = 0.5f)
                trigger(freq = 659f, freqEnd = 659f, dur = 0.1f, wave = 0, gain = 0.5f, delayFrames = SAMPLE_RATE / 10)
                trigger(freq = 784f, freqEnd = 784f, dur = 0.1f, wave = 0, gain = 0.5f, delayFrames = SAMPLE_RATE / 5)
                trigger(freq = 1046f, freqEnd = 1046f, dur = 0.3f, wave = 0, gain = 0.6f, delayFrames = SAMPLE_RATE * 3 / 10)
            }
            Sfx.ERROR -> trigger(freq = 220f, freqEnd = 180f, dur = 0.15f, wave = 2, gain = 0.5f)
        }
    }

    private fun trigger(
        freq: Float, freqEnd: Float, dur: Float, wave: Int, gain: Float,
        attack: Float = 0.004f, release: Float = 0.35f,
        sweep: Float = 0f, sweepRate: Float = 0f,
        delayFrames: Int = 0
    ) {
        val v = voices.firstOrNull { !it.active } ?: return
        v.active = true
        v.t = if (delayFrames > 0) -delayFrames.toFloat() / SAMPLE_RATE else 0f
        v.dur = dur
        v.freq = max(20f, freq)
        v.freqEnd = max(20f, freqEnd)
        v.wave = wave
        v.attack = attack
        v.release = release.coerceIn(0.05f, 0.95f)
        v.gain = gain
        v.sweep = sweep
        v.sweepRate = sweepRate
        v.phase = 0f
    }
}
