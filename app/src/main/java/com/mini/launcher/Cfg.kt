package com.mini.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Nombres disponibles para las filas extra (indice 0 = fila desactivada). */
val ROW_NAMES = listOf(
    "Desactivada", "Streaming", "Juegos", "Herramientas", "Música", "Familia", "Deportes", "Noticias"
)

/** Ajustes de inicio, fondo y musica (se guardan en Prefs). */
class Cfg(private val p: Prefs) {
    private var _clock24 by mutableStateOf(p.getInt("clock24", 0) == 1)
    private var _showDate by mutableStateOf(p.getInt("showDate", 1) == 1)
    private var _showWeather by mutableStateOf(p.getInt("showWeather", 1) == 1)
    private var _city by mutableStateOf(p.getStr("city", CITY))
    private var _fahrenheit by mutableStateOf(p.getInt("fahrenheit", 0) == 1)
    private var _showMusic by mutableStateOf(p.getInt("showMusic", 1) == 1)
    private var _dim by mutableStateOf(p.getInt("dim", 0))
    private var _showRecents by mutableStateOf(p.getInt("showRecents", 1) == 1)
    private var _rot by mutableStateOf(p.getInt("rot", 0))
    private var _ambient by mutableStateOf(p.getInt("ambient", 2))
    private var _fade by mutableStateOf(p.getInt("fade", 1) == 1)
    private var _sort by mutableStateOf(p.getInt("sort", 0))
    private var _musicSize by mutableStateOf(p.getInt("musicSize", 80))
    private var _musicPos by mutableStateOf(p.getInt("musicPos", 0))

    // Nuevos
    private var _overscan by mutableStateOf(p.getInt("overscan", 0))
    private var _uiScale by mutableStateOf(p.getInt("uiScale", 100))
    private var _glowSrc by mutableStateOf(p.getInt("glowSrc", 0))
    private var _soundLevel by mutableStateOf(p.getInt("sounds", 1).coerceIn(0, 2))
    private var _theme by mutableStateOf(p.getInt("theme", 0).coerceIn(0, Themes.list.lastIndex))
    private var _blur by mutableStateOf(p.getInt("blur", 1) == 1)
    private var _bigClock by mutableStateOf(p.getInt("bigClock", 1) == 1)
    private var _voiceMode by mutableStateOf(p.getInt("voiceMode", 0))
    private var _lockSettings by mutableStateOf(p.getInt("lockSettings", 0) == 1)
    private var _lockHidden by mutableStateOf(p.getInt("lockHidden", 0) == 1)
    private var _pin by mutableStateOf(p.getStr("pin", ""))
    private var _dynBg by mutableStateOf(p.getInt("dynBg", 2).coerceIn(0, 2))
    private var _screenAnim by mutableStateOf(p.getInt("screenAnim", 1) == 1)
    private var _pauseWall by mutableStateOf(p.getInt("pauseWall", 1) == 1)
    // Paquete A: tarjeta de app enfocada, modo ahorro, modo noche y proteccion contra quemado.
    private var _focusCard by mutableStateOf(p.getInt("focusCard", 1) == 1)
    private var _saver by mutableStateOf(p.getInt("saver", 0) == 1)
    private var _saverHint by mutableStateOf(p.getInt("saverHint", 1) == 1)
    private var _night by mutableStateOf(p.getInt("night", 0) == 1)
    private var _nightFrom by mutableStateOf(p.getInt("nightFrom", 22).coerceIn(0, 23))
    private var _nightTo by mutableStateOf(p.getInt("nightTo", 7).coerceIn(0, 23))
    private var _burnIn by mutableStateOf(p.getInt("burnIn", 1) == 1)
    private var _updateCheck by mutableStateOf(p.getInt("updateCheck", 1) == 1)
    private val _rows = mutableStateListOf(
        p.getInt("row0", 0), p.getInt("row1", 0), p.getInt("row2", 0)
    )

    val clock24: Boolean get() = _clock24
    val showDate: Boolean get() = _showDate
    val showWeather: Boolean get() = _showWeather
    val city: String get() = _city
    val fahrenheit: Boolean get() = _fahrenheit
    val showMusic: Boolean get() = _showMusic
    val dim: Int get() = _dim
    val showRecents: Boolean get() = _showRecents
    val rot: Int get() = _rot
    val ambient: Int get() = _ambient
    val fade: Boolean get() = _fade
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
    val blur: Boolean get() = _blur
    val bigClock: Boolean get() = _bigClock
    val voiceMode: Int get() = _voiceMode
    val lockSettings: Boolean get() = _lockSettings
    val lockHidden: Boolean get() = _lockHidden
    val pinHash: String get() = _pin
    val pinSet: Boolean get() = _pin.isNotEmpty()
    /** Fondo segun la app enfocada: 0 = apagado, 1 = degradado, 2 = banner + degradado. */
    val dynBg: Int get() = _dynBg
    /** Fundido / deslizamiento corto al cambiar de pantalla. */
    val screenAnim: Boolean get() = _screenAnim
    /** Pausa el fondo animado (video / GIF) mientras hay un panel, un menu o una app abierta. */
    val pauseWall: Boolean get() = _pauseWall

    /** Texto bajo el dock con el nombre de la app enfocada, notificaciones y ultimo uso. */
    val focusCard: Boolean get() = _focusCard
    /** Modo ahorro: apaga desenfoque, animaciones, fondo dinamico y brillo de color. */
    val saver: Boolean get() = _saver
    /** Sugerir el modo ahorro si los fotogramas por segundo bajan mucho. */
    val saverHint: Boolean get() = _saverHint
    /** Modo noche automatico entre dos horas. */
    val night: Boolean get() = _night
    val nightFrom: Int get() = _nightFrom
    val nightTo: Int get() = _nightTo
    /** Mueve lentamente el reloj del modo ambiente. */
    val burnIn: Boolean get() = _burnIn

    /** Avisar una vez al dia, al abrir el inicio, si hay una version nueva en GitHub. */
    val updateCheck: Boolean get() = _updateCheck

    /** true si la hora (0..23) cae dentro del horario nocturno; admite cruzar la medianoche. */
    fun nightActive(hour: Int): Boolean = when {
        _nightFrom == _nightTo -> false
        _nightFrom < _nightTo -> hour >= _nightFrom && hour < _nightTo
        else -> hour >= _nightFrom || hour < _nightTo
    }

    /** Indice (en ROW_NAMES) de la fila extra i (0..2). */
    fun row(i: Int): Int = _rows[i]

    fun setRow(i: Int, v: Int) {
        _rows[i] = v
        p.putInt("row$i", v)
    }

    fun setClock24(v: Boolean) {
        _clock24 = v
        p.putInt("clock24", if (v) 1 else 0)
    }

    fun setShowDate(v: Boolean) {
        _showDate = v
        p.putInt("showDate", if (v) 1 else 0)
    }

    fun setShowWeather(v: Boolean) {
        _showWeather = v
        p.putInt("showWeather", if (v) 1 else 0)
    }

    fun setCity(v: String) {
        _city = v
        p.putStr("city", v)
    }

    fun setFahrenheit(v: Boolean) {
        _fahrenheit = v
        p.putInt("fahrenheit", if (v) 1 else 0)
    }

    fun setShowMusic(v: Boolean) {
        _showMusic = v
        p.putInt("showMusic", if (v) 1 else 0)
    }

    fun setDim(v: Int) {
        _dim = v
        p.putInt("dim", v)
    }

    fun setShowRecents(v: Boolean) {
        _showRecents = v
        p.putInt("showRecents", if (v) 1 else 0)
    }

    fun setRot(v: Int) {
        _rot = v
        p.putInt("rot", v)
    }

    fun setAmbient(v: Int) {
        _ambient = v
        p.putInt("ambient", v)
    }

    fun setFade(v: Boolean) {
        _fade = v
        p.putInt("fade", if (v) 1 else 0)
    }

    fun setSort(v: Int) {
        _sort = v
        p.putInt("sort", v)
    }

    fun setMusicSize(v: Int) {
        _musicSize = v
        p.putInt("musicSize", v)
    }

    fun setMusicPos(v: Int) {
        _musicPos = v
        p.putInt("musicPos", v)
    }

    fun setOverscan(v: Int) {
        _overscan = v
        p.putInt("overscan", v)
    }

    fun setUiScale(v: Int) {
        _uiScale = v
        p.putInt("uiScale", v)
    }

    fun setGlowSrc(v: Int) {
        _glowSrc = v
        p.putInt("glowSrc", v)
    }

    fun setSounds(v: Boolean) {
        setSoundLevel(if (v) 1 else 0)
    }

    fun setSoundLevel(v: Int) {
        _soundLevel = v
        p.putInt("sounds", v)
    }

    fun setTheme(v: Int) {
        _theme = v
        p.putInt("theme", v)
    }

    fun setBlur(v: Boolean) {
        _blur = v
        p.putInt("blur", if (v) 1 else 0)
    }

    fun setBigClock(v: Boolean) {
        _bigClock = v
        p.putInt("bigClock", if (v) 1 else 0)
    }

    fun setVoiceMode(v: Int) {
        _voiceMode = v
        p.putInt("voiceMode", v)
    }

    fun setLockSettings(v: Boolean) {
        _lockSettings = v
        p.putInt("lockSettings", if (v) 1 else 0)
    }

    fun setLockHidden(v: Boolean) {
        _lockHidden = v
        p.putInt("lockHidden", if (v) 1 else 0)
    }

    fun setPin(hash: String) {
        _pin = hash
        p.putStr("pin", hash)
    }

    fun setDynBg(v: Int) {
        _dynBg = v
        p.putInt("dynBg", v)
    }

    fun setScreenAnim(v: Boolean) {
        _screenAnim = v
        p.putInt("screenAnim", if (v) 1 else 0)
    }

    fun setPauseWall(v: Boolean) {
        _pauseWall = v
        p.putInt("pauseWall", if (v) 1 else 0)
    }

    fun setFocusCard(v: Boolean) {
        _focusCard = v
        p.putInt("focusCard", if (v) 1 else 0)
    }

    fun setSaver(v: Boolean) {
        _saver = v
        p.putInt("saver", if (v) 1 else 0)
    }

    fun setSaverHint(v: Boolean) {
        _saverHint = v
        p.putInt("saverHint", if (v) 1 else 0)
    }

    fun setNight(v: Boolean) {
        _night = v
        p.putInt("night", if (v) 1 else 0)
    }

    fun setNightFrom(v: Int) {
        _nightFrom = v.coerceIn(0, 23)
        p.putInt("nightFrom", _nightFrom)
    }

    fun setNightTo(v: Int) {
        _nightTo = v.coerceIn(0, 23)
        p.putInt("nightTo", _nightTo)
    }

    fun setUpdateCheck(v: Boolean) {
        _updateCheck = v
        p.putInt("updateCheck", if (v) 1 else 0)
    }

    fun setBurnIn(v: Boolean) {
        _burnIn = v
        p.putInt("burnIn", if (v) 1 else 0)
    }
}
