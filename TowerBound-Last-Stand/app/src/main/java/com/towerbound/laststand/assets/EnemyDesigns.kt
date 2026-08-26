package com.towerbound.laststand.assets

import android.graphics.Color

/** Visual identities of all 10 enemy archetypes and the boss roster. */
object EnemyDesigns {

    val GRUNT = CharacterDesign(
        id = "GRUNT", displayName = "Grunt",
        skin = Color.parseColor("#7CB342"),
        primary = Color.parseColor("#558B2F"),
        secondary = Color.parseColor("#33691E"),
        accent = Color.parseColor("#C0CA33"),
        headStyle = HeadStyle.IMPSKULL,
        weapon = WeaponStyle.CLUB,
        capeStyle = CapeStyle.NONE,
        eyeGlow = Color.parseColor("#FFEB3B"),
        scale = 0.95f, bulk = 1.05f
    )

    val RUNNER = CharacterDesign(
        id = "RUNNER", displayName = "Runner",
        skin = Color.parseColor("#E57373"),
        primary = Color.parseColor("#C62828"),
        secondary = Color.parseColor("#8E0000"),
        accent = Color.parseColor("#FFAB91"),
        headStyle = HeadStyle.IMPSKULL,
        weapon = WeaponStyle.DAGGERS,
        capeStyle = CapeStyle.NONE,
        eyeGlow = Color.parseColor("#FFCDD2"),
        scale = 0.85f, bulk = 0.8f
    )

    val ARCHER = CharacterDesign(
        id = "ARCHER", displayName = "Archer",
        skin = Color.parseColor("#D7CCC8"),
        primary = Color.parseColor("#455A64"),
        secondary = Color.parseColor("#263238"),
        accent = Color.parseColor("#80CBC4"),
        headStyle = HeadStyle.SKULL,
        weapon = WeaponStyle.BOW,
        capeStyle = CapeStyle.SHORT
    )

    val TANK = CharacterDesign(
        id = "TANK", displayName = "Siegebreaker",
        skin = Color.parseColor("#8D6E63"),
        primary = Color.parseColor("#5D4037"),
        secondary = Color.parseColor("#3E2723"),
        accent = Color.parseColor("#A1887F"),
        headStyle = HeadStyle.HELM_HORNS,
        weapon = WeaponStyle.GAUNTLET,
        capeStyle = CapeStyle.NONE,
        scale = 1.25f, bulk = 1.5f
    )

    val SWARMER = CharacterDesign(
        id = "SWARMER", displayName = "Swarmling",
        skin = Color.parseColor("#AB47BC"),
        primary = Color.parseColor("#6A1B9A"),
        secondary = Color.parseColor("#4A148C"),
        accent = Color.parseColor("#E1BEE7"),
        headStyle = HeadStyle.IMPSKULL,
        weapon = WeaponStyle.CLAWS,
        capeStyle = CapeStyle.NONE,
        eyeGlow = Color.parseColor("#F8BBD0"),
        scale = 0.68f, bulk = 0.72f
    )

    val MAGE = CharacterDesign(
        id = "MAGE", displayName = "Cult Mage",
        skin = Color.parseColor("#FFCC80"),
        primary = Color.parseColor("#4E342E"),
        secondary = Color.parseColor("#BF360C"),
        accent = Color.parseColor("#FF7043"),
        headStyle = HeadStyle.HOOD,
        weapon = WeaponStyle.STAFF,
        capeStyle = CapeStyle.LONG,
        eyeGlow = Color.parseColor("#FF7043"),
        floating = true
    )

    val ASSASSIN = CharacterDesign(
        id = "ASSASSIN", displayName = "Assassin",
        skin = Color.parseColor("#B0BEC5"),
        primary = Color.parseColor("#212121"),
        secondary = Color.parseColor("#424242"),
        accent = Color.parseColor("#EF5350"),
        headStyle = HeadStyle.MASK_NINJA,
        weapon = WeaponStyle.DAGGERS,
        capeStyle = CapeStyle.NONE,
        eyeGlow = Color.parseColor("#EF5350"),
        scale = 0.9f, bulk = 0.85f
    )

    val SIEGE = CharacterDesign(
        id = "SIEGE", displayName = "Siege Construct",
        skin = Color.parseColor("#90A4AE"),
        primary = Color.parseColor("#546E7A"),
        secondary = Color.parseColor("#37474F"),
        accent = Color.parseColor("#FF9100"),
        headStyle = HeadStyle.VISOR_TECH,
        weapon = WeaponStyle.CANNON,
        capeStyle = CapeStyle.NONE,
        scale = 1.3f, bulk = 1.4f
    )

    val ELITE = CharacterDesign(
        id = "ELITE", displayName = "Elite Champion",
        skin = Color.parseColor("#FFD54F"),
        primary = Color.parseColor("#F57F17"),
        secondary = Color.parseColor("#E65100"),
        accent = Color.parseColor("#FFEA00"),
        headStyle = HeadStyle.CROWN,
        weapon = WeaponStyle.SPEAR,
        capeStyle = CapeStyle.LONG,
        eyeGlow = Color.parseColor("#FFEA00"),
        scale = 1.12f, bulk = 1.2f,
        auraColor = Color.argb(70, 255, 215, 64)
    )

    val BOSS = CharacterDesign(
        id = "BOSS", displayName = "Warlord",
        skin = Color.parseColor("#E53935"),
        primary = Color.parseColor("#8E0000"),
        secondary = Color.parseColor("#4A0D0D"),
        accent = Color.parseColor("#FF6F00"),
        headStyle = HeadStyle.HELM_HORNS,
        weapon = WeaponStyle.GREATSWORD,
        capeStyle = CapeStyle.LONG,
        eyeGlow = Color.parseColor("#FF9100"),
        scale = 1.6f, bulk = 1.6f,
        auraColor = Color.argb(60, 255, 60, 30)
    )

    /* ---- named bosses (cycle every 10 waves) ---- */

    val BOSS_GRAULK = BOSS.copy(id = "BOSS_GRAULK", displayName = "Graulk the Unmaker")

    val BOSS_SOVEREIGN = CharacterDesign(
        id = "BOSS_SOVEREIGN", displayName = "Sovereign of Ash",
        skin = Color.parseColor("#EFEBE9"),
        primary = Color.parseColor("#4E342E"),
        secondary = Color.parseColor("#B71C1C"),
        accent = Color.parseColor("#FF6D00"),
        headStyle = HeadStyle.CROWN,
        weapon = WeaponStyle.SCYTHE,
        capeStyle = CapeStyle.TATTERED,
        eyeGlow = Color.parseColor("#FF6D00"),
        floating = true,
        scale = 1.55f, bulk = 1.35f,
        auraColor = Color.argb(60, 255, 109, 0)
    )

    val BOSS_VOIDMAW = CharacterDesign(
        id = "BOSS_VOIDMAW", displayName = "The Voidmaw",
        skin = Color.parseColor("#6A1B9A"),
        primary = Color.parseColor("#311B92"),
        secondary = Color.parseColor("#12005E"),
        accent = Color.parseColor("#B388FF"),
        headStyle = HeadStyle.SKULL,
        weapon = WeaponStyle.DAGGERS,
        capeStyle = CapeStyle.TATTERED,
        eyeGlow = Color.parseColor("#B388FF"),
        scale = 1.5f, bulk = 1.45f,
        auraColor = Color.argb(60, 179, 136, 255)
    )

    val bosses: List<CharacterDesign> = listOf(BOSS_GRAULK, BOSS_SOVEREIGN, BOSS_VOIDMAW)

    val all: List<CharacterDesign> = listOf(
        GRUNT, RUNNER, ARCHER, TANK, SWARMER, MAGE, ASSASSIN, SIEGE, ELITE
    )

    fun byId(id: String): CharacterDesign? =
        (all + bosses).firstOrNull { it.id == id }
}
