package com.towerbound.laststand.render

import com.towerbound.laststand.assets.AnimState
import com.towerbound.laststand.assets.AssetRepository
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.anim.AnimationController
import com.towerbound.laststand.meta.MetaSession
import kotlin.math.sin

/**
 * Animated pixel-art world scene rendered behind every non-gameplay screen
 * (main menu, hero select, shop, settings, collection, quests).
 *
 * Layered composition (design: MAIN MENU — complete rebuild):
 *   sky gradient + stars -> distant hills x2 -> drifting clouds ->
 *   ground meadow -> central tower -> animated hero idle ->
 *   foreground grass sway -> fireflies (additive).
 *
 * The hero is the currently selected hero, breathing/idle-animated next to
 * the tower. Everything is drawn with the shared SpriteBatch in one pass
 * plus a small additive pass for the fireflies and tower glow.
 */
class MenuSceneRenderer(private val engine: GameEngine) {

    private var time = 0f
    private val heroAnim = AnimationController(AnimState.IDLE)
    private var cachedHeroId: String? = null

    private class Cloud(var x: Float, val y: Float, val speed: Float, val scale: Float, val name: String)
    private class Firefly(val baseX: Float, val baseY: Float, val phase: Float, val speed: Float, val size: Float)

    private val clouds = ArrayList<Cloud>()
    private val fireflies = ArrayList<Firefly>()
    private val grass = ArrayList<FloatArray>() // x, scale, phase
    private var initialized = false

    /** Screen transition dim (0 = full scene, 1 = dark). */
    @Volatile var dim: Float = 0f

    fun render(batch: SpriteBatch, textures: TextureManager, camera: Camera, dt: Float) {
        if (!AssetRepository.generated) return
        time += dt
        if (!initialized) initScene(camera)
        heroAnim.update(dt)

        val vw = camera.viewWidth
        val vh = camera.viewHeight
        val left = -vw / 2f
        val bottom = -vh / 2f
        val horizonY = bottom + vh * 0.44f

        /* ---------- 1. sky ---------- */
        val sky = AssetRepository.fx("menu_sky") ?: AssetRepository.fx("glow_void")
        if (sky != null) {
            val tex = textures.get("menu_sky", sky, nearest = false)
            batch.draw(tex, 0f, 0f, vw + 8f, vh + 8f)
        }

        /* ---------- 2. distant hills (two parallax layers) ---------- */
        val hillsFar = AssetRepository.fx("menu_hills_far")
        if (hillsFar != null) {
            val tex = textures.get("menu_hills_far", hillsFar)
            val w = vw * 1.15f
            val h = vh * 0.30f
            batch.setAlpha(0.9f)
            batch.draw(tex, 0f, horizonY - h * 0.28f, w, h)
            batch.setAlpha(1f)
        }
        val hillsNear = AssetRepository.fx("menu_hills_near")
        if (hillsNear != null) {
            val tex = textures.get("menu_hills_near", hillsNear)
            val w = vw * 1.3f
            val h = vh * 0.24f
            batch.draw(tex, sin(time * 0.05f) * 6f, horizonY - h * 0.34f, w, h)
        }

        /* ---------- 3. drifting clouds ---------- */
        for (c in clouds) {
            c.x += c.speed * dt
            val totalW = vw + 420f
            var cx = c.x
            if (cx > vw / 2f + 220f) cx -= totalW
            if (cx < -vw / 2f - 220f) cx += totalW
            c.x = cx
            val bmp = AssetRepository.prop(c.name) ?: continue
            val tex = textures.get("prop_${c.name}", bmp)
            batch.setAlpha(0.85f)
            batch.draw(tex, cx, c.y, bmp.width * c.scale, bmp.height * c.scale)
            batch.setAlpha(1f)
        }

        /* ---------- 4. ground meadow ---------- */
        val terrain = AssetRepository.terrain(0L)
        val texTerrain = textures.get("terrain_base", terrain)
        val tileSize = vh * 0.34f
        var tx = left - tileSize / 2f
        while (tx < vw / 2f + tileSize) {
            batch.draw(texTerrain, tx, bottom - tileSize * 0.12f, tileSize, tileSize)
            tx += tileSize
        }

        /* ---------- 5. central tower ---------- */
        val tower = AssetRepository.towerStage(0)
        val texTower = textures.get("tower_0", tower)
        val towerH = vh * 0.46f
        val towerW = towerH * (tower.width.toFloat() / tower.height)
        val towerY = bottom + vh * 0.30f
        batch.draw(texTower, 0f, towerY, towerW, towerH)

        // ground shadow under tower
        val shadow = AssetRepository.fx("glow_void")
        if (shadow != null) {
            val texShadow = textures.get("fx_glow_shadow", shadow, nearest = false)
            batch.setTint(0f, 0f, 0.02f, 0.45f)
            batch.draw(texShadow, 0f, bottom + vh * 0.085f, towerW * 1.15f, vh * 0.05f)
            batch.setTint(1f, 1f, 1f, 1f)
        }

        /* ---------- 6. animated hero beside the tower ---------- */
        val heroId = currentHeroId()
        if (heroId != cachedHeroId) {
            cachedHeroId = heroId
            heroAnim.play(AnimState.IDLE, force = true)
        }
        val sheet = AssetRepository.heroSheet(heroId, heroAnim.state)
        if (sheet != null) {
            val tex = textures.get("hero_${sheet.designId}_${sheet.state.name}", sheet.bitmap)
            val u0 = sheet.frameU(heroAnim.frameFloat())
            val u1 = u0 + 1f / sheet.frameCount
            val size = vh * 0.36f
            val hx = -vw * 0.30f
            val hy = bottom + vh * 0.24f
            // subtle bob is already inside the idle animation frames
            batch.draw(tex, hx, hy, size, size, 0f, u0, 0f, u1, 1f)
        }

        /* ---------- 7. foreground grass sway ---------- */
        val grassBmp1 = AssetRepository.prop("grass_1")
        val grassBmp2 = AssetRepository.prop("grass_2")
        if (grassBmp1 != null && grassBmp2 != null) {
            val tex1 = textures.get("prop_grass_1", grassBmp1)
            val tex2 = textures.get("prop_grass_2", grassBmp2)
            for (g in grass) {
                val rot = sin(time * 1.7f + g[2]) * 8f
                val bmp = if ((g[2] * 1000).toInt() % 2 == 0) grassBmp1 else grassBmp2
                val tex = if (bmp === grassBmp1) tex1 else tex2
                val w = bmp.width * g[1] * 1.5f
                val h = bmp.height * g[1] * 1.5f
                batch.draw(tex, g[0], bottom + vh * 0.06f, w, h, rot)
            }
        }

        /* ---------- 8. additive glow pass: fireflies + tower core ---------- */
        batch.additiveBlending()
        val glow = AssetRepository.fx("glow_shield")
        if (glow != null) {
            val tex = textures.get("fx_glow_shield2", glow, nearest = false)
            // fireflies
            val pulse = 0.55f + 0.45f * sin(time * 2.2f)
            for (f in fireflies) {
                val fx = f.baseX + sin(time * f.speed + f.phase) * 26f
                val fy = f.baseY + sin(time * f.speed * 1.3f + f.phase * 2f) * 16f
                batch.setTint(1f, 0.95f, 0.55f, 0.55f * pulse)
                batch.draw(tex, fx, fy, f.size, f.size)
            }
            // tower energy pulse
            val tp = 0.4f + 0.25f * sin(time * 1.6f)
            batch.setTint(0.55f, 0.8f, 1f, tp)
            batch.draw(tex, 0f, towerY + towerH * 0.22f, towerW * 0.5f, towerW * 0.5f)
            batch.setTint(1f, 1f, 1f, 1f)
        }
        batch.normalBlending()

        /* ---------- 9. optional dim overlay for readability ---------- */
        if (dim > 0.01f) {
            val veil = AssetRepository.fx("glow_void")
            if (veil != null) {
                val tex = textures.get("fx_menu_dim", veil, nearest = false)
                batch.setTint(0.02f, 0.03f, 0.08f, dim.coerceIn(0f, 0.8f))
                batch.draw(tex, 0f, 0f, vw + 8f, vh + 8f)
                batch.setTint(1f, 1f, 1f, 1f)
            }
        }
    }

    private fun currentHeroId(): String =
        try {
            MetaSession.profile.selectedHero.ifEmpty { "ARIC" }
        } catch (_: Throwable) {
            "ARIC"
        }

    private fun initScene(camera: Camera) {
        val vw = camera.viewWidth
        val vh = camera.viewHeight
        // clouds across the sky band
        val cloudDefs = arrayOf(
            Triple(-0.36f, 0.72f, 14f), Triple(0.1f, 0.80f, 9f), Triple(0.42f, 0.66f, 18f),
            Triple(-0.05f, 0.55f, 11f), Triple(0.3f, 0.9f, 7f)
        )
        for ((i, c) in cloudDefs.withIndex()) {
            clouds.add(Cloud(
                x = c.first * vw,
                y = -vh / 2f + c.second * vh * 0.5f,
                speed = c.third,
                scale = 0.9f + (i % 3) * 0.28f,
                name = if (i % 2 == 0) "cloud_1" else "cloud_2"
            ))
        }
        // fireflies around the tower and hero
        for (i in 0 until 14) {
            val ang = i / 14f * 6.28318f
            fireflies.add(Firefly(
                baseX = sin(ang) * vw * 0.33f,
                baseY = -vh / 2f + vh * (0.32f + (i % 5) * 0.07f),
                phase = i * 1.7f,
                speed = 0.6f + (i % 4) * 0.22f,
                size = 18f + (i % 3) * 9f
            ))
        }
        // foreground grass row
        var x = -vw / 2f + 30f
        var i = 0
        while (x < vw / 2f - 10f) {
            grass.add(floatArrayOf(x, 0.9f + (i % 4) * 0.22f, i * 1.31f))
            x += vw / 9f
            i++
        }
        initialized = true
    }
}
