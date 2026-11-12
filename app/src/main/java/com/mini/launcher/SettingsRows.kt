package com.mini.launcher

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun cityList(c: Cfg) = if (c.city in CITIES) CITIES else listOf(c.city) + CITIES

fun rowsFondo(): List<SRow> = listOf(
    acRow("walls", "Fondo", "palette", "Cambiar fondo de pantalla") { it.act("walls") },
    swRow("pausewall", "Fondo", "bolt", "Pausar fondo animado con paneles", { it.cfg.pauseWall }, { c, v -> c.cfg.setPauseWall(v) }),
    SRow("theme", "Color", "palette") { c, m -> ThemeSwatchRow(c.cfg.theme, m) { c.cfg.setTheme(it) } },
    SRow("accent", "Color", "palette") { c, m ->
        DialogRow(if (c.cfg.accentCustom != 0) "Color de acento propio (activo)" else "Color de acento propio", m) { close ->
            AccentPickerDialog(
                current = c.cfg.accentCustom,
                onSave = { c.cfg.setAccentCustom(it); close() },
                onClear = { c.cfg.setAccentCustom(0); close() },
                onClose = close
            )
        }
    },
    chRow("glow", "Color", "eye", "Brillo de color", GLOW_NAMES, { it.style.glow }, { c, v -> c.onSet("glow", v) }),
    slRow("dim", "Color", "layout", "Oscurecer fondo", 0, 80, 5, "%", { it.cfg.dim }, { c, v -> c.cfg.setDim(v) })
)

fun rowsDock(): List<SRow> = listOf(
    slRow("scale", "Dock", "layout", "Tamaño general", 50, 180, 10, "%", { it.style.scalePct }, { c, v -> c.onSet("scale", v) }),
    slRow("spacing", "Dock", "layout", "Separación", 0, 48, 2, " dp", { it.style.spacing }, { c, v -> c.onSet("spacing", v) }),
    slRow("corner", "Dock", "layout", "Redondeo", 0, 40, 2, " dp", { it.style.corner }, { c, v -> c.onSet("corner", v) }),
    slRow("bottom", "Dock", "layout", "Margen inferior", 0, 80, 2, " dp", { it.style.bottom }, { c, v -> c.onSet("bottom", v) }),
    chRow("tilestyle", "Fichas", "device", "Estilo de ficha", TILE_STYLES, { it.cfg.tileStyle }, { c, v -> c.cfg.setTileStyle(v) }),
    swRow("uniform", "Fichas", "device", "Iconos uniformes", { it.cfg.uniformIcons }, { c, v -> c.cfg.setUniformIcons(v) }, { it.cfg.tileStyle >= 2 }),
    chRow("focusstyle", "Fichas", "eye", "Estilo de foco", FOCUS_STYLES, { it.cfg.focusStyle }, { c, v -> c.cfg.setFocusStyle(v) }),
    swRow("openanim", "Fichas", "bolt", "Animación al abrir apps", { it.cfg.openAnim }, { c, v -> c.cfg.setOpenAnim(v) })
)

fun rowsInicio(): List<SRow> = listOf(
    chRow("topmode", "Parte superior", "home", "Elementos de arriba", listOf("Completa", "Reducida", "Mínima"), { it.cfg.topMode }, { c, v -> c.cfg.setTopMode(v) }),
    SRow("topnote", "Parte superior") { c, m ->
        Text(
            when (c.cfg.topMode) {
                0 -> "Se ve todo: reloj, fecha, clima, tarjetas, música, red y botón Más."
                1 -> "Reloj, fecha, clima, música y ajustes. Sin tarjetas, red ni botón Más."
                else -> "Solo el reloj y el botón de ajustes."
            },
            color = Color(0xFFA8B1C3), fontSize = 11.sp,
            modifier = m.padding(start = 8.dp, end = 8.dp, bottom = 2.dp)
        )
    },
    slRow("topscale", "Parte superior", "layout", "Tamaño de la parte superior", 60, 150, 10, " %", { it.cfg.topScale }, { c, v -> c.cfg.setTopScale(v) }),
    swRow("hidebar", "Parte superior", "eye", "Ocultar la barra superior sola", { it.cfg.hideBar }, { c, v -> c.cfg.setHideBar(v) }),
    slRow("hidesecs", "Parte superior", "eye", "Ocultar tras", 3, 60, 3, " s", { it.cfg.hideBarSecs }, { c, v -> c.cfg.setHideBarSecs(v) }, { it.cfg.hideBar }),
    swRow("bigclock", "Reloj", "home", "Reloj grande", { it.cfg.bigClock }, { c, v -> c.cfg.setBigClock(v) }),
    chRow("clock24", "Reloj", "home", "Formato de hora", listOf("12 horas", "24 horas"), { if (it.cfg.clock24) 1 else 0 }, { c, v -> c.cfg.setClock24(v == 1) }),
    swRow("showdate", "Reloj", "home", "Mostrar fecha", { it.cfg.showDate }, { c, v -> c.cfg.setShowDate(v) }),
    SRow("datefmt", "Reloj", "home") { c, m ->
        val labels = remember { dateFormatLabels() }
        ChoiceRow("Formato de fecha", labels, c.cfg.dateFmt, m) { c.cfg.setDateFmt(it) }
    },
    swRow("showwx", "Clima", "globe", "Mostrar clima", { it.cfg.showWeather }, { c, v -> c.cfg.setShowWeather(v) }),
    SRow("city", "Clima", "globe") { c, m ->
        val opts = cityList(c.cfg)
        ChoiceRow("Ciudad del clima", opts, opts.indexOf(c.cfg.city), m) { c.cfg.setCity(opts[it]) }
    },
    chRow("units", "Clima", "globe", "Unidades", listOf("°C", "°F"), { if (it.cfg.fahrenheit) 1 else 0 }, { c, v -> c.cfg.setFahrenheit(v == 1) })
)
