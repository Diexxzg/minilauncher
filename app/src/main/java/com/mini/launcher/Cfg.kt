package com.mini.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

}
