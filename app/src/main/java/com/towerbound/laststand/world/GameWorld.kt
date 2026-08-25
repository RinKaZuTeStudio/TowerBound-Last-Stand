package com.towerbound.laststand.world

import com.towerbound.laststand.core.Rng
import kotlin.math.sqrt

/**
 * Circular static obstacle used for collision + visual prop placement.
 */
data class Obstacle(val x: Float, val y: Float, val radius: Float, val kind: String)

/**
 * The battlefield: bounds, obstacles, spawn ring, safe zone. Deterministic
 * from a seed; the architecture supports future biomes by parameterizing
 * the palette and prop mix (see [Biome]).
 */
class GameWorld(val seed: Long, val width: Float = 2400f, val height: Float = 1600f) {

    val centerX = width / 2f
    val centerY = height / 2f

    /** Central tower collision radius. */
    val towerRadius = 90f

    /** Player spawns just north of the tower (+Y = up on screen). */
    val playerSpawnX = centerX
    val playerSpawnY = centerY + 170f

    /** Enemies spawn on this ring (just outside the visible edge). */
    val spawnRingRadius: Float
        get() = sqrt(width * width + height * height) / 2f + 60f

    /** Safe zone around the tower where enemies can't spawn. */
    val safeZoneRadius = 320f

    val obstacles = mutableListOf<Obstacle>()

    /** Terrain visual tiles: [x, y, seed] per 256-unit tile. */
    data class TerrainTile(val x: Float, val y: Float, val seed: Long, val variant: Int)
    val terrainTiles = mutableListOf<TerrainTile>()
    val tileSize = 256f

    /** Decorative prop placements: name, x, y, scale, sway phase. */
    data class PropPlacement(val name: String, val x: Float, val y: Float, val scale: Float, val swayPhase: Float)
    val props = mutableListOf<PropPlacement>()

    val biome: Biome = Biome.VERDANT

    enum class Biome(val propMix: List<String>) {
        VERDANT(listOf("tree_1", "tree_2", "bush_1", "bush_2", "rock_1", "rock_2", "grass_1", "grass_2"))
    }

    init {
        val rng = Rng(seed)

        // ---- terrain tiles ----
        val cols = (width / tileSize).toInt() + 1
        val rows = (height / tileSize).toInt() + 1
        for (ty in 0 until rows) {
            for (tx in 0 until cols) {
                val x = tx * tileSize + tileSize / 2f
                val y = ty * tileSize + tileSize / 2f
                val variant = rng.nextInt(4)
                terrainTiles.add(TerrainTile(x, y, seed + variant * 101L, variant))
            }
        }

        // ---- obstacles & props ----
        val treeCount = 26
        var placed = 0
        var attempts = 0
        while (placed < treeCount && attempts < 400) {
            attempts++
            val x = rng.range(90f, width - 90f)
            val y = rng.range(90f, height - 90f)
            val dTower = dist(x, y, centerX, centerY)
            if (dTower < 380f) continue
            if (obstacles.any { dist(x, y, it.x, it.y) < it.radius + 150f }) continue
            val tree = if (rng.chance(0.5f)) "tree_1" else "tree_2"
            obstacles.add(Obstacle(x, y, 46f, tree))
            props.add(PropPlacement(tree, x, y, 1f, rng.nextFloat() * 6.28f))
            placed++
        }
        // bushes (decorative, no collision)
        repeat(16) {
            val x = rng.range(60f, width - 60f)
            val y = rng.range(60f, height - 60f)
            if (dist(x, y, centerX, centerY) < 300f) return@repeat
            val bush = if (rng.chance(0.5f)) "bush_1" else "bush_2"
            props.add(PropPlacement(bush, x, y, rng.range(0.8f, 1.25f), rng.nextFloat() * 6.28f))
        }
        // rocks (collide)
        repeat(10) {
            val x = rng.range(70f, width - 70f)
            val y = rng.range(70f, height - 70f)
            if (dist(x, y, centerX, centerY) < 340f) return@repeat
            val rock = if (rng.chance(0.5f)) "rock_1" else "rock_2"
            obstacles.add(Obstacle(x, y, 30f, rock))
            props.add(PropPlacement(rock, x, y, rng.range(0.8f, 1.3f), rng.nextFloat() * 6.28f))
        }
        // grass tufts (animated decoration)
        repeat(70) {
            val x = rng.range(30f, width - 30f)
            val y = rng.range(30f, height - 30f)
            val grass = if (rng.chance(0.5f)) "grass_1" else "grass_2"
            props.add(PropPlacement(grass, x, y, rng.range(0.9f, 1.4f), rng.nextFloat() * 6.28f))
        }
    }

    /* ---------------- queries ---------------- */

    fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    /** Clamp position into world bounds. Returns adjusted coords. */
    fun clampToBounds(x: Float, y: Float, radius: Float): FloatArray {
        return floatArrayOf(
            x.coerceIn(radius, width - radius),
            y.coerceIn(radius, height - radius)
        )
    }

    /** Circle-vs-obstacles + tower + bounds resolution for a moving entity. */
    fun resolveCollision(x: Float, y: Float, radius: Float): FloatArray {
        var nx = x.coerceIn(radius, width - radius)
        var ny = y.coerceIn(radius, height - radius)

        val dT = dist(nx, ny, centerX, centerY)
        val minT = towerRadius + radius
        if (dT < minT && dT > 0.001f) {
            nx = centerX + (nx - centerX) / dT * minT
            ny = centerY + (ny - centerY) / dT * minT
        }
        for (o in obstacles) {
            val minD = o.radius + radius
            val d = dist(nx, ny, o.x, o.y)
            if (d < minD && d > 0.001f) {
                nx = o.x + (nx - o.x) / d * minD
                ny = o.y + (ny - o.y) / d * minD
            }
        }
        return floatArrayOf(nx, ny)
    }

    /**
     * Validated spawn point on the map ring. Rejects: inside tower safe
     * zone, too close to the player, overlapping occupied points, outside
     * the playable area (design safety rules).
     */
    fun findSpawnPoint(rng: Rng, occupied: List<Pair<Float, Float>>, minSeparation: Float,
                       avoidX: Float, avoidY: Float, avoidRadius: Float): FloatArray {
        for (attempt in 0 until 24) {
            val a = rng.nextFloat() * 6.28318f
            val r = spawnRingRadius * rng.range(0.86f, 1f)
            val x = (centerX + kotlin.math.cos(a) * r).coerceIn(40f, width - 40f)
            val y = (centerY + kotlin.math.sin(a) * r).coerceIn(40f, height - 40f)
            if (dist(x, y, centerX, centerY) < safeZoneRadius) continue
            if (dist(x, y, avoidX, avoidY) < avoidRadius) continue
            var ok = true
            for ((ox, oy) in occupied) {
                if (dist(x, y, ox, oy) < minSeparation) { ok = false; break }
            }
            if (ok) return floatArrayOf(x, y)
        }
        val candidates = listOf(
            floatArrayOf(40f, centerY), floatArrayOf(width - 40f, centerY),
            floatArrayOf(centerX, 40f), floatArrayOf(centerX, height - 40f)
        )
        return candidates.maxByOrNull { dist(it[0], it[1], avoidX, avoidY) } ?: candidates[0]
    }

    fun inPlayableArea(x: Float, y: Float, margin: Float = 0f): Boolean =
        x > margin && y > margin && x < width - margin && y < height - margin
}
