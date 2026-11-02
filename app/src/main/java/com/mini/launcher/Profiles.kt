package com.mini.launcher

/**
 * Perfil de diseno: cambia a la vez el tamano del dock, la separacion, el redondeo,
 * el brillo de color, el fondo dinamico, las filas extra y el reloj.
 */
class DesignProfile(
    val name: String,
    val scale: Int,
    val spacing: Int,
    val corner: Int,
    val glow: Int,
    val dynBg: Int,
    /** Indices (en ROW_NAMES) de las tres filas extra; 0 = desactivada. */
    val rows: List<Int>,
    val bigClock: Boolean
) {
    fun matches(scalePct: Int, spacingDp: Int, cornerDp: Int, glowLvl: Int, cfg: Cfg): Boolean =
        scalePct == scale && spacingDp == spacing && cornerDp == corner && glowLvl == glow &&
            cfg.dynBg == dynBg && cfg.bigClock == bigClock &&
            cfg.row(0) == rows[0] && cfg.row(1) == rows[1] && cfg.row(2) == rows[2]
}

val DESIGN_PROFILES = listOf(
    // Cine: fichas grandes con banner de fondo, brillo fuerte y reloj grande.
    DesignProfile("Cine", 140, 24, 26, 3, 2, listOf(0, 0, 0), true),
    // Compacto: fichas pequenas y juntas, con las filas Streaming, Juegos y Herramientas.
    DesignProfile("Compacto", 80, 10, 12, 1, 1, listOf(1, 2, 3), false),
    // Minimalista: sin brillo, sin fondo dinamico, sin filas y con el reloj pequeno.
    DesignProfile("Minimalista", 100, 16, 18, 0, 0, listOf(0, 0, 0), false),
    // Clasico: los valores de fabrica.
    DesignProfile("Clásico", 100, 16, 18, 2, 2, listOf(0, 0, 0), true)
)

/** Opciones del selector: la primera indica que los ajustes no coinciden con ningun perfil. */
val PROFILE_OPTIONS = listOf("Personalizado") + DESIGN_PROFILES.map { it.name }

/** 0 = personalizado; 1..4 = el perfil que coincide con los ajustes actuales. */
fun profileIndex(scalePct: Int, spacingDp: Int, cornerDp: Int, glowLvl: Int, cfg: Cfg): Int {
    val i = DESIGN_PROFILES.indexOfFirst { it.matches(scalePct, spacingDp, cornerDp, glowLvl, cfg) }
    return i + 1
}
