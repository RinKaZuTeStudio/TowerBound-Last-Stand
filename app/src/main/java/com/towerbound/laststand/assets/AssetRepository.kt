package com.towerbound.laststand.assets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.LruCache
import com.towerbound.laststand.core.GameLog

/**
 * Central procedural asset cache. All visuals in the game come from here —
 * there are no external image files anywhere in the project.
 *
 * Memory policy:
 *  - FX/icon/environment assets are small and always resident.
 *  - Hero sheets (256px) are generated lazily with a small LRU (menu + active hero).
 *  - Enemy sheets are generated at 128px from the 256px master grid to keep
 *    10 archetypes + bosses under ~12 MB of bitmap memory.
 */
object AssetRepository {

    /* ---------- small always-on assets ---------- */

    private val fxAssets = HashMap<String, Bitmap>()
    private val explosionStrip: Bitmap by lazy { stripOf(FxPainter.explosionFrames()) }
    private val waterStrip: Bitmap by lazy { stripOf(EnvironmentPainter.waterFrames()) }

    private val towerStages = mutableListOf<Bitmap>()
    private val turretBitmap: Bitmap by lazy { TowerPainter.generateTurret() }

    // keyed by enemy design id
    private val enemySheets = HashMap<String, HashMap<AnimState, SpriteSheet>>()
    // hero sheets keyed by hero id (LRU capped)
    private val heroSheetCache = object : LruCache<String, HashMap<AnimState, SpriteSheet>>(3) {}
    private val portraits = LruCache<String, Bitmap>(10)

    private val terrainCache = LruCache<Long, Bitmap>(6)
    private val propCache = LruCache<String, Bitmap>(24)
    private val iconCache = LruCache<String, Bitmap>(64)

    @Volatile var generated: Boolean = false
        private set

    /* ---------- startup generation ---------- */

    /** Generate the core asset set; [progress] receives 0..1 updates. */
    fun generateCore(progress: (Float) -> Unit) {
        if (generated) { progress(1f); return }
        val t0 = System.currentTimeMillis()

        // FX glows per damage flavor
        val glows = mapOf(
            "fire" to Color.parseColor("#FF6D00"),
            "frost" to Color.parseColor("#4FC3F7"),
            "poison" to Color.parseColor("#7CB342"),
            "magic" to Color.parseColor("#B388FF"),
            "heal" to Color.parseColor("#69F0AE"),
            "shield" to Color.parseColor("#4DA6FF"),
            "impact" to Color.parseColor("#FFD54F"),
            "void" to Color.parseColor("#7C4DFF")
        )
        var done = 0f
        val totalSteps = 22f
        for ((k, col) in glows) {
            fxAssets["glow_$k"] = FxPainter.softGlow(col)
            fxAssets["spark_$k"] = FxPainter.spark(col)
            done++; progress(done / totalSteps)
        }
        fxAssets["smoke"] = FxPainter.smoke()
        fxAssets["ring_red"] = FxPainter.ring(Color.parseColor("#FF5252"))
        fxAssets["ring_blue"] = FxPainter.ring(Color.parseColor("#4FC3F7"))
        fxAssets["ring_gold"] = FxPainter.ring(Color.parseColor("#FFC947"))
        fxAssets["slash_white"] = FxPainter.slash(Color.WHITE)
        fxAssets["slash_gold"] = FxPainter.slash(Color.parseColor("#FFC947"))
        fxAssets["star_gold"] = FxPainter.star(Color.parseColor("#FFC947"))
        fxAssets["star_green"] = FxPainter.star(Color.parseColor("#7CFF6B"))
        fxAssets["xp_orb"] = FxPainter.xpOrb()
        fxAssets["gold_coin"] = FxPainter.goldCoin()
        fxAssets["shard_purple"] = FxPainter.shard(Color.parseColor("#9C27B0"))
        done += 12; progress(done / totalSteps)

        // status icons
        for (k in StatusIconKind.entries) {
            fxAssets["status_${k.name.lowercase()}"] = FxPainter.statusIcon(k)
        }
        done += StatusIconKind.entries.size; progress(done / totalSteps)

        // tower damage stages
        for (stage in 0 until 5) towerStages.add(TowerPainter.generate(stage))

        // environment props
        propCache.put("tree_1", EnvironmentPainter.tree(7L))
        propCache.put("tree_2", EnvironmentPainter.tree(23L))
        propCache.put("bush_1", EnvironmentPainter.bush(3L))
        propCache.put("bush_2", EnvironmentPainter.bush(9L))
        propCache.put("rock_1", EnvironmentPainter.rock(5L))
        propCache.put("rock_2", EnvironmentPainter.rock(15L))
        propCache.put("grass_1", EnvironmentPainter.grassTuft(11L))
        propCache.put("grass_2", EnvironmentPainter.grassTuft(29L))
        propCache.put("cloud_1", EnvironmentPainter.cloud(13L))
        propCache.put("cloud_2", EnvironmentPainter.cloud(31L))
        done += 10; progress(done / totalSteps)

        // enemy sheets at gameplay resolution
        for (design in EnemyDesigns.all) {
            generateEnemySheets(design)
            done += 0.5f
        }
        for (boss in EnemyDesigns.bosses) {
            generateEnemySheets(boss, 192)
        }
        progress(0.95f)

        generated = true
        GameLog.i("Assets", "core generation done in ${System.currentTimeMillis() - t0} ms")
        progress(1f)
    }

    private fun generateEnemySheets(design: CharacterDesign, size: Int = 128) {
        val map = HashMap<AnimState, SpriteSheet>()
        for (state in AnimState.entries) {
            map[state] = SpriteSheet.generate(design, state, size)
        }
        enemySheets[design.id] = map
    }

    /* ---------- accessors ---------- */

    fun fx(name: String): Bitmap? = fxAssets[name]

    fun explosionFrameCount(): Int = 8
    fun explosionStrip(): Bitmap = explosionStrip
    fun explosionFrame(i: Int): Bitmap {
        val w = explosionStrip.width / 8
        return Bitmap.createBitmap(explosionStrip, i * w, 0, w, explosionStrip.height)
    }

    fun waterFrameCount(): Int = 4
    fun waterFrame(i: Int): Bitmap {
        val w = waterStrip.width / 4
        return Bitmap.createBitmap(waterStrip, (i % 4) * w, 0, w, waterStrip.height)
    }

    fun towerStage(stage: Int): Bitmap =
        towerStages[stage.coerceIn(0, towerStages.size - 1)]

    fun turret(): Bitmap = turretBitmap

    fun enemySheet(designId: String, state: AnimState): SpriteSheet? =
        enemySheets[designId]?.get(state)

    /** Hero sheets: full 256px, LRU-cached, generated on demand. */
    fun heroSheet(heroId: String, state: AnimState): SpriteSheet? {
        val map = heroSheetCache.get(heroId) ?: run {
            val design = HeroDesigns.byId(heroId)
            val m = HashMap<AnimState, SpriteSheet>()
            for (s in AnimState.entries) {
                m[s] = SpriteSheet.generate(design, s, 256)
            }
            heroSheetCache.put(heroId, m)
            m
        }
        return map[state]
    }

    fun releaseHeroSheet(heroId: String) {
        heroSheetCache.remove(heroId)
    }

    /** Single-frame portrait for menus (cheaper than full sheet). */
    fun portrait(heroId: String): Bitmap {
        portraits.get(heroId)?.let { return it }
        val design = if (HeroDesigns.all.any { it.id == heroId }) HeroDesigns.byId(heroId)
        else EnemyDesigns.byId(heroId) ?: HeroDesigns.ARIC
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        CharacterPainter(design).paint(canvas, Pose(walkPhase = 0.2f, bob = 1f))
        portraits.put(heroId, bmp)
        return bmp
    }

    fun terrain(seed: Long): Bitmap {
        terrainCache.get(seed)?.let { return it }
        val bmp = EnvironmentPainter.terrainTile(seed)
        terrainCache.put(seed, bmp)
        return bmp
    }

    fun prop(name: String): Bitmap? = propCache.get(name)

    fun icon(key: String, symbol: SymbolPainter.Symbol, color: Int, bg: Int): Bitmap {
        iconCache.get(key)?.let { return it }
        val bmp = SymbolPainter.icon(symbol, color, bg)
        iconCache.put(key, bmp)
        return bmp
    }

    /** Total estimated bitmap bytes (debug overlay). */
    fun estimatedBytes(): Long {
        var total = 0L
        for (b in fxAssets.values) total += b.byteCount
        total += explosionStrip.byteCount + waterStrip.byteCount + turretBitmap.byteCount
        for (b in towerStages) total += b.byteCount
        for (m in enemySheets.values) for (s in m.values) total += s.bitmap.byteCount
        heroSheetCache.snapshot().values.forEach { m -> for (s in m.values) total += s.bitmap.byteCount }
        return total
    }

    /** Full cleanup when memory pressure demands it (keeps core fx only). */
    fun trimMemory(aggressive: Boolean) {
        heroSheetCache.evictAll()
        portraits.evictAll()
        if (aggressive) {
            propCache.evictAll()
            terrainCache.evictAll()
        }
        GameLog.i("Assets", "trimMemory(aggressive=$aggressive)")
    }

    private fun stripOf(frames: List<Bitmap>): Bitmap {
        val w = frames[0].width
        val h = frames[0].height
        val strip = Bitmap.createBitmap(w * frames.size, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(strip)
        for (i in frames.indices) {
            c.drawBitmap(frames[i], (i * w).toFloat(), 0f, null)
        }
        return strip
    }
}
