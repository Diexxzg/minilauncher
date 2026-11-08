package com.mini.launcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.text.style.TextOverflow
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ajustes de la TV que se ven y se cambian desde el celular. GENERADO a partir de Settings.kt:
 * cada fila simple (interruptor, opciones, deslizador) trae como leer y escribir su valor.
 * Las filas que abren dialogos o permisos en la TV salen con kind "info" (solo se ven).
 */
object RemoteSchema {
    class R(
        val id: Int, val cat: Int, val sec: String, val label: String, val kind: String,
        val opts: List<String>, val mn: Int, val mx: Int, val step: Int, val unit: String, val act: String,
        val get: ((Cfg, Prefs, Context) -> Int)?,
        val set: ((Cfg, Prefs, Context, Int) -> Unit)?
    )

    val cats = listOf("Fondo y color", "Dock y diseño", "Inicio", "Pantalla y energía", "Música", "Apps", "Sistema", "Permisos", "Seguridad", "Accesibilidad", "Acerca de")

    val rows: List<R> = listOf(
        R(0, 0, "Fondo", "Cambiar fondo de pantalla", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(1, 0, "Fondo", "Rotación de fondos", "choice", listOf("Desactivada", "Cada 30 min", "Cada hora", "Según la hora"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.rot },
            { cfg, prefs, ctx, v -> val it = v; cfg.setRot(it) }),
        R(2, 0, "Fondo", "Transición suave", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.fade) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setFade(it) }),
        R(3, 0, "Fondo", "Pausar fondo animado", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.pauseWall) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setPauseWall(it) }),
        R(4, 0, "Fondo", "Fondo según app enfocada", "choice", listOf("Apagado", "Degradado", "Banner"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.dynBg },
            { cfg, prefs, ctx, v -> val it = v; cfg.setDynBg(it) }),
        R(5, 0, "Fondo", "Presentación de fondos", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.wallShow) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setWallShow(it) }),
        R(6, 0, "Fondo", "Cambiar cada", "slider", emptyList(), 5, 120, 5, " min", "",
            { cfg, prefs, ctx -> cfg.wallShowMins },
            { cfg, prefs, ctx, v -> val it = v; cfg.setWallShowMins(it) }),
        R(7, 0, "Fondo", "Clima en el fondo", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.weatherFx) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setWeatherFx(it) }),
        R(9, 0, "Color", "Tema de color", "theme", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.theme },
            { cfg, prefs, ctx, v -> cfg.setTheme(v) }),
        R(10, 0, "Color", "Tema automático", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.autoTheme) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setAutoTheme(it) }),
        R(11, 0, "Color", "Color de acento propio", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(12, 0, "Color", "Brillo de color", "info", listOf("Apagado", "Suave", "Medio", "Fuerte", "Máximo"), 0, 0, 1, "", "",
            null,
            null),
        R(13, 0, "Color", "Color del brillo", "choice", listOf("Color de cada app", "Color del fondo", "Color del tema"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.glowSrc },
            { cfg, prefs, ctx, v -> val it = v; cfg.setGlowSrc(it) }),
        R(15, 0, "Color", "Oscurecer fondo", "slider", emptyList(), 0, 80, 5, "%", "",
            { cfg, prefs, ctx -> cfg.dim },
            { cfg, prefs, ctx, v -> val it = v; cfg.setDim(it) }),
        R(8, 1, "Perfil", "Perfil de diseño", "info", listOf("Personalizado"), 0, 0, 1, "", "",
            null,
            null),
        R(55, 1, "Fichas", "Tamaño general", "info", emptyList(), 50, 180, 10, "%", "",
            null,
            null),
        R(56, 1, "Fichas", "Separación", "info", emptyList(), 0, 48, 2, " dp", "",
            null,
            null),
        R(57, 1, "Fichas", "Redondeo", "info", emptyList(), 0, 40, 2, " dp", "",
            null,
            null),
        R(58, 1, "Fichas", "Margen inferior", "info", emptyList(), 0, 80, 2, " dp", "",
            null,
            null),
        R(59, 1, "Fichas", "Estilo de ficha", "choice", listOf("Banner", "Banner 16:9", "Icono cuadrado", "Círculo"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.tileStyle },
            { cfg, prefs, ctx, v -> val it = v; cfg.setTileStyle(it) }),
        R(60, 1, "Fichas", "Iconos uniformes", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.uniformIcons) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setUniformIcons(it) }),
        R(61, 1, "Información", "Tarjeta de la app enfocada", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.focusCard) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setFocusCard(it) }),
        R(62, 1, "Filas extra", "Fila extra 1", "choice", listOf("Desactivada", "Streaming", "Juegos", "Herramientas", "Música", "Familia", "Deportes", "Noticias"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.row(0) },
            { cfg, prefs, ctx, v -> val it = v; cfg.setRow(0, it) }),
        R(63, 1, "Filas extra", "Fila extra 2", "choice", listOf("Desactivada", "Streaming", "Juegos", "Herramientas", "Música", "Familia", "Deportes", "Noticias"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.row(1) },
            { cfg, prefs, ctx, v -> val it = v; cfg.setRow(1, it) }),
        R(64, 1, "Filas extra", "Fila extra 3", "choice", listOf("Desactivada", "Streaming", "Juegos", "Herramientas", "Música", "Familia", "Deportes", "Noticias"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.row(2) },
            { cfg, prefs, ctx, v -> val it = v; cfg.setRow(2, it) }),
        R(65, 1, "Filas extra", "Asignar apps", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(14, 1, "Foco y efectos", "Estilo de foco", "choice", listOf("Clásico (aro y brillo)", "Aro grueso", "Solo brillo", "Solo escala"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.focusStyle },
            { cfg, prefs, ctx, v -> val it = v; cfg.setFocusStyle(it) }),
        R(16, 1, "Foco y efectos", "Desenfoque al abrir paneles", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.blur) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setBlur(it) }),
        R(17, 1, "Foco y efectos", "Animar cambio de pantallas", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.screenAnim) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setScreenAnim(it) }),
        R(18, 1, "Foco y efectos", "Animación al abrir apps", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.openAnim) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setOpenAnim(it) }),
        R(66, 1, "Compartir diseño", "Exportar diseño", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(67, 1, "Compartir diseño", "Importar diseño", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(68, 2, "Parte superior", "Elementos de arriba", "choice", listOf("Completa", "Reducida", "Mínima"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.topMode },
            { cfg, prefs, ctx, v -> val it = v; cfg.setTopMode(it) }),
        R(69, 2, "Parte superior", "Aviso de elementos de arriba", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(70, 2, "Parte superior", "Tamaño de arriba", "slider", emptyList(), 60, 150, 10, " %", "",
            { cfg, prefs, ctx -> cfg.topScale },
            { cfg, prefs, ctx, v -> val it = v; cfg.setTopScale(it) }),
        R(71, 2, "Reloj y fecha", "Reloj grande", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.bigClock) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setBigClock(it) }),
        R(72, 2, "Reloj y fecha", "Formato de hora", "choice", listOf("12 horas", "24 horas"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.clock24) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v; cfg.setClock24(it == 1) }),
        R(73, 2, "Reloj y fecha", "Mostrar fecha", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.showDate) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setShowDate(it) }),
        R(74, 2, "Reloj y fecha", "Formato de fecha", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(75, 2, "Tarjetas junto al reloj", "Próximo evento", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(76, 2, "Tarjetas junto al reloj", "Tarjeta de nota", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.cardNote) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setCardNote(it) }),
        R(77, 2, "Tarjetas junto al reloj", "Escribir la nota", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(78, 2, "Tarjetas junto al reloj", "Tarjeta de cuenta regresiva", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.cardCountdown) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setCardCountdown(it) }),
        R(79, 2, "Tarjetas junto al reloj", "Fecha de la cuenta regresiva", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(80, 2, "Tarjetas junto al reloj", "Nombre de la cuenta regresiva", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(81, 2, "Clima", "Mostrar clima", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.showWeather) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setShowWeather(it) }),
        R(82, 2, "Clima", "Ciudad del clima", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(83, 2, "Clima", "Unidades", "choice", listOf("°C", "°F"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.fahrenheit) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v; cfg.setFahrenheit(it == 1) }),
        R(84, 2, "Clima", "Pronóstico de 3 días", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.forecast) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setForecast(it)
                WeatherOpts.forecast = if (it) 1 else 0 }),
        R(85, 2, "Clima", "Ubicación automática", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(86, 2, "Contenido", "Fila Continuar", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.continueRow) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setContinueRow(it) }),
        R(87, 2, "Contenido", "Mostrar recientes", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.showRecents) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setShowRecents(it) }),
        R(88, 2, "Contenido", "Botón de voz", "choice", listOf("Asistente de voz", "Buscar apps"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.voiceMode },
            { cfg, prefs, ctx, v -> val it = v; cfg.setVoiceMode(it) }),
        R(89, 2, "Barra superior", "Ocultar barra superior", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.hideBar) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setHideBar(it) }),
        R(90, 2, "Barra superior", "Ocultar tras", "slider", emptyList(), 3, 60, 3, " s", "",
            { cfg, prefs, ctx -> cfg.hideBarSecs },
            { cfg, prefs, ctx, v -> val it = v; cfg.setHideBarSecs(it) }),
        R(19, 3, "Pantalla", "Tamaño de la interfaz", "slider", emptyList(), 70, 130, 5, "%", "",
            { cfg, prefs, ctx -> cfg.uiScale },
            { cfg, prefs, ctx, v -> val it = v; cfg.setUiScale(it) }),
        R(20, 3, "Pantalla", "Margen de seguridad", "slider", emptyList(), 0, 60, 2, " dp", "",
            { cfg, prefs, ctx -> cfg.overscan },
            { cfg, prefs, ctx, v -> val it = v; cfg.setOverscan(it) }),
        R(21, 3, "Pantalla", "Margen guiado", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(27, 3, "Rendimiento", "Fluidez máxima", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.fluid) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setFluid(it) }),
        R(28, 3, "Rendimiento", "Modo ahorro", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.saverOnly) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setSaver(it) }),
        R(29, 3, "Rendimiento", "Sugerir modo ahorro", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.saverHint) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setSaverHint(it) }),
        R(30, 3, "Rendimiento", "Rendimiento: salud, red, almacenamiento y más", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(31, 3, "Efectos visuales", "Desenfoque del fondo con paneles abiertos", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (Fx.blur) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setFxBlur(it) }),
        R(32, 3, "Efectos visuales", "Brillos y resplandor", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (Fx.glow) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setFxGlow(it) }),
        R(33, 3, "Efectos visuales", "Animaciones de ventanas", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (Fx.anim) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setFxAnim(it) }),
        R(34, 3, "Efectos visuales", "Efectos recomendados (todo apagado)", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(50, 3, "Noche", "Modo noche automático", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.night) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setNight(it) }),
        R(51, 3, "Noche", "Noche desde", "slider", emptyList(), 0, 23, 1, ":00", "",
            { cfg, prefs, ctx -> cfg.nightFrom },
            { cfg, prefs, ctx, v -> val it = v; cfg.setNightFrom(it) }),
        R(52, 3, "Noche", "Noche hasta", "slider", emptyList(), 0, 23, 1, ":00", "",
            { cfg, prefs, ctx -> cfg.nightTo },
            { cfg, prefs, ctx, v -> val it = v; cfg.setNightTo(it) }),
        R(91, 3, "Reposo", "Modo ambiente", "choice", listOf("Desactivado", "2 min", "5 min", "10 min", "20 min"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ambient },
            { cfg, prefs, ctx, v -> val it = v; cfg.setAmbient(it) }),
        R(92, 3, "Reposo", "Protección contra quemado", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.burnIn) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setBurnIn(it) }),
        R(100, 3, "Reposo", "Temporizador de sueño", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(101, 3, "Reposo", "Al terminar el temporizador", "choice", listOf("Pantalla negra", "Modo ambiente"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.sleepAction },
            { cfg, prefs, ctx, v -> val it = v; cfg.setSleepAction(it) }),
        R(22, 3, "Diseño del modo ambiente", "Marco de fotos", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("frameOn") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("frameOn", if (it) "1" else "0") }),
        R(23, 3, "Diseño del modo ambiente", "Carpeta del marco", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(24, 3, "Diseño del modo ambiente", "Fondo vivo según la hora", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("liveBg") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("liveBg", if (it) "1" else "0") }),
        R(25, 3, "Diseño del modo ambiente", "Amplitud del movimiento", "choice", listOf("Normal", "Amplia", "Muy amplia"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ex("ambMove", "0").toIntOrNull() ?: 0 },
            { cfg, prefs, ctx, v -> val it = v; cfg.setEx("ambMove", it.toString()) }),
        R(26, 3, "Diseño del modo ambiente", "Pantalla negra al entrar", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("ambBlack") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("ambBlack", if (it) "1" else "0") }),
        R(93, 3, "Diseño del modo ambiente", "Oscurecer en modo ambiente", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ambDimOn) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setAmbDimOn(it) }),
        R(94, 3, "Diseño del modo ambiente", "Nivel de oscurecimiento", "slider", emptyList(), 10, 85, 5, "%", "",
            { cfg, prefs, ctx -> cfg.ambDimLvl },
            { cfg, prefs, ctx, v -> val it = v; cfg.setAmbDimLvl(it) }),
        R(95, 3, "Diseño del modo ambiente", "Fondo animado en reposo", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ambAnim) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setAmbAnim(it) }),
        R(96, 3, "Diseño del modo ambiente", "Posición de la hora", "choice", listOf("Arriba izquierda", "Arriba centro", "Arriba derecha", "Centro izquierda", "Centro", "Centro derecha", "Abajo izquierda", "Abajo centro", "Abajo derecha", "Oculto"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ambPosTime },
            { cfg, prefs, ctx, v -> val it = v; cfg.setAmbPosTime(it) }),
        R(97, 3, "Diseño del modo ambiente", "Posición de la fecha", "choice", listOf("Arriba izquierda", "Arriba centro", "Arriba derecha", "Centro izquierda", "Centro", "Centro derecha", "Abajo izquierda", "Abajo centro", "Abajo derecha", "Oculto"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ambPosDate },
            { cfg, prefs, ctx, v -> val it = v; cfg.setAmbPosDate(it) }),
        R(98, 3, "Diseño del modo ambiente", "Posición del reproductor", "choice", listOf("Arriba izquierda", "Arriba centro", "Arriba derecha", "Centro izquierda", "Centro", "Centro derecha", "Abajo izquierda", "Abajo centro", "Abajo derecha", "Oculto"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ambPosMusic },
            { cfg, prefs, ctx, v -> val it = v; cfg.setAmbPosMusic(it) }),
        R(99, 3, "Diseño del modo ambiente", "Posición de los iconos", "choice", listOf("Arriba izquierda", "Arriba centro", "Arriba derecha", "Centro izquierda", "Centro", "Centro derecha", "Abajo izquierda", "Abajo centro", "Abajo derecha", "Oculto"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ambPosIcons },
            { cfg, prefs, ctx, v -> val it = v; cfg.setAmbPosIcons(it) }),
        R(53, 3, "Sonido", "Sonido al mover el foco", "choice", listOf("Apagado", "Suave", "Medio"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.soundLevel },
            { cfg, prefs, ctx, v -> val it = v; cfg.setSoundLevel(it) }),
        R(54, 3, "Sonido", "Sonido de paneles", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.panelSounds) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setPanelSounds(it) }),
        R(35, 3, "Ambiente y noche", "Tema desde la carátula", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("artTheme") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("artTheme", if (it) "1" else "0") }),
        R(36, 3, "Ambiente y noche", "Fondo musical", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("artBg") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("artBg", if (it) "1" else "0") }),
        R(37, 3, "Ambiente y noche", "Temas por fechas", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("dateThemes") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("dateThemes", if (it) "1" else "0") }),
        R(38, 3, "Ambiente y noche", "Cuentas regresivas", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("cdOn") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("cdOn", if (it) "1" else "0") }),
        R(39, 3, "Ambiente y noche", "Nueva cuenta regresiva", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(40, 3, "Ambiente y noche", "Borrar cuentas", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(41, 3, "Ambiente y noche", "Noche por contenido", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("nightOn") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("nightOn", if (it) "1" else "0") }),
        R(42, 3, "Ambiente y noche", "Volumen mínimo de noche", "slider", emptyList(), 5, 60, 5, "%", "",
            { cfg, prefs, ctx -> cfg.ex("nightVol", "20").toIntOrNull() ?: 20 },
            { cfg, prefs, ctx, v -> val it = v; cfg.setEx("nightVol", it.toString()) }),
        R(43, 3, "Ambiente y noche", "Despertador con rampa", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("wakeOn") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("wakeOn", if (it) "1" else "0") }),
        R(44, 3, "Ambiente y noche", "Hora del despertador", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(45, 3, "Ambiente y noche", "Duración de la rampa", "slider", emptyList(), 1, 30, 1, " min", "",
            { cfg, prefs, ctx -> cfg.ex("wakeMin", "10").toIntOrNull() ?: 10 },
            { cfg, prefs, ctx, v -> val it = v; cfg.setEx("wakeMin", it.toString()) }),
        R(46, 3, "Ambiente y noche", "Filtro de luz cálida", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("warmOn") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("warmOn", if (it) "1" else "0") }),
        R(47, 3, "Ambiente y noche", "Intensidad del filtro", "slider", emptyList(), 5, 50, 5, "%", "",
            { cfg, prefs, ctx -> cfg.ex("warmPct", "20").toIntOrNull() ?: 20 },
            { cfg, prefs, ctx, v -> val it = v; cfg.setEx("warmPct", it.toString()) }),
        R(48, 3, "Ambiente y noche", "Tarjeta en esquina", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("cornerOn") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("cornerOn", if (it) "1" else "0") }),
        R(49, 3, "Ambiente y noche", "Esquina de la tarjeta", "choice", listOf("Arriba derecha", "Arriba izquierda", "Abajo derecha", "Abajo izquierda"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ex("cornerPos", "0").toIntOrNull() ?: 0 },
            { cfg, prefs, ctx, v -> val it = v; cfg.setEx("cornerPos", it.toString()) }),
        R(102, 4, "Reproductor", "Mostrar reproductor", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.showMusic) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setShowMusic(it) }),
        R(103, 4, "Reproductor", "Tamaño del reproductor", "slider", emptyList(), 50, 120, 5, "%", "",
            { cfg, prefs, ctx -> cfg.musicSize },
            { cfg, prefs, ctx, v -> val it = v; cfg.setMusicSize(it) }),
        R(104, 4, "Reproductor", "Posición del reproductor", "choice", listOf("Junto al reloj", "Arriba al centro"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.musicPos },
            { cfg, prefs, ctx, v -> val it = v; cfg.setMusicPos(it) }),
        R(105, 5, "Organizar", "Editar apps", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(106, 5, "Carpetas", "Carpetas del dock", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(107, 5, "Apps nuevas", "Aviso de app nueva", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.newAppAlert) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setNewAppAlert(it) }),
        R(108, 6, "Conexiones", "Wi-Fi", "action", emptyList(), 0, 0, 1, "", "wifi",
            null,
            null),
        R(109, 6, "Conexiones", "Bluetooth", "action", emptyList(), 0, 0, 1, "", "bluetooth",
            null,
            null),
        R(110, 6, "Conexiones", "Entradas (HDMI)", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(112, 6, "Sistema", "Todos los ajustes de Android", "action", emptyList(), 0, 0, 1, "", "settings",
            null,
            null),
        R(113, 6, "Sistema", "Menú de energía (accesibilidad)", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(114, 6, "Sistema", "Protector de pantalla del sistema", "action", emptyList(), 0, 0, 1, "", "dream",
            null,
            null),
        R(115, 6, "Sistema", "Establecer como inicio predeterminado", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(116, 6, "Sistema", "Inicio: prueba, restaurar y permisos", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(117, 6, "Celular", "Conexión con el celular", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(140, 6, "Mando y uso diario", "Remapear botones del mando", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(141, 6, "Mando y uso diario", "Uso diario", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(142, 6, "Arranque", "Abrir app al encender", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(143, 6, "Arranque", "App de arranque", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(147, 6, "Escena de cine", "App de la escena", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(148, 6, "Escena de cine", "Modo noche en la escena", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.sceneNight) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setSceneNight(it) }),
        R(149, 6, "Escena de cine", "Oscurecimiento de la escena", "slider", emptyList(), 0, 80, 5, "%", "",
            { cfg, prefs, ctx -> cfg.sceneDim },
            { cfg, prefs, ctx, v -> val it = v; cfg.setSceneDim(it) }),
        R(150, 6, "Escena de cine", "Temporizador de la escena", "choice", listOf("Apagado", "15 minutos", "30 minutos", "60 minutos"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.sceneSleep },
            { cfg, prefs, ctx, v -> val it = v; cfg.setSceneSleep(it) }),
        R(151, 6, "Rutina diaria", "Rutina diaria", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.routineOn) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setRoutineOn(it) }),
        R(152, 6, "Rutina diaria", "Hora de la rutina", "slider", emptyList(), 0, 23, 1, " h", "",
            { cfg, prefs, ctx -> cfg.routineHour },
            { cfg, prefs, ctx, v -> val it = v; cfg.setRoutineHour(it) }),
        R(153, 6, "Rutina diaria", "Minutos de la rutina", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(154, 6, "Rutina diaria", "Acción de la rutina", "choice", listOf("Activar la escena de cine", "Abrir una app"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.routineAction },
            { cfg, prefs, ctx, v -> val it = v; cfg.setRoutineAction(it) }),
        R(155, 6, "Rutina diaria", "App de la rutina", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(156, 6, "Perfiles de familia", "Perfil activo", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(157, 6, "Asistente", "Asistente de configuración", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(170, 6, "Hogar y utilidades", "Tarjeta de visitas", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(171, 6, "Hogar y utilidades", "Lista de compras", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(172, 6, "Hogar y utilidades", "Ruido para dormir", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(173, 6, "Hogar y utilidades", "Modo fácil", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(174, 6, "Herramientas del sistema", "Modo juego", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(175, 6, "Herramientas del sistema", "Instalar APK desde USB", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(176, 6, "Herramientas del sistema", "Volumen adaptable", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(177, 6, "Herramientas del sistema", "Centro de notificaciones", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(118, 6, "", "Botón ", "info", listOf("Ninguno", "Todas las apps", "Ajustes", "Entradas", "Búsqueda", "Menú de energía", "Editar apps", "Modo ambiente", "Pantalla negra", "Escena de cine", "Siguiente perfil", "Intermedio de cine", "Limpiar RAM"), 0, 0, 1, "", "",
            null,
            null),
        R(119, 6, "", "Botón ", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(120, 6, "Casa y avisos", "Recordatorios en pantalla", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("remOn") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("remOn", if (it) "1" else "0") }),
        R(121, 6, "Casa y avisos", "Nuevo recordatorio", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(122, 6, "Casa y avisos", "Borrar recordatorios", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(123, 6, "Casa y avisos", "Ambilight", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("ambiOn") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("ambiOn", if (it) "1" else "0") }),
        R(124, 6, "Casa y avisos", "Dirección del WLED", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(125, 6, "Casa y avisos", "Intensidad Ambilight", "slider", emptyList(), 10, 100, 10, "%", "",
            { cfg, prefs, ctx -> cfg.ex("ambiLvl", "70").toIntOrNull() ?: 70 },
            { cfg, prefs, ctx, v -> val it = v; cfg.setEx("ambiLvl", it.toString()) }),
        R(126, 6, "Casa y avisos", "Probar Ambilight", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(127, 6, "Casa y avisos", "Dirección de la cámara", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(128, 6, "Casa y avisos", "Esquina de la cámara", "choice", listOf("Abajo derecha", "Abajo izquierda", "Arriba derecha", "Arriba izquierda"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ex("camCorner", "0").toIntOrNull() ?: 0 },
            { cfg, prefs, ctx, v -> val it = v; cfg.setEx("camCorner", it.toString()) }),
        R(129, 6, "Casa y avisos", "Ver cámara", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(130, 6, "", "Atajo ", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(131, 6, "Familia y control", "Tiempo por app", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("famTime") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("famTime", if (it) "1" else "0") }),
        R(132, 6, "Familia y control", "Apps con límite", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(133, 6, "Familia y control", "Bloqueo por horario", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("famSched") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("famSched", if (it) "1" else "0") }),
        R(134, 6, "Familia y control", "Horario del bloqueo", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(135, 6, "Familia y control", "Apps con PIN", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(136, 6, "Familia y control", "Aprobar instalaciones", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("famApprove") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("famApprove", if (it) "1" else "0") }),
        R(137, 6, "Familia y control", "Apps pendientes", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(138, 6, "Familia y control", "Registro de actividad", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("famLog") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("famLog", if (it) "1" else "0") }),
        R(139, 6, "Familia y control", "Ver registro", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(158, 6, "Código PIN", "Estado del PIN", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(160, 6, "Código PIN", "Estado del PIN", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(111, 7, "Permisos", "Permitir nombre de red y Bluetooth", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(144, 7, "Permisos", "Permiso del calendario", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(145, 7, "Permisos", "Permiso de ubicación", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(146, 7, "Permisos", "Permiso de notificaciones", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(159, 8, "Código PIN", "Crear PIN de 4 dígitos", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(161, 8, "Código PIN", "Cambiar PIN", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(162, 8, "Código PIN", "Quitar PIN", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(163, 8, "Protección", "Proteger ajustes", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.lockSettings) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setLockSettings(it) }),
        R(164, 8, "Protección", "Proteger apps ocultas", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.lockHidden) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setLockHidden(it) }),
        R(165, 8, "Perfil para niños", "Perfil para niños", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(166, 8, "Perfil para niños", "Apps permitidas para niños", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(167, 9, "Lectura", "Texto grande", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.bigText) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setBigText(it) }),
        R(168, 9, "Lectura", "Alto contraste", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.highContrast) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setHighContrast(it) }),
        R(169, 9, "Navegación", "Foco grueso", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.thickFocus) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setThickFocus(it) })
    )

    fun json(cfg: Cfg, prefs: Prefs, ctx: Context): String {
        val a = JSONArray()
        for (r in rows) {
            val o = JSONObject().put("id", r.id).put("c", r.cat).put("s", r.sec).put("l", r.label).put("k", r.kind)
            if (r.opts.isNotEmpty()) o.put("o", JSONArray(r.opts))
            if (r.kind == "slider") o.put("mn", r.mn).put("mx", r.mx).put("st", r.step).put("u", r.unit)
            if (r.act.isNotEmpty()) o.put("a", r.act)
            val g = r.get
            if (g != null) {
                try {
                    o.put("v", g(cfg, prefs, ctx))
                } catch (e: Exception) {
                    o.put("k", "info")
                }
            }
            a.put(o)
        }
        val th = JSONArray()
        for (t in Themes.list) th.put(JSONObject().put("n", t.name).put("c", (t.acc.toArgb().toLong() and 0xFFFFFFFFL)))
        return JSONObject().put("cats", JSONArray(cats)).put("rows", a).put("themes", th).toString()
    }

    fun apply(cfg: Cfg, prefs: Prefs, ctx: Context, id: Int, v: Int) {
        val r = rows.firstOrNull { it.id == id } ?: return
        val s = r.set ?: return
        try {
            s(cfg, prefs, ctx, v)
        } catch (e: Exception) {
        }
    }
}
