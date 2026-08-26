package com.towerbound.laststand.assets

import android.graphics.Color

/** Visual identities of all 8 heroes. Each also gets distinct gameplay stats/abilities. */
object HeroDesigns {

    val ARIC = CharacterDesign(
        id = "ARIC", displayName = "Aric",
        skin = Color.parseColor("#E8B98C"),
        primary = Color.parseColor("#8D99AE"),   // steel armor
        secondary = Color.parseColor("#5D3FD3"), // purple cape
        accent = Color.parseColor("#00E5FF"),
        headStyle = HeadStyle.HELM_KITE,
        weapon = WeaponStyle.SHIELD_SWORD,
        capeStyle = CapeStyle.SHORT
    )

    val KAEL = CharacterDesign(
        id = "KAEL", displayName = "Kael",
        skin = Color.parseColor("#D9A066"),
        primary = Color.parseColor("#2E7D32"),   // ranger green
        secondary = Color.parseColor("#1B5E20"),
        accent = Color.parseColor("#AEEA00"),
        headStyle = HeadStyle.HOOD,
        weapon = WeaponStyle.BOW,
        capeStyle = CapeStyle.SHORT
    )

    val LYRA = CharacterDesign(
        id = "LYRA", displayName = "Lyra",
        skin = Color.parseColor("#F2D5C4"),
        primary = Color.parseColor("#4FC3F7"),   // ice blue robes
        secondary = Color.parseColor("#E1F5FE"),
        accent = Color.parseColor("#80D8FF"),
        headStyle = HeadStyle.WIZARD_HAT,
        weapon = WeaponStyle.STAFF,
        capeStyle = CapeStyle.LONG,
        floating = false
    )

    val DARIUS = CharacterDesign(
        id = "DARIUS", displayName = "Darius",
        skin = Color.parseColor("#C68642"),
        primary = Color.parseColor("#B87333"),   // bronze plate
        secondary = Color.parseColor("#7A4419"),
        accent = Color.parseColor("#FF7043"),
        headStyle = HeadStyle.HELM_HORNS,
        weapon = WeaponStyle.GREATSWORD,
        capeStyle = CapeStyle.TATTERED,
        scale = 1.18f, bulk = 1.35f
    )

    val NYX = CharacterDesign(
        id = "NYX", displayName = "Nyx",
        skin = Color.parseColor("#D7BDE2"),
        primary = Color.parseColor("#311B92"),   // violet stealth suit
        secondary = Color.parseColor("#1A0A45"),
        accent = Color.parseColor("#F06292"),
        headStyle = HeadStyle.MASK_NINJA,
        weapon = WeaponStyle.DAGGERS,
        capeStyle = CapeStyle.NONE,
        eyeGlow = Color.parseColor("#F06292"),
        scale = 0.92f, bulk = 0.85f
    )

    val MORVAIN = CharacterDesign(
        id = "MORVAIN", displayName = "Morvain",
        skin = Color.parseColor("#B9C4C9"),
        primary = Color.parseColor("#37474F"),   // dark robes
        secondary = Color.parseColor("#7B1FA2"), // necro purple
        accent = Color.parseColor("#B388FF"),
        headStyle = HeadStyle.HOOD,
        weapon = WeaponStyle.SCYTHE,
        capeStyle = CapeStyle.TATTERED,
        eyeGlow = Color.parseColor("#B388FF")
    )

    val VOLT = CharacterDesign(
        id = "VOLT", displayName = "Volt",
        skin = Color.parseColor("#E0C3A0"),
        primary = Color.parseColor("#00838F"),   // teal tech suit
        secondary = Color.parseColor("#006064"),
        accent = Color.parseColor("#00E5FF"),
        headStyle = HeadStyle.VISOR_TECH,
        weapon = WeaponStyle.GAUNTLET,
        capeStyle = CapeStyle.NONE,
        eyeGlow = Color.parseColor("#00E5FF")
    )

    val BRONN = CharacterDesign(
        id = "BRONN", displayName = "Bronn",
        skin = Color.parseColor("#D9A066"),
        primary = Color.parseColor("#F9A825"),   // gold guardian armor
        secondary = Color.parseColor("#1565C0"),
        accent = Color.parseColor("#FFD54F"),
        headStyle = HeadStyle.HELM_KITE,
        weapon = WeaponStyle.HAMMER,
        capeStyle = CapeStyle.SHORT,
        scale = 1.08f, bulk = 1.25f
    )

    val all: List<CharacterDesign> = listOf(ARIC, KAEL, LYRA, DARIUS, NYX, MORVAIN, VOLT, BRONN)

    fun byId(id: String): CharacterDesign = all.first { it.id == id }
}
