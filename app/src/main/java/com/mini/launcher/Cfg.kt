package com.mini.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Acciones de los botones de color (indice = codigo guardado). */
val COLOR_ACTIONS = listOf(
    "Ninguno", "Todas las apps", "Ajustes", "Entradas", "Búsqueda",
    "Menú de energía", "Editar apps", "Modo ambiente", "Pantalla negra", "Limpiar RAM"
)

/** Una sola vez: quita 9..11 (cine, perfil, intermedio) y desplaza 12+ tres lugares en botones de color y remapeo. */
fun migrateActions(p: Prefs) {
    if (p.getInt("actMig448", 0) == 1) return
    fun mv(a: Int) = when { a in 9..11 -> 0; a >= 12 -> a - 3; else -> a }
    for (k in listOf("keyRed", "keyGreen", "keyYellow", "keyBlue")) {
        val v = p.getInt(k, -1)
        if (v >= 0) p.putInt(k, mv(v))
    }
    try {
        val o = org.json.JSONObject(p.getStr("remap", "{}"))
        for (k in o.keys().asSequence().toList()) {
            val s = o.getString(k)
            if (s.startsWith("a:")) s.substring(2).toIntOrNull()?.let { o.put(k, "a:" + mv(it)) }
        }
        p.putStr("remap", o.toString())
    } catch (e: Exception) {
    }
    p.putInt("actMig448", 1)
}

/** Ajustes de inicio, fondo y musica (se guardan en Prefs). */
/** Efectos visuales opcionales (todos apagados por defecto). Se leen como estado: aplican al momento. */
object Fx {
    var glow by mutableStateOf(false)
}

class Cfg(private val p: Prefs) {
    init {
        migrateActions(p)
        Fx.glow = p.getInt("fxGlow", 0) == 1
    }
    private val _ex = androidx.compose.runtime.mutableStateMapOf<String, String>()
    /** Ajustes de texto sueltos (recordatorios, Ambilight, camara, atajos del panel). */
    fun ex(k: String, d: String = ""): String = _ex[k] ?: p.getStr(k, d)
    fun setEx(k: String, v: String) { _ex[k] = v; p.putStr(k, v) }
    private val _colorX = androidx.compose.runtime.mutableStateListOf(
        p.getStr("colorX0", ""), p.getStr("colorX1", ""), p.getStr("colorX2", ""), p.getStr("colorX3", "")
    )
    /** Accion extra del boton de color i: "w:url" (web), "l:MAC" (Wake-on-LAN) o vacio. */
    fun colorExtra(i: Int): String = _colorX[i]
    fun setColorExtra(i: Int, v: String) { _colorX[i] = v; p.putStr("colorX$i", v) }
    fun setFxGlow(v: Boolean) { Fx.glow = v; p.putInt("fxGlow", if (v) 1 else 0) }
    fun fxRecommended() { setFxGlow(false) }
    private var _clock24 by mutableStateOf(p.getInt("clock24", 0) == 1)
    private var _showDate by mutableStateOf(p.getInt("showDate", 1) == 1)
    private var _showWeather by mutableStateOf(p.getInt("showWeather", 1) == 1)
    private var _city by mutableStateOf(p.getStr("city", CITY))
    private var _fahrenheit by mutableStateOf(p.getInt("fahrenheit", 0) == 1)
    private var _showMusic by mutableStateOf(p.getInt("showMusic", 1) == 1)
    private var _dim by mutableStateOf(p.getInt("dim", 0))
    private var _ambient by mutableStateOf(p.getInt("ambient", 2))
    private var _sort by mutableStateOf(p.getInt("sort", 0))
    private var _musicSize by mutableStateOf(p.getInt("musicSize", 80))
    private var _musicPos by mutableStateOf(p.getInt("musicPos", 0))

    // Nuevos
    private var _overscan by mutableStateOf(p.getInt("overscan", 0))
    private var _uiScale by mutableStateOf(p.getInt("uiScale", 100))
    private var _glowSrc by mutableStateOf(p.getInt("glowSrc", 0))
    private var _soundLevel by mutableStateOf(p.getInt("sounds", 1).coerceIn(0, 2))
    private var _theme by mutableStateOf(p.getInt("theme", 0).coerceIn(0, Themes.list.lastIndex))
    private var _bigClock by mutableStateOf(p.getInt("bigClock", 1) == 1)
    private var _lockSettings by mutableStateOf(p.getInt("lockSettings", 0) == 1)
    private var _lockHidden by mutableStateOf(p.getInt("lockHidden", 0) == 1)
    private var _pin by mutableStateOf(p.getStr("pin", ""))
    private var _pauseWall by mutableStateOf(p.getInt("pauseWall", 1) == 1)
    // Paquete A: tarjeta de app enfocada, modo ahorro, modo noche y proteccion contra quemado.
    private var _saver by mutableStateOf(p.getInt("saver", 0) == 1)
    private var _uniformIcons by mutableStateOf(p.getInt("uniformIcons", 0) == 1)
    /** Lo activa Perf cuando hay poca RAM libre (modo ahorro automatico). */
    var lowRam by mutableStateOf(false)
    private var _fluid by mutableStateOf(p.getInt("fluid", 0) == 1)
    private var _ambDimOn by mutableStateOf(p.getInt("ambDimOn", 1) == 1)
    private var _ambDimLvl by mutableStateOf(p.getInt("ambDimLvl", 53))
    private var _ambAnim by mutableStateOf(p.getInt("ambAnim", 1) == 1)
    private var _ambPosTime by mutableStateOf(p.getInt("ambPosTime", 1))
    private var _ambPosDate by mutableStateOf(p.getInt("ambPosDate", 1))
    private var _ambPosMusic by mutableStateOf(p.getInt("ambPosMusic", 4))
    private var _ambPosIcons by mutableStateOf(p.getInt("ambPosIcons", 4))
    private var _burnIn by mutableStateOf(p.getInt("burnIn", 1) == 1)
    private var _updateCheck by mutableStateOf(p.getInt("updateCheck", 1) == 1)
    // Paquete 2: estilos de ficha y de foco, formato de fecha y barra superior que se oculta.
    private var _tileStyle by mutableStateOf(p.getInt("tileStyle", 0).coerceIn(0, 3))
    private var _focusStyle by mutableStateOf(p.getInt("focusStyle", 0).coerceIn(0, 3))
    private var _dateFmt by mutableStateOf(p.getInt("dateFmt", 0).coerceIn(0, 4))
    private var _hideBar by mutableStateOf(p.getInt("hideBar", 0) == 1)
    private var _hideBarSecs by mutableStateOf(p.getInt("hideBarSecs", 12).coerceIn(3, 60))
    private var _topMode by mutableStateOf(p.getInt("topMode", 0).coerceIn(0, 2))
    private var _topScale by mutableStateOf(p.getInt("topScale", 100).coerceIn(60, 150))
    // Mejoras 12 a 39.
    private var _newAppAlert by mutableStateOf(p.getInt("newAppAlert", 1) == 1)
    private var _openAnim by mutableStateOf(p.getInt("openAnim", 1) == 1)
    private var _bigText by mutableStateOf(p.getInt("bigText", 0) == 1)
    private var _highContrast by mutableStateOf(p.getInt("highContrast", 0) == 1)
    private var _thickFocus by mutableStateOf(p.getInt("thickFocus", 0) == 1)
    private var _keyRed by mutableStateOf(p.getInt("keyRed", 1).coerceIn(0, COLOR_ACTIONS.lastIndex))
    private var _keyGreen by mutableStateOf(p.getInt("keyGreen", 2).coerceIn(0, COLOR_ACTIONS.lastIndex))
    private var _keyYellow by mutableStateOf(p.getInt("keyYellow", 4).coerceIn(0, COLOR_ACTIONS.lastIndex))
    private var _keyBlue by mutableStateOf(p.getInt("keyBlue", 3).coerceIn(0, COLOR_ACTIONS.lastIndex))
    private var _accentCustom by mutableStateOf(p.getInt("accentCustom", 0))
    private var _sleepAction by mutableStateOf(p.getInt("sleepAction", 0).coerceIn(0, 1))
    private var _lastVersion by mutableStateOf(p.getInt("lastVersion", 0))

    val clock24: Boolean get() = _clock24
    val showDate: Boolean get() = _showDate
    val showWeather: Boolean get() = _showWeather
    val city: String get() = _city
    val fahrenheit: Boolean get() = _fahrenheit
    val showMusic: Boolean get() = _showMusic
    val dim: Int get() = _dim
    val ambient: Int get() = _ambient
    val sort: Int get() = _sort
    val musicSize: Int get() = _musicSize
    val musicPos: Int get() = _musicPos

    val overscan: Int get() = _overscan
    val uiScale: Int get() = _uiScale
    val glowSrc: Int get() = _glowSrc
    val sounds: Boolean get() = _soundLevel > 0
    /** 0 = apagado, 1 = suave, 2 = medio. */
    val soundLevel: Int get() = _soundLevel
    val theme: Int get() = _theme
    val bigClock: Boolean get() = _bigClock
    val lockSettings: Boolean get() = _lockSettings
    val lockHidden: Boolean get() = _lockHidden
    val pinHash: String get() = _pin
    val pinSet: Boolean get() = _pin.isNotEmpty()
    /** Fondo segun la app enfocada: 0 = apagado, 1 = degradado, 2 = banner + degradado. */
    /** Fundido / deslizamiento corto al cambiar de pantalla. */
    /** Pausa el fondo animado (video / GIF) mientras hay un panel, un menu o una app abierta. */
    val pauseWall: Boolean get() = _pauseWall

    /** Texto bajo el dock con el nombre de la app enfocada, notificaciones y ultimo uso. */
    /** Modo ahorro: apaga desenfoque, animaciones, fondo dinamico y brillo de color. */
    /** Efectos pesados apagados: lo activan "Modo ahorro" o "Fluidez máxima". */
    val saver: Boolean get() = _saver || _fluid || lowRam || Game.pauseBg(this)
    val saverOnly: Boolean get() = _saver
    val fluid: Boolean get() = _fluid
    /** Rutina diaria: a esta hora (una vez al dia) activa la escena de cine o abre una app. */
    /** Escena de cine: app a abrir, modo noche, oscurecimiento y temporizador de sueño (indice de SLEEP_MINS). */
    /** Fichas cuadradas o circulares: todas las imagenes se ven completas sobre un fondo del color de la app. */
    val uniformIcons: Boolean get() = _uniformIcons
    /** Sugerir el modo ahorro si los fotogramas por segundo bajan mucho. */
    /** Modo noche automatico entre dos horas. */
    /** Mueve lentamente el reloj del modo ambiente. */
    val burnIn: Boolean get() = _burnIn
    /** Modo ambiente: oscurecimiento, fondo animado y posicion (0-8 = cuadricula 3x3, 9 = oculto) de cada elemento. */
    val ambDimOn: Boolean get() = _ambDimOn
    val ambDimLvl: Int get() = _ambDimLvl
    val ambAnim: Boolean get() = _ambAnim
    val ambPosTime: Int get() = _ambPosTime
    val ambPosDate: Int get() = _ambPosDate
    val ambPosMusic: Int get() = _ambPosMusic
    val ambPosIcons: Int get() = _ambPosIcons

    /** Avisar una vez al dia, al abrir el inicio, si hay una version nueva en GitHub. */
    val updateCheck: Boolean get() = _updateCheck

    /** Estilo de las fichas del dock: 0 banner, 1 banner 16:9, 2 icono cuadrado, 3 circulo. */
    val tileStyle: Int get() = _tileStyle
    /** Foco de las fichas: 0 clasico, 1 aro grueso, 2 solo brillo, 3 solo escala. */
    val focusStyle: Int get() = _focusStyle
    /** Formato de la fecha (ver datePattern en Look.kt). */
    val dateFmt: Int get() = _dateFmt
    /** Oculta la barra superior tras unos segundos sin pulsar nada; Arriba la muestra. */
    val hideBar: Boolean get() = _hideBar
    val hideBarSecs: Int get() = _hideBarSecs
    /** Parte superior del inicio: 0 = completa, 1 = reducida, 2 = minima (solo reloj y ajustes). */
    val topMode: Int get() = _topMode
    /** Tamaño de la parte superior del inicio, en porcentaje (60 a 150). */
    val topScale: Int get() = _topScale

    // Mejoras 12 a 39 (lectura).
    val newAppAlert: Boolean get() = _newAppAlert
    val openAnim: Boolean get() = _openAnim
    val bigText: Boolean get() = _bigText
    val highContrast: Boolean get() = _highContrast
    val thickFocus: Boolean get() = _thickFocus
    val keyRed: Int get() = _keyRed
    val keyGreen: Int get() = _keyGreen
    val keyYellow: Int get() = _keyYellow
    val keyBlue: Int get() = _keyBlue
    val accentCustom: Int get() = _accentCustom
    val sleepAction: Int get() = _sleepAction
    val lastVersion: Int get() = _lastVersion

    /** Texto del boton de color segun su codigo (ver COLOR_ACTIONS). */
    fun colorAction(i: Int): Int = when (i) { 0 -> _keyRed; 1 -> _keyGreen; 2 -> _keyYellow; else -> _keyBlue }

    // Setters (se guardan al instante en Prefs).
    fun setClock24(v: Boolean) { _clock24 = v; p.putInt("clock24", if (v) 1 else 0) }
    fun setShowDate(v: Boolean) { _showDate = v; p.putInt("showDate", if (v) 1 else 0) }
    fun setShowWeather(v: Boolean) { _showWeather = v; p.putInt("showWeather", if (v) 1 else 0) }
    fun setCity(v: String) { _city = v; p.putStr("city", v) }
    fun setFahrenheit(v: Boolean) { _fahrenheit = v; p.putInt("fahrenheit", if (v) 1 else 0) }
    fun setShowMusic(v: Boolean) { _showMusic = v; p.putInt("showMusic", if (v) 1 else 0) }
    fun setDim(v: Int) { val x = v; _dim = x; p.putInt("dim", x) }
    fun setAmbient(v: Int) { val x = v; _ambient = x; p.putInt("ambient", x) }
    fun setSort(v: Int) { val x = v; _sort = x; p.putInt("sort", x) }
    fun setMusicSize(v: Int) { val x = v; _musicSize = x; p.putInt("musicSize", x) }
    fun setMusicPos(v: Int) { val x = v; _musicPos = x; p.putInt("musicPos", x) }
    fun setOverscan(v: Int) { val x = v; _overscan = x; p.putInt("overscan", x) }
    fun setUiScale(v: Int) { val x = v; _uiScale = x; p.putInt("uiScale", x) }
    fun setGlowSrc(v: Int) { val x = v; _glowSrc = x; p.putInt("glowSrc", x) }
    fun setSoundLevel(v: Int) { val x = v.coerceIn(0, 2); _soundLevel = x; p.putInt("sounds", x) }
    fun setTheme(v: Int) { val x = v.coerceIn(0, Themes.list.lastIndex); _theme = x; p.putInt("theme", x) }
    fun setBigClock(v: Boolean) { _bigClock = v; p.putInt("bigClock", if (v) 1 else 0) }
    fun setLockSettings(v: Boolean) { _lockSettings = v; p.putInt("lockSettings", if (v) 1 else 0) }
    fun setLockHidden(v: Boolean) { _lockHidden = v; p.putInt("lockHidden", if (v) 1 else 0) }
    fun setPin(v: String) { _pin = v; p.putStr("pin", v) }
    fun setPauseWall(v: Boolean) { _pauseWall = v; p.putInt("pauseWall", if (v) 1 else 0) }
    fun setSaver(v: Boolean) { _saver = v; p.putInt("saver", if (v) 1 else 0) }
    fun setUniformIcons(v: Boolean) { _uniformIcons = v; p.putInt("uniformIcons", if (v) 1 else 0) }
    fun setFluid(v: Boolean) { _fluid = v; p.putInt("fluid", if (v) 1 else 0) }
    fun setAmbDimOn(v: Boolean) { _ambDimOn = v; p.putInt("ambDimOn", if (v) 1 else 0) }
    fun setAmbDimLvl(v: Int) { val x = v; _ambDimLvl = x; p.putInt("ambDimLvl", x) }
    fun setAmbAnim(v: Boolean) { _ambAnim = v; p.putInt("ambAnim", if (v) 1 else 0) }
    fun setAmbPosTime(v: Int) { val x = v; _ambPosTime = x; p.putInt("ambPosTime", x) }
    fun setAmbPosDate(v: Int) { val x = v; _ambPosDate = x; p.putInt("ambPosDate", x) }
    fun setAmbPosMusic(v: Int) { val x = v; _ambPosMusic = x; p.putInt("ambPosMusic", x) }
    fun setAmbPosIcons(v: Int) { val x = v; _ambPosIcons = x; p.putInt("ambPosIcons", x) }
    fun setBurnIn(v: Boolean) { _burnIn = v; p.putInt("burnIn", if (v) 1 else 0) }
    fun setUpdateCheck(v: Boolean) { _updateCheck = v; p.putInt("updateCheck", if (v) 1 else 0) }
    fun setTileStyle(v: Int) { val x = v.coerceIn(0, 3); _tileStyle = x; p.putInt("tileStyle", x) }
    fun setFocusStyle(v: Int) { val x = v.coerceIn(0, 3); _focusStyle = x; p.putInt("focusStyle", x) }
    fun setDateFmt(v: Int) { val x = v.coerceIn(0, 4); _dateFmt = x; p.putInt("dateFmt", x) }
    fun setHideBar(v: Boolean) { _hideBar = v; p.putInt("hideBar", if (v) 1 else 0) }
    fun setHideBarSecs(v: Int) { val x = v.coerceIn(3, 60); _hideBarSecs = x; p.putInt("hideBarSecs", x) }
    fun setTopMode(v: Int) { val x = v.coerceIn(0, 2); _topMode = x; p.putInt("topMode", x) }
    fun setTopScale(v: Int) { val x = v.coerceIn(60, 150); _topScale = x; p.putInt("topScale", x) }
    fun setNewAppAlert(v: Boolean) { _newAppAlert = v; p.putInt("newAppAlert", if (v) 1 else 0) }
    fun setOpenAnim(v: Boolean) { _openAnim = v; p.putInt("openAnim", if (v) 1 else 0) }
    fun setBigText(v: Boolean) { _bigText = v; p.putInt("bigText", if (v) 1 else 0) }
    fun setHighContrast(v: Boolean) { _highContrast = v; p.putInt("highContrast", if (v) 1 else 0) }
    fun setThickFocus(v: Boolean) { _thickFocus = v; p.putInt("thickFocus", if (v) 1 else 0) }
    fun setKeyRed(v: Int) { val x = v.coerceIn(0, COLOR_ACTIONS.lastIndex); _keyRed = x; p.putInt("keyRed", x) }
    fun setKeyGreen(v: Int) { val x = v.coerceIn(0, COLOR_ACTIONS.lastIndex); _keyGreen = x; p.putInt("keyGreen", x) }
    fun setKeyYellow(v: Int) { val x = v.coerceIn(0, COLOR_ACTIONS.lastIndex); _keyYellow = x; p.putInt("keyYellow", x) }
    fun setKeyBlue(v: Int) { val x = v.coerceIn(0, COLOR_ACTIONS.lastIndex); _keyBlue = x; p.putInt("keyBlue", x) }
    fun setAccentCustom(v: Int) { val x = v; _accentCustom = x; p.putInt("accentCustom", x) }
    fun setSleepAction(v: Int) { val x = v.coerceIn(0, 1); _sleepAction = x; p.putInt("sleepAction", x) }
    fun setLastVersion(v: Int) { val x = v; _lastVersion = x; p.putInt("lastVersion", x) }
}
