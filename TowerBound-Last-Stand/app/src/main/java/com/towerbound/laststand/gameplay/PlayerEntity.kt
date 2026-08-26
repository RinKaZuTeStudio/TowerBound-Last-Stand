package com.towerbound.laststand.gameplay

import com.towerbound.laststand.anim.AnimEvent
import com.towerbound.laststand.anim.AnimationController
import com.towerbound.laststand.assets.AnimState
import com.towerbound.laststand.combat.StatusKind
import com.towerbound.laststand.combat.StatusTarget
import com.towerbound.laststand.combat.StatBlock
import com.towerbound.laststand.combat.TEAM_PLAYER
import com.towerbound.laststand.combat.Combatant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The player-controlled hero entity. Holds position, stats, animation,
 * dash state, hero identity and the combat-facing [Combatant] contract.
 */
class PlayerEntity(
    val heroId: String,
    val stats: StatBlock,
    override var x: Float,
    override var y: Float
) : Combatant, StatusTarget {
    override val radius: Float = 34f

    /** Facing angle in radians (movement or aim direction). */
    var facing: Float = 0f
    var hp: Float = stats.finalMaxHp
        private set

    /** True when facing left (sprite mirror). */
    var facingLeft: Boolean = false

    val anim = AnimationController(AnimState.IDLE)

    /** Dash state. */
    var dashCooldown: Float = 0f
    var dashTimeRemaining: Float = 0f
    private var dashDirX: Float = 0f
    private var dashDirY: Float = 0f
    val dashSpeed: Float = 760f
    val dashDuration: Float = 0.18f
    val dashCooldownTime: Float = 1.6f

    /** Invulnerability while dashing (dodge i-frames). */
    val dashing: Boolean get() = dashTimeRemaining > 0f

    var invulnTimer: Float = 0f

    /** Hit flash for render. */
    var hitFlashTimer: Float = 0f

    /** Equipped cosmetic skin tint (RGB multipliers, null = default). */
    var skinTint: FloatArray? = null

    var alive: Boolean = true
        private set

    /** Current visual speed for walk/run animation choice. */
    var speedVisual: Float = 0f
        private set

    /** Damage-over-time accumulator fed by StatusTarget callback. */
    var pendingDotDamage: Float = 0f
        private set

    /** Last status kinds applied (for VFX hooks). */
    var lastStatusApplied: StatusKind? = null
        private set

    /* ---- Combatant / StatusTarget contracts ---- */

    override val team: Int get() = TEAM_PLAYER

    override fun statsBlock(): StatBlock = stats

    override fun stats(): StatBlock = stats

    override fun applyDamage(damage: Float, isCrit: Boolean, source: String): Float {
        val d = if (damage.isNaN() || damage.isInfinite() || damage < 0f) 0f else damage
        return takeDamage(d)
    }

    override fun isAlive(): Boolean = alive

    override fun onStatusDamage(damage: Float, kind: StatusKind) {
        if (damage.isNaN() || damage < 0f) return
        pendingDotDamage += damage
    }

    override fun onStatusApplied(kind: StatusKind) {
        lastStatusApplied = kind
    }

    override fun onStatusRemoved(kind: StatusKind) {}

    fun consumePendingDot(): Float {
        val v = pendingDotDamage
        pendingDotDamage = 0f
        return v
    }


    fun maxHp(): Float = stats.finalMaxHp

    fun hpFraction(): Float = (hp / maxHp()).coerceIn(0f, 1f)

    fun takeDamage(amount: Float): Float {
        if (!alive || invulnTimer > 0f || dashing) return 0f
        val applied = amount.coerceAtLeast(0f)
        hp = (hp - applied).coerceAtLeast(0f)
        hitFlashTimer = 0.15f
        if (hp <= 0f) {
            alive = false
            anim.play(AnimState.DEATH, force = true)
        } else {
            anim.play(AnimState.HIT, force = true)
        }
        return applied
    }

    fun heal(amount: Float) {
        if (!alive) return
        hp = (hp + amount.coerceAtLeast(0f)).coerceAtMost(maxHp())
    }

    fun reviveAt(x: Float, y: Float) {
        this.x = x; this.y = y
        hp = maxHp()
        alive = true
        invulnTimer = 1.5f
        anim.play(AnimState.IDLE, force = true)
    }

    /**
     * Movement + dash integration for one fixed step.
     */
    fun update(dt: Float, moveX: Float, moveY: Float, world: com.towerbound.laststand.world.GameWorld) {
        if (!alive) {
            anim.update(dt)
            return
        }
        if (invulnTimer > 0f) invulnTimer -= dt
        if (hitFlashTimer > 0f) hitFlashTimer -= dt
        if (dashCooldown > 0f) dashCooldown -= dt

        val vx: Float
        val vy: Float
        if (dashTimeRemaining > 0f) {
            dashTimeRemaining -= dt
            vx = dashDirX * dashSpeed
            vy = dashDirY * dashSpeed
            if (dashTimeRemaining <= 0f) {
                anim.play(AnimState.IDLE)
            }
        } else {
            val spd = stats.finalMoveSpeed
            vx = moveX * spd
            vy = moveY * spd
            val mag = kotlin.math.sqrt(moveX * moveX + moveY * moveY)
            if (mag > 0.01f) {
                facing = atan2(moveY, moveX)
                facingLeft = cos(facing) < -0.15f
            }
        }

        val nx = x + vx * dt
        val ny = y + vy * dt
        val resolved = world.resolveCollision(nx, ny, radius)
        x = resolved[0]; y = resolved[1]

        speedVisual = kotlin.math.sqrt(vx * vx + vy * vy)

        // animation state machine by movement
        when {
            dashTimeRemaining > 0f -> anim.play(AnimState.DASH, speed = 1f)
            speedVisual > stats.finalMoveSpeed * 1.15f -> anim.play(AnimState.RUN)
            speedVisual > 10f -> anim.play(AnimState.WALK)
            else -> anim.play(AnimState.IDLE)
        }
        anim.update(dt)
    }

    /** Start a dash in the current input (or facing) direction. */
    fun tryDash(moveX: Float, moveY: Float): Boolean {
        if (!alive || dashCooldown > 0f || dashTimeRemaining > 0f) return false
        val mag = kotlin.math.sqrt(moveX * moveX + moveY * moveY)
        if (mag > 0.01f) {
            dashDirX = moveX / mag
            dashDirY = moveY / mag
        } else {
            dashDirX = cos(facing)
            dashDirY = sin(facing)
        }
        dashTimeRemaining = dashDuration
        dashCooldown = dashCooldownTime
        anim.play(AnimState.DASH, force = true)
        return true
    }

    fun dashCooldownFraction(): Float =
        (dashCooldown / dashCooldownTime).coerceIn(0f, 1f)
}
