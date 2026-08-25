package com.towerbound.laststand.render

import android.graphics.Color
import com.towerbound.laststand.assets.AnimState
import com.towerbound.laststand.assets.AssetRepository
import com.towerbound.laststand.assets.SpriteSheet
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import kotlin.math.sin

/**
 * Draws the game world: terrain, animated props, tower, entities, fx.
 * Called once per frame from the engine's render hook with the sprite
 * batch already begun and the camera MVP applied.
 */
class WorldRenderer(private val engine: GameEngine, private val glRenderer: GLRenderer) {

    // cloud drift state
    private class Cloud(var x: Float, var y: Float, var speed: Float, var scale: Float, val name: String)
    private val clouds = mutableListOf<Cloud>()
    private var cloudsInit = false

    // water animation
    private var waterFrameAcc = 0f

    fun render(batch: SpriteBatch, textures: TextureManager, text: TextAtlas, camera: Camera) {
        val world = engine.world ?: return
        val t = engine.clock.simTime

        drawTerrain(batch, textures, camera)
        drawProps(batch, textures, camera, t)
        drawEnemies(batch, textures)
        drawBoss(batch, textures)
        drawShades(batch, textures)
        drawTower(batch, textures, t)
        drawXpOrbs(batch, textures, t)
        drawProjectiles(batch, textures, false)
        drawParticles(batch, textures, additive = false)
        drawPlayer(batch, textures)
        drawClouds(batch, textures, camera)
        // additive pass: projectile glows + glowing particles
        batch.additiveBlending()
        drawProjectiles(batch, textures, true)
        drawParticles(batch, textures, additive = true)
        drawStatusIcons(batch, textures)
        drawTelegraphs(batch, textures)
        batch.normalBlending()
        drawWorldEdge(batch, textures, camera, world)
        drawDamageNumbers(batch, text)
        drawDayNightOverlay(batch, textures, camera, world)
    }

    /** Full-screen day/night tint from the environment system. */
    private fun drawDayNightOverlay(batch: SpriteBatch, textures: TextureManager, camera: Camera, world: com.towerbound.laststand.world.GameWorld) {
        val env = engine.system<com.towerbound.laststand.env.EnvironmentSystem>() ?: return
        if (env.tintA <= 0.004f) return
        val glow = AssetRepository.fx("glow_void") ?: return
        val tex = textures.get("fx_glow_void", glow)
        batch.setTint(env.tintR, env.tintG, env.tintB, env.tintA)
        // cover the visible viewport in world coords
        batch.draw(tex, camera.x, camera.y, camera.viewWidth + 40f, camera.viewHeight + 40f)
        batch.setTint(1f, 1f, 1f, 1f)
    }

    /* ---------------- terrain ---------------- */

    private fun drawTerrain(batch: SpriteBatch, textures: TextureManager, camera: Camera) {
        val world = engine.world ?: return
        for (tile in world.terrainTiles) {
            if (!isVisible(camera, tile.x, tile.y, world.tileSize)) continue
            val bmp = AssetRepository.terrain(tile.seed)
            val tex = textures.get("terrain_${tile.variant}", bmp)
            batch.draw(tex, tile.x, tile.y, world.tileSize, world.tileSize)
        }
    }

    /* ---------------- props with sway ---------------- */

    private fun drawProps(batch: SpriteBatch, textures: TextureManager, camera: Camera, t: Float) {
        val world = engine.world ?: return
        // sort not needed for top-down; grass drawn first (ground layer)
        for (p in world.props) {
            val isGrass = p.name.startsWith("grass")
            if (!isGrass && !isVisible(camera, p.x, p.y, 160f)) continue
            if (isGrass && !isVisible(camera, p.x, p.y, 64f)) continue
            val bmp = AssetRepository.prop(p.name) ?: continue
            val tex = textures.get("prop_${p.name}", bmp)
            val w = bmp.width * p.scale
            val h = bmp.height * p.scale
            if (isGrass) {
                // sine-wave sway (design: grass sway animation)
                val rot = sin(t * 1.8f + p.swayPhase) * 7f
                batch.draw(tex, p.x, p.y, w, h, rot)
            } else if (p.name.startsWith("tree") || p.name.startsWith("bush")) {
                // gentle canopy sway
                val rot = sin(t * 0.7f + p.swayPhase) * 1.6f
                batch.draw(tex, p.x, p.y - h * 0.06f, w, h, rot)
            } else {
                batch.draw(tex, p.x, p.y, w, h)
            }
        }
    }

    /* ---------------- tower ---------------- */

    private fun drawTower(batch: SpriteBatch, textures: TextureManager, t: Float) {
        val world = engine.world ?: return
        val stage = towerStageFor(engine.towerHpFraction)
        val bmp = AssetRepository.towerStage(stage)
        val tex = textures.get("tower_$stage", bmp)
        val w = 250f
        val h = 250f * (bmp.height.toFloat() / bmp.width)
        batch.draw(tex, world.centerX, world.centerY, w, h)

        // idle turret rotation until enemies exist (Phase 8 wires real targeting)
        val turretBmp = AssetRepository.turret()
        val turretTex = textures.get("turret", turretBmp)
        val rot = t * 24f
        batch.draw(turretTex, world.centerX, world.centerY - 40f, 110f, 110f, rot)
    }

    private fun towerStageFor(hpFraction: Float): Int = when {
        hpFraction > 0.8f -> 0
        hpFraction > 0.6f -> 1
        hpFraction > 0.4f -> 2
        hpFraction > 0.2f -> 3
        else -> 4
    }

    /* ---------------- player ---------------- */

    private fun drawPlayer(batch: SpriteBatch, textures: TextureManager) {
        val player = engine.system<com.towerbound.laststand.gameplay.PlayerController>()?.player ?: return
        val sheet = AssetRepository.heroSheet(player.heroId, player.anim.state) ?: return
        val tex = textures.get("hero_${sheet.designId}_${sheet.state.name}", sheet.bitmap)
        val frameF = player.anim.frameFloat()
        var u0 = sheet.frameU(frameF)
        var u1 = u0 + 1f / sheet.frameCount
        // facing mirror by u-swap
        if (player.facingLeft) {
            val tmp = u0; u0 = u1; u1 = tmp
        }
        val size = 132f
        // cosmetic skin tint (equipped collection skin)
        player.skinTint?.let { tint ->
            batch.setTint(tint[0], tint[1], tint[2], 1f)
        }
        // hit flash tint overrides skin tint briefly
        if (player.hitFlashTimer > 0f) {
            batch.setTint(1f, 0.45f, 0.45f, 1f)
        }
        batch.draw(tex, player.x, player.y, size, size, 0f, u0, 0f, u1, 1f)
        batch.setTint(1f, 1f, 1f, 1f)

        // dash trail ghosts
        if (player.dashing) {
            batch.setAlpha(0.35f)
            batch.draw(tex, player.x - kotlin.math.cos(player.facing) * 40f, player.y - kotlin.math.sin(player.facing) * 40f, size, size, 0f, u0, 0f, u1, 1f)
            batch.setAlpha(1f)
        }
    }

    /* ---------------- boss ---------------- */

    private fun drawBoss(batch: SpriteBatch, textures: TextureManager) {
        val bossSys = engine.system<com.towerbound.laststand.bosses.BossSystem>() ?: return
        val b = bossSys.boss
        if (!b.alive && b.phase != com.towerbound.laststand.bosses.BossPhase.DYING) return
        if (!isVisible(cameraRef, b.x, b.y, 260f)) return

        val sheet = AssetRepository.enemySheet(b.designId, b.anim.state)
            ?: AssetRepository.enemySheet("BOSS_GRAULK", b.anim.state) ?: return
        val tex = textures.get("enemy_${sheet.designId}_${sheet.state.name}", sheet.bitmap)
        var u0 = sheet.frameU(b.anim.frameFloat())
        var u1 = u0 + 1f / sheet.frameCount
        if (b.facingLeft) { val tmp = u0; u0 = u1; u1 = tmp }
        val size = b.radius * 4.6f
        // phase tint: redder as phases advance
        val phaseTint = when (b.phaseNumber) {
            1 -> floatArrayOf(1f, 1f, 1f)
            2 -> floatArrayOf(1f, 0.92f, 0.85f)
            3 -> floatArrayOf(1f, 0.8f, 0.72f)
            else -> floatArrayOf(1f, 0.66f, 0.6f)
        }
        batch.setTint(phaseTint[0], phaseTint[1], phaseTint[2], 1f)
        batch.draw(tex, b.x, b.y, size, size, 0f, u0, 0f, u1, 1f)
        batch.setTint(1f, 1f, 1f, 1f)

        // boss HP bar (top of screen style, drawn above entity for now)
        drawHealthBar(batch, textures, b.x, b.y - size * 0.6f, size * 0.9f, 10f, b.hpFraction(), 0xFFE53935.toInt())
    }

    /** Telegraph rings rendered from boss system callback storage. */
    private val telegraphList = mutableListOf<FloatArray>() // x, y, radius, remaining

    fun pushTelegraph(x: Float, y: Float, radius: Float, t: Float) {
        telegraphList.add(floatArrayOf(x, y, radius, t))
    }

    private fun drawTelegraphs(batch: SpriteBatch, textures: TextureManager) {
        if (telegraphList.isEmpty()) return
        val ring = AssetRepository.fx("ring_red") ?: return
        val tex = textures.get("fx_ring_red", ring)
        val it = telegraphList.iterator()
        while (it.hasNext()) {
            val tg = it.next()
            tg[3] -= 0.016f
            if (tg[3] <= 0f) { it.remove(); continue }
            val pulse = 0.6f + 0.4f * kotlin.math.sin(tg[3] * 18f)
            batch.setAlpha(0.75f * pulse)
            batch.draw(tex, tg[0], tg[1], tg[2] * 2f, tg[2] * 2f)
            batch.setAlpha(1f)
        }
    }

    /* ---------------- shades (summons) ---------------- */

    private fun drawShades(batch: SpriteBatch, textures: TextureManager) {
        val shadows = engine.system<com.towerbound.laststand.heroes.ShadowSystem>() ?: return
        for (s in shadows.activeShades) {
            if (!isVisible(cameraRef, s.x, s.y, 120f)) continue
            // shades render as the player hero sheet with dark violet tint + flicker
            val sheet = AssetRepository.heroSheet("MORVAIN", s.anim.state) ?: continue
            val tex = textures.get("hero_MORVAIN_${s.anim.state.name}", sheet.bitmap)
            var u0 = sheet.frameU(s.anim.frameFloat())
            var u1 = u0 + 1f / sheet.frameCount
            if (s.facingLeft) { val tmp = u0; u0 = u1; u1 = tmp }
            val flicker = 0.55f + 0.15f * sin(engine.clock.simTime * 12f)
            batch.setTint(0.55f * flicker, 0.4f * flicker, 0.9f * flicker, flicker)
            batch.draw(tex, s.x, s.y, 118f, 118f, 0f, u0, 0f, u1, 1f)
            batch.setTint(1f, 1f, 1f, 1f)
        }
    }

    /* ---------------- XP orbs ---------------- */

    private fun drawXpOrbs(batch: SpriteBatch, textures: TextureManager, t: Float) {
        val xp = engine.system<com.towerbound.laststand.progression.XpSystem>() ?: return
        val bmp = AssetRepository.fx("xp_orb") ?: return
        val tex = textures.get("fx_xp_orb", bmp)
        xp.forEachOrb { orb ->
            if (!isVisible(cameraRef, orb.x, orb.y, 40f)) return@forEachOrb
            val bob = sin(t * 3f + orb.bobPhase) * 4f
            val s = 22f + sin(t * 5f + orb.bobPhase) * 3f
            batch.draw(tex, orb.x, orb.y + bob, s, s)
        }
    }

    /* ---------------- enemies ---------------- */

    private fun drawEnemies(batch: SpriteBatch, textures: TextureManager) {
        val mgr = engine.system<com.towerbound.laststand.enemies.EnemyManager>() ?: return
        for (e in mgr.active) {
            if (!isVisible(cameraRef, e.x, e.y, 160f)) continue
            val sheet = AssetRepository.enemySheet(e.def.id, e.anim.state) ?: continue
            val tex = textures.get("enemy_${sheet.designId}_${sheet.state.name}", sheet.bitmap)
            var u0 = sheet.frameU(e.anim.frameFloat())
            var u1 = u0 + 1f / sheet.frameCount
            if (e.facingLeft) { val tmp = u0; u0 = u1; u1 = tmp }
            val size = e.radius * 4.4f
            if (e.hitFlashTimer > 0f) batch.setTint(1f, 0.5f, 0.5f, 1f)
            else if (e.isElite) batch.setTint(1.15f, 1.05f, 0.75f, 1f)
            batch.draw(tex, e.x, e.y, size, size, 0f, u0, 0f, u1, 1f)
            batch.setTint(1f, 1f, 1f, 1f)

            // HP sliver for damaged enemies
            if (e.alive && e.hpFraction() < 0.999f && !e.isBoss) {
                drawHealthBar(batch, textures, e.x, e.y - size * 0.62f, size * 0.8f, 6f, e.hpFraction(), 0xFF4757.toInt())
            }
        }
    }

    /** Shared camera reference (set by GLRenderer each frame). */
    var cameraRef: Camera = Camera()

    /** Procedural health bar from a 1px white glow texture. */
    private fun drawHealthBar(batch: SpriteBatch, textures: TextureManager, cx: Float, cy: Float,
                               w: Float, h: Float, fraction: Float, color: Int) {
        val glow = AssetRepository.fx("glow_shield") ?: return
        val tex = textures.get("fx_glow_shield", glow)
        batch.setTint(0.08f, 0.09f, 0.16f, 0.85f)
        batch.draw(tex, cx, cy, w, h)
        batch.setTint(Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f, 0.95f)
        batch.draw(tex, cx - w * (1f - fraction) / 2f, cy, w * fraction.coerceIn(0f, 1f), h)
        batch.setTint(1f, 1f, 1f, 1f)
    }

    /* ---------------- status icons above heads ---------------- */

    private fun drawStatusIcons(batch: SpriteBatch, textures: TextureManager) {
        val mgr = engine.system<com.towerbound.laststand.enemies.EnemyManager>() ?: return
        val combat = engine.system<com.towerbound.laststand.combat.CombatSystem>() ?: return
        for (e in mgr.active) {
            if (!e.alive) continue
            var iconX = e.x - 24f
            val iconY = e.y - e.radius * 2.6f
            for (kind in com.towerbound.laststand.combat.StatusKind.entries) {
                if (combat.statuses.has(e, kind)) {
                    val bmp = AssetRepository.fx("status_${kind.name.lowercase()}") ?: continue
                    val tex = textures.get("fx_status_${kind.name.lowercase()}", bmp)
                    batch.draw(tex, iconX, iconY, 26f, 26f)
                    iconX += 26f
                }
            }
        }
    }

    /* ---------------- projectiles ---------------- */

    private fun drawProjectiles(batch: SpriteBatch, textures: TextureManager, glowPass: Boolean) {
        val combat = engine.system<com.towerbound.laststand.combat.CombatSystem>() ?: return
        if (combat.projectiles.activeCount() == 0) return
        for (p in combat.projectiles.active) {
            val bmp = AssetRepository.fx("glow_${p.visual}") ?: continue
            val tex = textures.get("fx_glow_${p.visual}", bmp)
            if (glowPass) {
                // glow halo
                batch.setAlpha(0.55f)
                batch.draw(tex, p.x, p.y, p.radius * 6f, p.radius * 6f)
                batch.setAlpha(1f)
            } else {
                // core dot + motion streak
                val ang = Math.toDegrees(kotlin.math.atan2(p.dirY.toDouble(), p.dirX.toDouble()).toDouble()).toFloat()
                val streak = AssetRepository.fx("spark_${p.visual}")
                if (streak != null) {
                    val stex = textures.get("fx_spark_${p.visual}", streak)
                    val sl = p.radius * 3.6f
                    val px = p.x - p.dirX * sl * 0.5f
                    val py = p.y - p.dirY * sl * 0.5f
                    batch.draw(stex, px, py, sl, p.radius * 1.7f, ang)
                }
                batch.draw(tex, p.x, p.y, p.radius * 2.6f, p.radius * 2.6f)
            }
        }
    }

    /* ---------------- particles ---------------- */

    private fun drawParticles(batch: SpriteBatch, textures: TextureManager, additive: Boolean) {
        val fx = engine.system<com.towerbound.laststand.fx.ParticleManager>() ?: return
        for (p in fx.activeList) {
            if (p.additive != additive) continue
            val bmp = AssetRepository.fx(p.texture) ?: continue
            val tex = textures.get("fx_${p.texture}", bmp)
            val t = fx.lifeT(p)
            batch.setAlpha(p.alpha * (0.25f + 0.75f * t))
            batch.draw(tex, p.x, p.y, fx.sizeOf(p) * 2f, fx.sizeOf(p) * 2f, p.angle)
        }
        batch.setAlpha(1f)
    }

    /* ---------------- damage numbers ---------------- */

    private fun drawDamageNumbers(batch: SpriteBatch, text: TextAtlas) {
        val combat = engine.system<com.towerbound.laststand.combat.CombatSystem>() ?: return
        combat.damageNumbers.forEach { n ->
            val h = (if (n.isCrit) 46f else 30f) * n.scale()
            text.drawText(batch, n.value.toString(), n.x, n.y + n.offsetY(), h,
                Color.red(n.color) / 255f, Color.green(n.color) / 255f, Color.blue(n.color) / 255f, n.alpha())
        }
    }

    /* ---------------- clouds (parallax) ---------------- */

    private fun initClouds(world: com.towerbound.laststand.world.GameWorld) {
        val rng = com.towerbound.laststand.core.Rng(world.seed + 777L)
        repeat(7) {
            clouds.add(Cloud(
                x = rng.range(0f, world.width),
                y = rng.range(80f, world.height - 80f),
                speed = rng.range(6f, 16f),
                scale = rng.range(0.9f, 1.8f),
                name = if (rng.chance(0.5f)) "cloud_1" else "cloud_2"
            ))
        }
        cloudsInit = true
    }

    private fun drawClouds(batch: SpriteBatch, textures: TextureManager, camera: Camera) {
        val world = engine.world ?: return
        if (!cloudsInit) initClouds(world)
        batch.setTint(1f, 1f, 1f, 0.55f)
        for (c in clouds) {
            c.x += c.speed * (1f / 60f)
            if (c.x > world.width + 300f) c.x = -300f
            val bmp = AssetRepository.prop(c.name) ?: continue
            val tex = textures.get("prop_${c.name}", bmp)
            val parallax = 0.82f // slight depth difference vs ground
            val px = c.x + (camera.x - world.centerX) * (1f - parallax)
            val py = c.y + (camera.y - world.centerY) * (1f - parallax)
            if (!isVisible(camera, px, py, 300f)) continue
            batch.draw(tex, px, py - 60f, bmp.width * c.scale, bmp.height * c.scale)
        }
        batch.setTint(1f, 1f, 1f, 1f)
    }

    /* ---------------- world edge vignette ---------------- */

    private fun drawWorldEdge(batch: SpriteBatch, textures: TextureManager, camera: Camera, world: com.towerbound.laststand.world.GameWorld) {
        val glow = AssetRepository.fx("glow_void") ?: return
        val tex = textures.get("fx_glow_void", glow)
        batch.setTint(0f, 0f, 0.05f, 0.8f)
        // top edge shadow band
        batch.draw(tex, world.centerX, 0f, world.width * 1.4f, 260f)
        batch.draw(tex, world.centerX, world.height, world.width * 1.4f, 260f)
        batch.draw(tex, 0f, world.centerY, 260f, world.height * 1.4f)
        batch.draw(tex, world.width, world.centerY, 260f, world.height * 1.4f)
        batch.setTint(1f, 1f, 1f, 1f)
    }

    private fun isVisible(camera: Camera, x: Float, y: Float, size: Float): Boolean {
        val hw = camera.viewWidth / 2f + size
        val hh = camera.viewHeight / 2f + size
        return x > camera.x - hw && x < camera.x + hw && y > camera.y - hh && y < camera.y + hh
    }
}
