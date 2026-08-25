package com.towerbound.laststand.ui.screens

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.towerbound.laststand.MainActivity
import com.towerbound.laststand.R
import com.towerbound.laststand.assets.SymbolPainter
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.core.GameLog
import com.towerbound.laststand.core.GameState
import com.towerbound.laststand.core.LoadingPipeline
import com.towerbound.laststand.combat.CombatSystem
import com.towerbound.laststand.enemies.EnemyManager
import com.towerbound.laststand.fx.ParticleManager
import com.towerbound.laststand.gameplay.PlayerController
import com.towerbound.laststand.tower.TowerSystem
import com.towerbound.laststand.waves.WaveSystem
import com.towerbound.laststand.input.ActionButton
import com.towerbound.laststand.input.VirtualJoystick
import com.towerbound.laststand.heroes.AbilitySystem
import com.towerbound.laststand.heroes.ShadowSystem
import com.towerbound.laststand.progression.XpSystem
import com.towerbound.laststand.meta.MetaSession
import com.towerbound.laststand.audio.AudioDirector
import com.towerbound.laststand.haptics.Haptics
import com.towerbound.laststand.perf.PerformanceManager
import com.towerbound.laststand.ui.UiKit

/**
 * Run flow controller: orchestrates LOADING_GAME -> GAMEPLAY -> GAME_OVER ->
 * RESULTS. Owns the gameplay HUD (joystick + action buttons + status) inside
 * the activity's HUD layer. One instance per activity; reachable via [get].
 */
class GameFlow private constructor(private val activity: MainActivity) {

    private val engine = GameEngine.get()
    private var hudContainer: FrameLayout? = null
    private var joystick: VirtualJoystick? = null
    private var dashButton: ActionButton? = null
    private var abilityButton: ActionButton? = null
    private var ultimateButton: ActionButton? = null
    private var hudLastUpdate = 0L
    private var hudStatus: android.widget.TextView? = null

    private val playerController = PlayerController(engine.input)

    /** Run statistics for the results screen. */
    val runStats = RunStats()

    /** Live level-up screens (closed on card pick or run end). */
    private val levelUpScreens = mutableListOf<LevelUpScreen>()

    private fun removeLevelUpScreen(screen: LevelUpScreen) {
        levelUpScreens.remove(screen)
        activity.screenManager.remove(screen)
    }

    class RunStats {
        var kills: Int = 0
        var gold: Int = 0
        var damageDealt: Float = 0f
        var wave: Int = 1
        var time: Float = 0f
        var xp: Long = 0
        fun reset() { kills = 0; gold = 0; damageDealt = 0f; wave = 1; time = 0f; xp = 0 }
    }

    /** Hero XP from the most recent finished run (results screen readout). */
    var lastRunXp: Long = 0

    companion object {
        @Volatile private var instance: GameFlow? = null
        fun get(): GameFlow = instance ?: throw IllegalStateException("GameFlow not attached")
        fun attach(activity: MainActivity): GameFlow {
            val flow = instance
            if (flow != null && flow.activity === activity) return flow
            val nf = GameFlow(activity)
            instance = nf
            return nf
        }
    }

    fun hud(): FrameLayout? = hudContainer

    /** Current hero for retry flows. */
    fun engineHeroId(): String = engine.currentHeroId

    /** Begin a run: register systems, run-specific loading, enter GAMEPLAY.
     * Passing [resume] restores a crashed run's wave progress. */
    fun startRun(activity: MainActivity, heroId: String, resume: org.json.JSONObject? = null) {
        engine.startRun(heroId)
        runStats.reset()
        lastRunXp = 0
        if (resume != null) {
            runStats.wave = resume.optInt("wave", 1).coerceAtLeast(1)
            runStats.kills = resume.optInt("kills", 0)
            runStats.gold = resume.optInt("gold", 0)
        }
        // permanent hero progression + equipped skin apply to the run
        playerController.metaStatScale = MetaSession.profile.heroProgress(heroId).statScale()
        playerController.skinTint = MetaSession.profile.inventory.equippedSkinFor(heroId).tint
        AudioDirector.setMusicSeed(engine.currentRunSeed)
        AudioDirector.setMood(AudioDirector.MusicMood.BATTLE)
        val loading = LoadingScreen(activity)
        activity.screenManager.replace(loading)

        val pipeline = LoadingPipeline("run-${engine.currentRunSeed}")
        pipeline
            .task(activity.getString(R.string.loading_battlefield), 2f) { _ ->
                // Pre-generate the hero sheet for the selected hero (256px).
                com.towerbound.laststand.assets.AssetRepository.heroSheet(heroId, com.towerbound.laststand.assets.AnimState.IDLE)
                Thread.sleep(30)
            }
            .task(activity.getString(R.string.loading_systems), 1f) { _ ->
                Thread.sleep(30)
            }

        pipeline.start(object : LoadingPipeline.Listener {
            override fun onProgress(fraction: Float, statusText: String) {
                loading.setProgress(fraction, statusText)
            }
            override fun onComplete() {
                activity.runOnUiThread {
                    // register systems in update order: combat, fx, tower, enemies, player, waves
                    val combat = engine.system<CombatSystem>() ?: CombatSystem().also { engine.registerSystem(it) }
                    val particles = engine.system<ParticleManager>() ?: ParticleManager().also { engine.registerSystem(it) }
                    val towerSystem = engine.system<TowerSystem>() ?: TowerSystem().also { engine.registerSystem(it) }
                    val enemyManager = engine.system<EnemyManager>() ?: EnemyManager().also { engine.registerSystem(it) }
                    val waveSystem = engine.system<WaveSystem>() ?: WaveSystem().also { engine.registerSystem(it) }
                    val xpSystem = engine.system<XpSystem>() ?: XpSystem().also { engine.registerSystem(it) }
                    val abilitySystem = engine.system<AbilitySystem>() ?: AbilitySystem().also { engine.registerSystem(it) }
                    val shadowSystem = engine.system<ShadowSystem>() ?: ShadowSystem().also { engine.registerSystem(it) }
                    val bossSystem = engine.system<com.towerbound.laststand.bosses.BossSystem>() ?: com.towerbound.laststand.bosses.BossSystem().also { engine.registerSystem(it) }
                    val envSystem = engine.system<com.towerbound.laststand.env.EnvironmentSystem>() ?: com.towerbound.laststand.env.EnvironmentSystem().also { engine.registerSystem(it) }
                    shadowSystem.fx = particles
                    combat.fx = particles
                    towerSystem.fx = particles
                    towerSystem.combat = combat
                    enemyManager.fx = particles
                    enemyManager.combat = combat
                    enemyManager.onEnemyDeath = { enemy ->
                        xpSystem.onEnemyDeath(enemy, engine)
                        runStats.kills++
                        runStats.gold += enemy.def.goldValue * (if (enemy.isElite) 3 else 1)
                        MetaSession.quests.onEnemyKilled(1)
                        AudioDirector.play(AudioDirector.Sfx.ENEMY_DEATH)
                        Haptics.fire(Haptics.Preset.LIGHT_HIT, 0.5f)
                    }
                    // projectile hit reporting -> damage numbers + particles
                    combat.projectiles.onDamageDealt = { target, amount, isCrit, source ->
                        combat.reportHit(target, amount, isCrit, source)
                    }
                    combat.projectiles.onProjectileImpact = { x, y, visual, isCrit ->
                        particles.emitImpact(x, y, if (isCrit) 14 else 7, visual)
                    }
                    // combatant provider: team 0 shoots enemies (Phase 9), team 1 shoots player+tower
                    engine.targetProvider = { team ->
                        if (team == 0) emptyList()
                        else listOfNotNull(
                            playerController.player?.takeIf { it.alive },
                            towerSystem.tower?.takeIf { it.alive }
                        )
                    }
                    abilitySystem.onAbilityCast = { ability ->
                        AudioDirector.play(if (ability.isUltimate) AudioDirector.Sfx.ULTIMATE else AudioDirector.Sfx.ABILITY)
                        Haptics.fire(if (ability.isUltimate) Haptics.Preset.ULTIMATE else Haptics.Preset.ABILITY)
                        if (ability.isUltimate) activity.gameRenderer?.camera?.shake(10f, 0.4f)
                    }

                    // wave banners
                    waveSystem.onWaveStarted = { wave, isElite, isBoss, event ->
                        activity.runOnUiThread {
                            val label = when {
                                isBoss -> activity.getString(R.string.boss_incoming)
                                isElite -> activity.getString(R.string.elite_wave)
                                else -> activity.getString(R.string.hud_wave, wave)
                            }
                            showBanner(label, if (isBoss) 0xFFFF5252.toInt() else if (isElite) 0xFFFFC947.toInt() else 0xFF00E5FF.toInt())
                        }
                        AudioDirector.play(AudioDirector.Sfx.WAVE_START)
                        Haptics.fire(if (isBoss) Haptics.Preset.BOSS else Haptics.Preset.WAVE_START)
                    }
                    waveSystem.onWaveCleared = { wave ->
                        runStats.wave = wave + 1
                        MetaSession.quests.onWaveSurvived(wave)
                    }
                    bossSystem.onBossSpawn = { name ->
                        activity.runOnUiThread { showBanner(name, 0xFFFF5252.toInt(), large = true) }
                        AudioDirector.setMood(AudioDirector.MusicMood.BOSS)
                        AudioDirector.play(AudioDirector.Sfx.BOSS_ROAR)
                        Haptics.fire(Haptics.Preset.BOSS)
                    }
                    bossSystem.onBossPhase = { phase ->
                        activity.runOnUiThread { showBanner("PHASE $phase", 0xFFFF8A65.toInt()) }
                    }
                    bossSystem.onBossDefeated = { defeated ->
                        runStats.kills += 5
                        runStats.gold += 120
                        xpSystem.addXp(140f)
                        AudioDirector.setMood(AudioDirector.MusicMood.BATTLE)
                        AudioDirector.play(AudioDirector.Sfx.EXPLOSION)
                        Haptics.fire(Haptics.Preset.EXPLOSION)
                        MetaSession.achievements.report(
                            com.towerbound.laststand.meta.AchievementManager.Metric.BOSSES, 1
                        )
                    }
                    // boss telegraph rings -> world renderer
                    activity.gameRenderer?.worldRenderer?.let { wr ->
                        bossSystem.onTelegraph = { x, y, r, t -> wr.pushTelegraph(x, y, r, t) }
                    }

                    // level-up flow: pause + cards
                    xpSystem.onUpgradeChosen = { _, _ ->
                        MetaSession.achievements.report(
                            com.towerbound.laststand.meta.AchievementManager.Metric.UPGRADES_CHOSEN, 1
                        )
                    }
                    xpSystem.onLevelUp = { lvl, offers ->
                        activity.runOnUiThread {
                            AudioDirector.play(AudioDirector.Sfx.LEVEL_UP)
                            Haptics.fire(Haptics.Preset.LEVEL_UP)
                            if (offers.isEmpty()) {
                                // no upgrades left: grant heal instead
                                playerController.player?.heal(30f)
                                xpSystem.choose("", engine).let { /* no-op */ }
                                return@runOnUiThread
                            }
                            lateinit var screen: LevelUpScreen
                            screen = LevelUpScreen(activity, lvl, offers) { upgradeId ->
                                AudioDirector.play(AudioDirector.Sfx.CARD_PICK)
                                Haptics.fire(Haptics.Preset.CLICK)
                                xpSystem.choose(upgradeId, engine)
                                removeLevelUpScreen(screen)
                            }
                            activity.screenManager.push(screen)
                            levelUpScreens.add(screen)
                        }
                    }
                    // tower destroyed -> run over
                    towerSystem.onTowerDamaged = { fraction ->
                        // game-feel: shake scales with lost HP
                        activity.gameRenderer?.camera?.shake((1f - fraction) * 6f + 2f, 0.2f)
                        AudioDirector.play(AudioDirector.Sfx.TOWER_HIT)
                        Haptics.fire(Haptics.Preset.HEAVY_HIT, (1f - fraction).coerceIn(0.3f, 1f))
                    }
                    towerSystem.onTowerDestroyed = {
                        activity.runOnUiThread { endRun() }
                    }
                    engine.registerSystem(playerController)
                    activity.screenManager.clear()
                    ensureHud()
                    // crash-recovery resume: jump waves ahead
                    if (resume != null) {
                        waveSystem.resumeFromWave = resume.optInt("wave", 1).coerceAtLeast(1)
                    }
                    val world = engine.world
                    activity.gameRenderer?.camera?.let { cam ->
                        if (world != null) {
                            cam.worldW = world.width
                            cam.worldH = world.height
                            cam.snapTo(world.playerSpawnX, world.playerSpawnY)
                        }
                    }
                    engine.stateManager.transition(GameState.GAMEPLAY)
                    GameLog.i("GameFlow", "run started, hero=$heroId")
                    // first-run tutorial
                    if (!MetaSession.profile.tutorialDone) {
                        activity.runOnUiThread {
                            activity.screenManager.push(TutorialOverlay(activity, onDone = {}))
                        }
                    }
                    // dynamic quality + debug overlay
                    PerformanceManager.applyQuality(
                        com.towerbound.laststand.meta.GameSettings.get(),
                        com.towerbound.laststand.meta.GameSettings.get().quality
                    )
                    PerformanceManager.ensureOverlay(activity)
                }
            }
            override fun onTaskError(taskName: String, error: Throwable) {}
        })
    }

    /** Build/attach the gameplay HUD overlay. */
    @SuppressLint("ClickableViewAccessibility")
    private fun ensureHud() {
        if (hudContainer != null) return
        val root = activity.findViewById<FrameLayout>(R.id.hud_layer) ?: return
        val container = FrameLayout(activity)
        root.addView(container, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        hudContainer = container

        // --- virtual joystick (full-screen touch catcher, movement side) ---
        val settings = com.towerbound.laststand.meta.GameSettings.get()
        val stick = VirtualJoystick(activity, engine.input, VirtualJoystick.Config(
            baseRadiusDp = 78f * settings.joystickScale,
            knobRadiusDp = 34f * settings.joystickScale
        ))
        container.addView(stick, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        joystick = stick

        // --- action buttons, bottom-right arc ---
        val dp = { v: Float -> UiKit.dp(activity, v) }
        fun actionParams(marginRightDp: Float, marginBottomDp: Float, sizeDp: Float): FrameLayout.LayoutParams {
            val lp = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            lp.gravity = Gravity.BOTTOM or Gravity.END
            lp.rightMargin = dp(marginRightDp)
            lp.bottomMargin = dp(marginBottomDp)
            return lp
        }

        val abilityColor = 0xFF00E5FF.toInt()
        val ultColor = 0xFFFFB300.toInt()
        val dashColor = 0xFF7C4DFF.toInt()

        val ability = ActionButton(activity, SymbolPainter.Symbol.BATTLE_CRY, abilityColor) {
            engine.input.queueAbility()
        }
        container.addView(ability, actionParams(18f, 96f, 68f))

        val ultimate = ActionButton(activity, SymbolPainter.Symbol.STAR, ultColor) {
            engine.input.queueUltimate()
        }
        container.addView(ultimate, actionParams(96f, 26f, 80f))

        val dash = ActionButton(activity, SymbolPainter.Symbol.DASH, dashColor) {
            engine.input.queueDash()
        }
        container.addView(dash, actionParams(18f, 22f, 56f))

        abilityButton = ability
        ultimateButton = ultimate
        dashButton = dash

        // --- top HUD: wave / enemies / tower HP + pause (top-right) ---
        hudStatus = android.widget.TextView(activity).apply {
            setTextColor(0xFFF4F6FF.toInt())
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 2f, android.graphics.Color.argb(190, 0, 0, 0))
            gravity = android.view.Gravity.START
        }
        val statusLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        )
        statusLp.topMargin = dp(12f)
        statusLp.leftMargin = dp(14f)
        container.addView(hudStatus, statusLp)

        val pause = android.widget.ImageButton(activity).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x66141830.toInt())
                cornerRadius = dp(22f).toFloat()
            }
            setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
            contentDescription = context.getString(R.string.hud_pause)
            setOnClickListener {
                com.towerbound.laststand.ui.UiSfx.click(activity)
                Haptics.fire(Haptics.Preset.CLICK)
                if (engine.stateManager.current == GameState.GAMEPLAY ||
                    engine.stateManager.current == GameState.BOSS_FIGHT) {
                    activity.screenManager.push(PauseScreen(activity))
                }
            }
        }
        val pauseLp = FrameLayout.LayoutParams(dp(44f), dp(44f), Gravity.TOP or Gravity.END)
        pauseLp.topMargin = dp(10f)
        pauseLp.rightMargin = dp(12f)
        container.addView(pause, pauseLp)

        // periodic HUD refresh (cooldown sweeps + status line) from sim ticks
        var snapshotTimer = 0f
        engine.hudUpdateHook = {
            val now = System.currentTimeMillis()
            if (now - hudLastUpdate > 100) {
                hudLastUpdate = now
                val p = playerController.player
                val abilities = engine.system<AbilitySystem>()
                val waves = engine.system<WaveSystem>()
                val enemies = engine.system<EnemyManager>()
                val tower = engine.system<TowerSystem>()?.tower
                PerformanceManager.onHudTick(engine)
                // crash-recovery snapshot every ~15 s of sim time
                snapshotTimer += 0.1f
                if (snapshotTimer >= 15f && engine.stateManager.current == GameState.GAMEPLAY) {
                    snapshotTimer = 0f
                    saveRunSnapshot()
                }
                activity.runOnUiThread {
                    dashButton?.cooldownFraction = p?.dashCooldownFraction() ?: 0f
                    abilityButton?.cooldownFraction = abilities?.activeCooldownFraction() ?: 0f
                    ultimateButton?.cooldownFraction = abilities?.ultimateCooldownFraction() ?: 0f
                    dashButton?.postInvalidate()
                    abilityButton?.postInvalidate()
                    ultimateButton?.postInvalidate()
                    hudStatus?.text = buildString {
                        append(activity.getString(R.string.hud_wave, waves?.currentWave ?: 1))
                        append("   ").append(activity.getString(R.string.hud_enemies, enemies?.activeCount() ?: 0))
                        if (tower != null && tower.alive) {
                            append("   ").append(activity.getString(R.string.hud_tower))
                            append(" ").append((tower.hpFraction() * 100).toInt()).append("%")
                        }
                    }
                }
            }
        }
    }

    /** Animated center banner (wave / boss / phase announcements). */
    private fun showBanner(text: String, color: Int, large: Boolean = false) {
        val container = hudContainer ?: return
        val banner = android.widget.TextView(activity).apply {
            this.text = text
            setTextColor(color)
            textSize = if (large) 34f else 24f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(12f, 0f, 4f, android.graphics.Color.argb(200, 0, 0, 0))
            gravity = android.view.Gravity.CENTER
            alpha = 0f
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
        ).apply { topMargin = UiKit.dp(activity, 90f) }
        container.addView(banner, lp)

        val appear = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            addUpdateListener { banner.alpha = it.animatedValue as Float }
        }
        val fade = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
            startDelay = 1500
            duration = 420
            addUpdateListener { banner.alpha = it.animatedValue as Float }
        }
        fade.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(a: android.animation.Animator) {}
            override fun onAnimationCancel(a: android.animation.Animator) {}
            override fun onAnimationRepeat(a: android.animation.Animator) {}
            override fun onAnimationEnd(a: android.animation.Animator) { container.removeView(banner) }
        })
        appear.start(); fade.start()
    }

    fun setStatus(text: String) { hudStatus?.text = text }

    /** Persist a crash-recovery snapshot of the current run. */
    private fun saveRunSnapshot() {
        if (!MetaSession.initialized) return
        try {
            val json = org.json.JSONObject()
            json.put("hero", engine.currentHeroId)
            json.put("wave", engine.system<WaveSystem>()?.currentWave ?: 1)
            json.put("kills", runStats.kills)
            json.put("gold", runStats.gold)
            json.put("time", engine.clock.simTime)
            json.put("savedAt", System.currentTimeMillis())
            MetaSession.save.saveRunSnapshot(json)
        } catch (_: Throwable) {
            // snapshot is best-effort only
        }
    }

    /** Snapshot of an interrupted run, or null. */
    fun pendingResume(): org.json.JSONObject? {
        if (!MetaSession.initialized) return null
        val snap = MetaSession.save.loadRunSnapshot() ?: return null
        // stale snapshots (older than 24h) are discarded
        val savedAt = snap.optLong("savedAt", 0L)
        if (System.currentTimeMillis() - savedAt > 86_400_000L) {
            MetaSession.save.clearRunSnapshot()
            return null
        }
        return snap
    }

    /** End the run: tower destroyed or player dead -> GAME_OVER -> results. */
    fun endRun() {
        if (engine.stateManager.current == GameState.GAME_OVER) return
        engine.clock.slowMotion(0.3f, 1.2f)
        engine.stateManager.transition(GameState.GAME_OVER)

        // --- run-end meta integration ---
        val xpSystem = engine.system<XpSystem>()
        val waveSystem = engine.system<WaveSystem>()
        runStats.wave = waveSystem?.currentWave ?: runStats.wave
        runStats.time = engine.clock.simTime
        runStats.xp = (xpSystem?.totalXpEarned ?: 0f).toLong()
        lastRunXp = runStats.xp
        val summary = MetaSession.onRunFinished(
            wave = runStats.wave,
            kills = runStats.kills,
            goldEarned = runStats.gold,
            heroXp = runStats.xp,
            heroId = engine.currentHeroId,
            playtimeSeconds = runStats.time.toLong()
        )
        MetaSession.save.clearRunSnapshot()

        AudioDirector.setMood(AudioDirector.MusicMood.GAME_OVER)
        AudioDirector.play(AudioDirector.Sfx.GAME_OVER)
        Haptics.fire(Haptics.Preset.EXPLOSION)

        activity.screenManager.push(GameOverScreen(activity, runStats, summary))
    }

    /** Leave gameplay back to the main menu. */
    fun returnToMenu() {
        engine.unregisterSystem(playerController)
        engine.system<ShadowSystem>()?.let { engine.unregisterSystem(it) }
        engine.system<AbilitySystem>()?.let { engine.unregisterSystem(it) }
        engine.system<XpSystem>()?.let { engine.unregisterSystem(it) }
        engine.system<WaveSystem>()?.let { engine.unregisterSystem(it) }
        engine.system<EnemyManager>()?.let { engine.unregisterSystem(it) }
        engine.system<TowerSystem>()?.let { engine.unregisterSystem(it) }
        engine.system<CombatSystem>()?.let { engine.unregisterSystem(it) }
        engine.system<ParticleManager>()?.let { engine.unregisterSystem(it) }
        engine.system<com.towerbound.laststand.bosses.BossSystem>()?.let { engine.unregisterSystem(it) }
        engine.system<com.towerbound.laststand.env.EnvironmentSystem>()?.let { engine.unregisterSystem(it) }
        engine.input.reset()
        engine.targetProvider = null
        engine.stateManager.forceState(GameState.MAIN_MENU)
        hudContainer?.removeAllViews()
        (hudContainer?.parent as? ViewGroup)?.removeView(hudContainer)
        hudContainer = null
        joystick = null
        hudStatus = null
        engine.hudUpdateHook = null
        PerformanceManager.hideOverlay()
        AudioDirector.setMood(AudioDirector.MusicMood.MENU)
        activity.screenManager.replace(MainMenuScreen(activity))
    }
}
