package com.towerbound.laststand.assets

import android.graphics.Color

/**
 * Parametric character visual definition. Every hero, enemy and boss gets a
 * distinct [CharacterDesign]; the [CharacterPainter] renders it at 256x256
 * with any [Pose], which is how all animation frames are generated.
 */
enum class HeadStyle { HUMAN, HELM_KITE, HOOD, HELM_HORNS, MASK_NINJA, WIZARD_HAT, VISOR_TECH, SKULL, CROWN, IMPSKULL }
enum class WeaponStyle { SWORD, GREATSWORD, BOW, STAFF, DAGGERS, HAMMER, GAUNTLET, CANNON, SCYTHE, ORB, SPEAR, CLAWS, SHIELD_SWORD, CLUB, NONE }
enum class CapeStyle { NONE, SHORT, LONG, TATTERED }

data class CharacterDesign(
    val id: String,
    val displayName: String,
    val skin: Int,
    val primary: Int,
    val secondary: Int,
    val accent: Int,
    val outline: Int = Color.argb(235, 10, 12, 26),
    val scale: Float = 1f,
    val bulk: Float = 1f,
    val headStyle: HeadStyle = HeadStyle.HUMAN,
    val weapon: WeaponStyle = WeaponStyle.SWORD,
    val capeStyle: CapeStyle = CapeStyle.NONE,
    val floating: Boolean = false,
    val eyeGlow: Int = 0,
    val auraColor: Int = 0
) {
    companion object
}

/**
 * A single animation frame's body configuration. Frames differ only by pose,
 * so one painter + design + pose sequence == a complete animated character.
 */
data class Pose(
    val walkPhase: Float = 0f,
    val bob: Float = 0f,
    val lean: Float = 0f,
    val armPhase: Float = 0f,
    val weaponAngle: Float = 0f,
    val weaponExtend: Float = 0f,
    val squash: Float = 0f,
    val tilt: Float = 0f,
    val alpha: Float = 1f,
    val castGlow: Float = 0f,
    val speedLines: Float = 0f,
    val airborne: Float = 0f
)
