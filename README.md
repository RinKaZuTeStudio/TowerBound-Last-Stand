# Towerbound: Last Stand

A pure **native Android (Kotlin + OpenGL ES)** tower-defense survival game with **zero external assets** — every sprite, texture, icon, and sound is generated **procedurally at runtime**.

> Defend the last tower against endless waves. Pick a hero, level up mid-battle, unlock permanent upgrades, and see how far you can push before the horde breaks through.

## Highlights

- **100% procedural graphics** — characters, towers, environment, FX, and 34 UI symbols are painted in code (Canvas → texture atlases). No PNGs, no audio files, tiny APK.
- **60 Hz fixed-timestep simulation** with hit-stop, slow-motion death sequences, and a splitmix64 seeded RNG (deterministic, replay-friendly).
- **Real-time action combat** — virtual joystick + action buttons, dash with i-frames, knockback, 6 interacting status effects (burn, freeze, poison, stun, slow, bleed).
- **Endless wave scaling** — HP +13%/wave, damage +9%/wave, elites every 5 waves, bosses every 10 waves, special events (SWARM / SIEGE / TREASURE / DOUBLE_XP).
- **10 enemy archetypes** with a 7-state AI machine and target-priority behaviors (Grunt, Runner, Archer, Tank, Swarmer, Mage, Assassin, Siege, Elite, Boss).
- **8 playable heroes** with unique abilities and shadow clones.
- **Full meta-game** — XP/upgrade cards mid-run, permanent growth, currencies, shop economy, quests, achievements, collection.
- **Persistence** — versioned save with checksum + backup/restore, continue-run support.
- **Accessibility** — Arabic RTL support, colorblind-friendly options, dynamic quality scaling for low-end devices.
- **Procedural audio + haptics** — synthesized SFX and vibration feedback via AudioTrack, no sound files.

## Tech

| | |
|---|---|
| Language | Kotlin |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |
| Graphics | OpenGL ES 3.0 (2.0 fallback), sprite-batch renderer (2048-quad batches, additive blending) |
| Architecture | Single-Activity + GLSurfaceView + ScreenManager stack; GameEngine process singleton; fixed-order SimSystems |
| Testing | Pure-JVM unit tests for core systems (state machine, RNG, clock, combat math, status effects, progression, quests) |
| Build | Gradle 8.7 · AGP 8.5.2 |

## Project layout

```
app/src/main/java/com/towerbound/laststand/
├── core/        # GameEngine, GameState FSM, GameClock, EventBus, Rng, loading pipeline
├── render/      # GLRenderer, SpriteBatch, Camera, TextAtlas, ShaderUtil, WorldRenderer
├── assets/      # Procedural painters (characters, towers, environment, FX, symbols)
├── combat/      # Damage math, status effects, buffs, projectiles
├── enemies/     # Enemy entities, AI, definitions (10 archetypes), manager/pooling
├── waves/       # Wave composition, scaling, special events
├── tower/       # Tower entity & system (shields, sub-weapons, death timeline)
├── gameplay/    # Player entity & controller (dash, i-frames)
├── heroes/      # Hero abilities & shadow system
├── bosses/      # Boss phases, telegraphs, camera work
├── progression/ # XP system, upgrade cards, synergies
├── meta/        # Currencies, permanent growth, shop, quests, achievements, settings
├── save/        # Versioned save manager with checksum & backups
├── audio/       # Procedural audio director
├── haptics/     # Vibration feedback
├── input/       # Virtual joystick, action buttons, input state
├── ui/          # UiKit, widgets, ScreenManager, all game screens
├── env/         # Day/night cycle, water, tree sway
├── perf/        # Dynamic quality manager & performance monitor
├── fx/          # Particle system (300-particle pool)
├── anim/        # Animation controller
└── world/       # Game world & environment state
```

## Build

```bash
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # release build
./gradlew testDebugUnitTest # run unit tests
```

Requires JDK 17 and Android SDK 34.

## Status

Core game, enemies, waves, heroes, bosses, meta-progression, economy, quests, achievements, save system, accessibility, performance scaling, and the full UI/UX flow are implemented. All art and audio are generated at runtime.

---

*Built entirely from scratch — no external art, audio, or game-engine dependencies.*
