package com.mini.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Contexto que reciben las filas: ajustes, estilo y acciones de la pantalla. */
class SCtx(
    val cfg: Cfg,
    val style: DockStyle,
    val onSet: (String, Int) -> Unit,
    val act: (String) -> Unit,
    val ctx: android.content.Context,
    val prefs: Prefs,
    val permTick: Int = 0
)

/** Fila declarativa: id estable (clave de LazyColumn y de foco), sección y contenido. */
class SRow(
    val id: String,
    val sec: String,
    val icon: String = "",
    val visible: (SCtx) -> Boolean = { true },
    val focusable: Boolean = true,
    val content: @Composable (SCtx, Modifier) -> Unit
)

/** Categoría del menú: icono, nombre, descripción, orden de secciones y filas. */
class SCat8(
    val icon: String,
    val name: String,
    val desc: String,
    val secs: List<String>,
    val rows: List<SRow>
)

/** Lista plana para LazyColumn: String = título de sección, SRow = fila. */
fun SCat8.flat(c: SCtx): List<Any> {
    val out = ArrayList<Any>()
    for (s in secs) {
        val rs = rows.filter { it.sec == s && it.visible(c) }
        if (rs.isEmpty()) continue
        out.add(s)
        out.addAll(rs)
    }
    return out
}

fun swRow(
    id: String, sec: String, ic: String, label: String,
    get: (SCtx) -> Boolean, set: (SCtx, Boolean) -> Unit,
    vis: (SCtx) -> Boolean = { true }
) = SRow(id, sec, ic, vis) { c, m -> SwitchRow(label, get(c), m) { set(c, it) } }

fun chRow(
    id: String, sec: String, ic: String, label: String, opts: List<String>,
    get: (SCtx) -> Int, set: (SCtx, Int) -> Unit,
    vis: (SCtx) -> Boolean = { true }
) = SRow(id, sec, ic, vis) { c, m -> ChoiceRow(label, opts, get(c), m) { set(c, it) } }

fun slRow(
    id: String, sec: String, ic: String, label: String, min: Int, max: Int, step: Int, unit: String,
    get: (SCtx) -> Int, set: (SCtx, Int) -> Unit,
    vis: (SCtx) -> Boolean = { true }
) = SRow(id, sec, ic, vis) { c, m -> SliderRow(label, get(c), min, max, step, unit, m) { set(c, it) } }

fun acRow(
    id: String, sec: String, ic: String, label: String,
    vis: (SCtx) -> Boolean = { true }, run: (SCtx) -> Unit
) = SRow(id, sec, ic, vis) { c, m -> ActionRow(label, m) { run(c) } }
