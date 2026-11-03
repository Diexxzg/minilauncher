package com.mini.launcher

import android.Manifest
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.Bundle
import android.view.KeyEvent as AKey
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    // tick: solo cambia al instalar/desinstalar/actualizar apps (recarga la lista).
    private var tick by mutableIntStateOf(0)
    private var homeTick by mutableIntStateOf(0)
    // resumeTick: al volver de una app; solo relee la carpeta de fondos (barato).
    private var resumeTick by mutableIntStateOf(0)
    // appPaused: el launcher no esta en primer plano (hay otra app abierta): pausa el fondo animado.
    private var appPaused by mutableStateOf(false)

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            tick++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, f, ContextCompat.RECEIVER_EXPORTED)
        addOnNewIntentListener { homeTick++ }
        setContent { HomeRoot(tick, homeTick, resumeTick, appPaused) }
    }

    override fun onResume() {
        super.onResume()
        appPaused = false
        resumeTick++
    }

    override fun onPause() {
        super.onPause()
        appPaused = true
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pkgReceiver)
        } catch (e: Exception) {
        }
    }
}

@Composable
fun HomeRoot(tick: Int, homeTick: Int, resumeTick: Int, appPaused: Boolean = false) {
    val ctx = LocalContext.current
    val baseDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(ctx) }
    val cfg = remember { Cfg(prefs) }
    var apps by remember { mutableStateOf(AppCache.apps) }
    var favs by remember { mutableStateOf(prefs.favs()) }
    var hidden by remember { mutableStateOf(prefs.hidden()) }
    var walls by remember { mutableStateOf<List<File>>(emptyList()) }
    var wallName by remember { mutableStateOf(prefs.wallpaper) }
    var media by remember { mutableStateOf<MediaInfo?>(null) }
    var screen by remember { mutableStateOf("home") }
    var inputs by remember { mutableStateOf<List<InputItem>>(emptyList()) }
    var scalePct by remember { mutableStateOf(prefs.getInt("scale", 100)) }
    var spacing by remember { mutableStateOf(prefs.getInt("spacing", 16)) }
    var corner by remember { mutableStateOf(prefs.getInt("corner", 18)) }
    var bottom by remember { mutableStateOf(prefs.getInt("bottom", 0)) }
    var glowLvl by remember { mutableStateOf(prefs.getInt("glow", 2)) }
    var sizes by remember { mutableStateOf(prefs.sizes()) }
    var recents by remember { mutableStateOf(prefs.recents()) }
    var usage by remember { mutableStateOf(prefs.usage()) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var minute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }
    val lastInput = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    // Nuevo: insignias, filas, red, PIN, energia, busqueda y acento.
    var seen by remember { mutableStateOf(prefs.seen()) }
    var unread by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var net by remember { mutableStateOf(NetState()) }
    var rowApps by remember { mutableStateOf((0..2).map { prefs.rowApps(it) }) }
    var pinAsk by remember { mutableStateOf<PinAsk?>(null) }
    var pinErr by remember { mutableStateOf<String?>(null) }
    var showPower by remember { mutableStateOf(false) }
    var searchFocus by remember { mutableStateOf(false) }
    var focusTick by remember { mutableIntStateOf(0) }
    var showMore by remember { mutableStateOf(false) }
    var moreBack by remember { mutableIntStateOf(0) }
    // Accesos rapidos (pulsacion larga en ajustes), carga inicial y categoria abierta en Configuracion.
    var showQuick by remember { mutableStateOf(false) }
    var quickBack by remember { mutableIntStateOf(0) }
    var appsLoaded by remember { mutableStateOf(AppCache.apps.isNotEmpty()) }
    // Configuracion: -1 = lista de categorias; 0.. = categoria abierta. La busqueda tambien vive aqui para que Atras la borre.
    var settingsCat by remember { mutableIntStateOf(-1) }
    var settingsQuery by remember { mutableStateOf("") }
    var focusedApp by remember { mutableStateOf<AppInfo?>(null) }
    // Ultimo uso de cada app (tarjeta bajo el dock) y aviso de modo ahorro.
    var lastUsed by remember { mutableStateOf(prefs.lastUsedMap()) }
    var showSaverHint by remember { mutableStateOf(false) }
    var saverSkip by remember { mutableStateOf(false) }
    // Actualizador (GitHub Releases): cuadro, aviso diario y vuelta del foco a Configuracion.
    val upd = remember { UpdaterState(ctx.applicationContext, prefs) }
    var updBack by remember { mutableIntStateOf(0) }
    // Barra superior que se oculta sola: oculta, foco dentro de ella y aviso para reiniciar el conteo.
    var barHidden by remember { mutableStateOf(false) }
    var barFocus by remember { mutableStateOf(false) }
    var barTick by remember { mutableIntStateOf(0) }
    var backdropApp by remember { mutableStateOf<AppInfo?>(null) }
    var prevScreen by remember { mutableStateOf("home") }
    var accentTarget by remember { mutableStateOf(Color.White) }
    val dockState = rememberLazyListState()

    // Modo ahorro y modo noche: valores efectivos. Los ajustes guardados no se tocan, asi que
    // al apagar el modo todo vuelve a como estaba.
    val hourNow = remember(minute) { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val night = cfg.night && cfg.nightActive(hourNow)
    val eAnim = cfg.screenAnim && !cfg.saver
    val eFade = cfg.fade && !cfg.saver
    val eBlur = cfg.blur && !cfg.saver
    val eDyn = if (cfg.saver) 0 else cfg.dynBg
    val eGlow = if (cfg.saver) 0 else if (night) (glowLvl - 1).coerceAtLeast(0) else glowLvl
    val eDim = if (night) (cfg.dim + 30).coerceAtMost(85) else cfg.dim

    // style: lo que se dibuja (con el brillo efectivo). realStyle: lo que muestra Configuracion.
    val style = remember(scalePct, spacing, corner, bottom, eGlow, sizes) {
        DockStyle(scalePct, spacing, corner, bottom, eGlow, sizes)
    }
    val realStyle = remember(scalePct, spacing, corner, bottom, glowLvl, sizes) {
        DockStyle(scalePct, spacing, corner, bottom, glowLvl, sizes)
    }
    val profileSel = profileIndex(scalePct, spacing, corner, glowLvl, cfg)

    // Tema de color: se fija antes de dibujar nada y se actualiza al cambiarlo.
    remember { Themes.set(cfg.theme); 0 }
    SideEffect { Themes.set(cfg.theme) }

    // Tecla Home: si ya estas en el inicio, vuelve al principio de la fila, devuelve el
    // foco a la primera ficha y sale del modo ambiente / menus.
    // Barra superior oculta: solo con el inicio libre (sin paneles ni menus) y sin foco en la barra.
    val barFree = screen == "home" && menuApp == null && !showMore && !showQuick &&
        !showPower && pinAsk == null && !showSaverHint && !upd.visible
    LaunchedEffect(cfg.hideBar, cfg.hideBarSecs, barFree, barFocus, barTick) {
        barHidden = false
        if (!cfg.hideBar || !barFree || barFocus) return@LaunchedEffect
        while (true) {
            delay(1000)
            if (SystemClock.elapsedRealtime() - lastInput.longValue >= cfg.hideBarSecs * 1000L) {
                barHidden = true
                break
            }
        }
    }

    // Limpia un APK viejo y, al volver del permiso de instalar, sigue con la instalacion.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { upd.cleanOld() } }
    LaunchedEffect(resumeTick) { upd.onResume() }

    // Aviso diario: una consulta al dia, unos segundos despues de abrir el inicio. Si en ese
    // momento hay un panel o menu abierto no se muestra nada (queda para "Buscar actualizaciones").
    LaunchedEffect(appsLoaded, screen) {
        if (screen == "home" && appsLoaded && cfg.updateCheck && upd.dueToday()) {
            delay(12_000)
            if (screen == "home" && !upd.visible) {
                val found = upd.check(false)
                if (found && screen == "home" && menuApp == null && !showMore && !showQuick &&
                    !showPower && pinAsk == null && !showSaverHint
                ) {
                    upd.visible = true
                }
            }
        }
    }

    LaunchedEffect(homeTick) {
        screen = "home"
        menuApp = null
        pinAsk = null
        showPower = false
        showMore = false
        showQuick = false
        showSaverHint = false
        if (homeTick > 0) {
            dockState.animateScrollToItem(0)
            focusTick++
        }
    }

    // Fondo segun la app enfocada: espera un instante para no parpadear al recorrer el dock.
    LaunchedEffect(focusedApp?.pkg) {
        delay(250)
        backdropApp = focusedApp
    }

    // Al cerrar Configuracion el inicio no se recompone: se devuelve el foco a la primera ficha.
    LaunchedEffect(screen) {
        if (screen == "home" && prevScreen == "settings") focusTick++
        if (screen == "home") {
            settingsCat = -1
            settingsQuery = ""
        }
        prevScreen = screen
    }

    LaunchedEffect(resumeTick) {
        walls = withContext(Dispatchers.IO) { listWalls(ctx) }
        lastInput.longValue = SystemClock.elapsedRealtime()
    }
        LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            minute = System.currentTimeMillis() / 60_000L
        }
    }

    LaunchedEffect(tick) {
        // Un reemplazo de app dispara varios avisos seguidos: se espera un poco.
        if (tick > 0) delay(500)
        val loaded = withContext(Dispatchers.IO) { loadApps(ctx) }
        AppCache.apps = loaded
        apps = loaded
        appsLoaded = true
        if (!prefs.initDone && loaded.isNotEmpty()) {
            favs = loaded.take(6).map { it.pkg }
            prefs.saveFavs(favs)
            prefs.initDone = true
        }
        // Primera vez con esta funcion: todo lo instalado cuenta como ya visto.
        if (!prefs.hasSeen() && loaded.isNotEmpty()) {
            seen = loaded.map { it.pkg }.toSet()
            prefs.saveSeen(seen)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val n = withContext(Dispatchers.IO) { readMedia(ctx) }
            if (!sameMedia(media, n)) media = n
            delay(1000)
        }
    }

    // Estado real de Wi-Fi / Ethernet / Bluetooth.
    LaunchedEffect(Unit) {
        while (true) {
            val n = withContext(Dispatchers.IO) { readNet(ctx) }
            if (n != net) net = n
            delay(4000)
        }
    }

    // Notificaciones sin leer por app.
    LaunchedEffect(Unit) {
        while (true) {
            val u = withContext(Dispatchers.IO) { unreadCounts(ctx.packageName) }
            if (u != unread) unread = u
            delay(3000)
        }
    }

    val m = media
    val art = remember(m?.title, m?.artist, m?.bitmap != null) { m?.bitmap?.asImageBitmap() }
    val mm = minute
    // Rotacion de fondos: por tiempo o segun la hora del dia.
    val selected: File? = if (walls.isEmpty()) null else when (cfg.rot) {
        1 -> walls[((mm / 30L) % walls.size).toInt()]
        2 -> walls[((mm / 60L) % walls.size).toInt()]
        3 -> walls[java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) * walls.size / 24]
        else -> walls.firstOrNull { it.name == wallName } ?: walls.first()
    }

    // Color de acento sacado del fondo actual.
    LaunchedEffect(selected?.name, cfg.glowSrc) {
        val f = selected
        if (cfg.glowSrc == 1 && f != null) {
            val c = withContext(Dispatchers.IO) {
                try {
                    loadBitmap(f, 64)?.asAndroidBitmap()?.let { dominantColor(it) }
                } catch (e: Exception) {
                    null
                }
            }
            if (c != null) accentTarget = Color(c)
        }
    }
    val accent by animateColorAsState(accentTarget, tween(900), label = "accent")

    // Modo ambiente: tras unos minutos sin tocar el control.
    LaunchedEffect(screen, cfg.ambient, menuApp) {
        if (screen == "home" && cfg.ambient > 0 && menuApp == null) {
            lastInput.longValue = SystemClock.elapsedRealtime()
            val limit = intArrayOf(0, 2, 5, 10, 20)[cfg.ambient.coerceIn(0, 4)] * 60_000L
            while (true) {
                delay(5_000)
                if (SystemClock.elapsedRealtime() - lastInput.longValue >= limit) {
                    screen = "ambient"
                    break
                }
            }
        }
    }

    BackHandler {
        when {
            pinAsk != null -> pinAsk = null
            upd.visible -> {
                upd.dismiss()
                focusTick++
                updBack++
            }
            showSaverHint -> {
                showSaverHint = false
                saverSkip = true
                focusTick++
            }
            showQuick -> {
                showQuick = false
                quickBack++
            }
            showMore -> {
                showMore = false
                moreBack++
            }
            showPower -> showPower = false
            screen == "walls" || screen == "edit" || screen == "dock" -> screen = "settings"
            screen == "settings" && settingsQuery.isNotEmpty() -> settingsQuery = ""
            screen == "settings" && settingsCat >= 0 -> settingsCat = -1
            screen == "hidden" -> screen = "all"
            else -> screen = "home"
        }
    }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val name = withContext(Dispatchers.IO) { importWall(ctx, uri) }
                walls = withContext(Dispatchers.IO) { listWalls(ctx) }
                if (name != null) {
                    wallName = name
                    prefs.wallpaper = name
                }
            }
        }
    }

    // Copias de seguridad: guardar donde elija el usuario y restaurar desde cualquier archivo.
    val backupSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { writeBackupTo(ctx, prefs, uri) }
                toast(ctx, if (ok) "Copia guardada." else "No se pudo guardar la copia.")
            }
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { readBackupFrom(ctx, prefs, uri) }
                if (ok) {
                    toast(ctx, "Copia restaurada.")
                    findActivity(ctx)?.recreate()
                } else {
                    toast(ctx, "Ese archivo no es una copia válida.")
                }
            }
        }
    }

    // Permiso opcional para leer el nombre de la red Wi-Fi y los dispositivos Bluetooth.
    val netPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { net = withContext(Dispatchers.IO) { readNet(ctx) } }
    }

    val visible = remember(apps, hidden) { apps.filter { it.pkg !in hidden } }
    val hiddenApps = remember(apps, hidden) { apps.filter { it.pkg in hidden } }
    val dock = remember(visible, favs) {
        val byPkg = visible.associateBy { it.pkg }
        favs.mapNotNull { byPkg[it] }
    }
    val newPkgs = remember(visible, seen) { visible.map { it.pkg }.filter { it !in seen }.toSet() }
    val badges = remember(newPkgs, unread) { Badges(newPkgs, unread) }

    // Filas extra (Streaming, Juegos...): solo las activas y con apps.
    val byPkgVisible = remember(visible) { visible.associateBy { it.pkg } }
    val rowList = ArrayList<Pair<String, List<AppInfo>>>()
    for (i in 0..2) {
        val ni = cfg.row(i)
        if (ni > 0 && ni < ROW_NAMES.size) {
            val l = rowApps[i].mapNotNull { byPkgVisible[it] }
            if (l.isNotEmpty()) rowList.add(ROW_NAMES[ni] to l)
        }
    }

    fun startNow(a: AppInfo) {
        val opts = try {
            ActivityOptions.makeCustomAnimation(ctx, 0, 0).toBundle()
        } catch (e: Exception) {
            null
        }
        try {
            ctx.startActivity(a.intent, opts)
        } catch (e: Exception) {
        }
    }

    val homeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    fun openApp(a: AppInfo) {
        recents = prefs.pushRecent(a.pkg)
        usage = prefs.bumpUsage(a.pkg)
        lastUsed = prefs.markUsed(a.pkg)
        seen = prefs.markSeen(a.pkg)
        TileBounds.last = null
        startNow(a)
    }

    // Perfil de diseno: aplica de una vez dock, brillo, fondo dinamico, filas y reloj.
    fun applyProfile(i: Int) {
        val pr = DESIGN_PROFILES.getOrNull(i) ?: return
        scalePct = pr.scale
        spacing = pr.spacing
        corner = pr.corner
        glowLvl = pr.glow
        prefs.putInt("scale", pr.scale)
        prefs.putInt("spacing", pr.spacing)
        prefs.putInt("corner", pr.corner)
        prefs.putInt("glow", pr.glow)
        cfg.setDynBg(pr.dynBg)
        cfg.setBigClock(pr.bigClock)
        for (r in 0..2) cfg.setRow(r, pr.rows[r])
    }

    val onSet: (String, Int) -> Unit = { k, v ->
        when (k) {
            "scale" -> {
                scalePct = v
                prefs.putInt("scale", v)
            }
            "spacing" -> {
                spacing = v
                prefs.putInt("spacing", v)
            }
            "corner" -> {
                corner = v
                prefs.putInt("corner", v)
            }
            "bottom" -> {
                bottom = v
                prefs.putInt("bottom", v)
            }
            "glow" -> {
                glowLvl = v
                prefs.putInt("glow", v)
            }
        }
    }

    val recentApps = remember(recents, visible) {
        val byPkg = visible.associateBy { it.pkg }
        recents.mapNotNull { byPkg[it] }.take(8)
    }
        // ---- PIN ----
    fun showPin(title: String, onCode: (String) -> Unit) {
        pinErr = null
        pinAsk = PinAsk(title, onCode)
    }

    fun askVerify(then: () -> Unit) {
        if (!cfg.pinSet) {
            then()
            return
        }
        showPin("Ingresa tu PIN") { code ->
            if (hashPin(code) == cfg.pinHash) {
                pinAsk = null
                then()
            } else {
                pinErr = "PIN incorrecto"
            }
        }
    }
        fun guarded(on: Boolean, then: () -> Unit) {
        if (on && cfg.pinSet) askVerify(then) else then()
        }
            fun createFlow() {
        showPin("Crea un PIN de 4 dígitos") { first ->
            showPin("Repite el PIN") { second ->
                if (second == first) {
                    cfg.setPin(hashPin(second))
                    pinAsk = null
                    toast(ctx, "PIN guardado.")
                } else {
                    createFlow()
                    pinErr = "No coinciden, intenta de nuevo"
                }
            }
        }
    }
        // ---- Voz / busqueda ----
    fun startVoice() {
        if (cfg.voiceMode == 0 && launchAssistant(ctx)) return
        searchFocus = true
        screen = "all"
    }

    // ---- Energia ----
    fun powerPick(kind: Int) {
        showPower = false
        when (kind) {
            0 -> screen = "black"
            1 -> systemPower(ctx, 1)
            2 -> systemPower(ctx, 2)
            else -> openSystem(ctx, "access")
        }
    }
        val uiScale = cfg.uiScale / 100f
    // Vista previa en vivo: en Apariencia y Dock el fondo no se oscurece ni se desenfoca,
    // y la primera ficha del dock se ve "enfocada" para que se note cada cambio.
    val preview = screen == "settings" && settingsCat in 0..1
    val sideOpen = screen == "settings" || screen == "edit"
    // Ancho que ocupa el panel lateral: el inicio se hace mas angosto para no quedar debajo.
    val availW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / uiScale -
        2f * cfg.overscan / uiScale
    val sideSpec: androidx.compose.animation.core.AnimationSpec<androidx.compose.ui.unit.Dp> =
        if (eAnim) tween(260) else androidx.compose.animation.core.snap()
    val sideInset by animateDpAsState(
        if (sideOpen) (sidePanelWidth(availW) + 20f).dp else 0.dp, sideSpec, label = "sideInset"
    )
    val dimLayer: Long = when (screen) {
        "all", "hidden" -> 0x99000000
        "walls" -> 0xAA000000
        "settings" -> if (preview) 0x14000000L else 0x40000000L
        "edit" -> 0x14000000
        "inputs" -> 0x55000000
        else -> 0L
    }

    val dimColor by animateColorAsState(Color(dimLayer), tween(300), label = "dim")

    // Desenfoque del fondo mientras hay un panel abierto (solo Android 12 o superior).
    val panelOpen = screen == "settings" || screen == "edit" || screen == "inputs" ||
        menuApp != null || showPower
    val blurDp by animateDpAsState(
        if (eBlur && panelOpen && !preview && screen != "edit") 24.dp else 0.dp, tween(350), label = "blur"
    )
    // Fondo animado (video / GIF): se pausa con paneles, menus o cuando hay otra app abierta.
    val wallPaused = cfg.pauseWall &&
        (appPaused || (screen != "home" && !preview) || menuApp != null || showPower || showMore)
    val bdApp: AppInfo? = when {
        screen == "home" -> backdropApp
        preview -> dock.firstOrNull()
        else -> null
    }
    // Si el control va muy lento varias veces seguidas, se sugiere el modo ahorro.
        SlowWatch(
        active = screen == "home" && !cfg.saver && cfg.saverHint && !saverSkip && !showSaverHint && !upd.visible &&
            menuApp == null && !showMore && !showQuick && !showPower && pinAsk == null,
        lastInputMs = { lastInput.longValue },
        onSlow = { showSaverHint = true }
    )
        Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1020))
            .onPreviewKeyEvent {
                lastInput.longValue = SystemClock.elapsedRealtime()
                val k = it.key
                // Arriba con la barra oculta: la muestra (la tecla sigue su camino normal).
                if (barHidden && it.type == KeyEventType.KeyDown && k == Key.DirectionUp) {
                    barHidden = false
                    barTick++
                }
                if (it.type == KeyEventType.KeyDown &&
                    (k == Key.Search || k == Key(AKey.KEYCODE_VOICE_ASSIST) || k == Key(AKey.KEYCODE_ASSIST)) &&
                    screen == "home" && pinAsk == null
                ) {
                    startVoice()
                    true
                } else {
                    false
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (blurDp.value > 0.5f) {
                        Modifier
                            .graphicsLayer {
                                val z = 1f + 0.08f * (blurDp.value / 24f)
                                scaleX = z
                                scaleY = z
                            }
                            .blur(blurDp)
                    } else {
                        Modifier
                    }
                )
        ) {
            Crossfade(
                targetState = selected,
                animationSpec = tween(if (eFade) 700 else 1),
                label = "wall"
            ) { f -> WallpaperLayer(f, wallPaused) }
            FocusBackdrop(bdApp, eDyn)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x22000000), Color.Transparent, Color(0x99000000)))
                )
            )
            if (eDim > 0) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = eDim / 100f)))
            }
        }
        if (screen == "ambient") {
            AmbientScreen(cfg, m) { screen = "home" }
        } else if (screen == "black") {
            BlackScreen { screen = "home" }
        } else {
            CompositionLocalProvider(
                LocalDock provides style,
                LocalDensity provides Density(baseDensity.density * uiScale, baseDensity.fontScale),
                LocalUiScale provides uiScale,
                LocalOverscan provides cfg.overscan,
                LocalAccent provides when (cfg.glowSrc) {
                    1 -> accent
                    2 -> Themes.current.acc
                    else -> null
                },
                LocalSounds provides cfg.sounds,
                LocalFocusStyle provides (if (cfg.saver && cfg.focusStyle == 2) 1 else cfg.focusStyle),
                LocalSoundVol provides (if (cfg.soundLevel == 2) 0.7f else 0.3f),
                LocalBadges provides badges
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (dimColor.alpha > 0.001f) {
                        Box(Modifier.fillMaxSize().background(dimColor))
                    }
                    if (screen == "settings" && cfg.overscan > 0) {
                        // Guia: el borde del area segura mientras ajustas el margen.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding((cfg.overscan / uiScale).dp)
                                .border(1.dp, Color(0x55FFFFFF))
                        )
                    }
                    Box(Modifier.fillMaxSize().padding((cfg.overscan / uiScale).dp)) {
                        AnimatedContent(
                            targetState = screen,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = { screenTransition(initialState, targetState, eAnim) },
                            label = "screen",
                            contentKey = { if (it == "home" || it == "settings" || it == "edit") "homeLayer" else it }
                        ) { s ->
                            when (s) {
                                "home", "settings", "edit" -> Box(Modifier.fillMaxSize()) {
                                    // El inicio queda debajo de Configuracion: asi no se duplica al abrir o cerrar el panel.
                                    val live = s == "home"
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(end = sideInset)
                                            .focusProperties { canFocus = live && !showMore && !showQuick && !showSaverHint && !upd.visible }
                                    ) {
                                        HomeScreen(
                                            tileStyle = cfg.tileStyle,
                                            barHidden = barHidden,
                                            onBarFocus = { barFocus = it },
                                            dock = dock,
                                            media = m,
                                            art = art,
                                            onLaunch = { if (live) openApp(it) },
                                            onAll = {
                                                if (live) {
                                                    searchFocus = false
                                                    screen = "all"
                                                }
                                            },
                                            onSettings = {
                                                if (live) guarded(cfg.lockSettings) { screen = "settings" }
                                            },
                                            onWifi = { if (live) openSystem(ctx, "wifi") },
                                            cfg = cfg,
                                            recents = recentApps,
                                            onMenu = { if (live) menuApp = it },
                                            rows = rowList,
                                            net = net,
                                            onMore = { if (live) showMore = true },
                                            moreBack = moreBack,
                                            onFocusApp = { a, f ->
                                                if (f) {
                                                    focusedApp = a
                                                } else if (focusedApp?.pkg == a.pkg) {
                                                    focusedApp = null
                                                }
                                            },
                                            dockState = dockState,
                                            focusTick = focusTick,
                                            loading = !appsLoaded,
                                            previewDock = preview,
                                            onQuick = { if (live) showQuick = true },
                                            quickBack = quickBack,
                                            focusedApp = if (preview) dock.firstOrNull() else focusedApp,
                                            lastUsed = lastUsed,
                                            sidePanel = s == "settings" || s == "edit"
                                        )
                                    }
                                    AnimatedVisibility(
                                        visible = s == "edit",
                                        enter = if (eAnim) {
                                            fadeIn(tween(240)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { h -> h / 24 }
                                        } else {
                                            EnterTransition.None
                                        },
                                        exit = if (eAnim) fadeOut(tween(160)) else ExitTransition.None
                                    ) {
                                        EditApps(
                                            apps = apps,
                                            favs = favs,
                                            hidden = hidden,
                                            sizes = sizes,
                                            onFav = { p ->
                                                favs = if (p in favs) favs - p else favs + p
                                                prefs.saveFavs(favs)
                                            },
                                            onUp = { p ->
                                                favs = move(favs, p, -1)
                                                prefs.saveFavs(favs)
                                            },
                                            onDown = { p ->
                                                favs = move(favs, p, 1)
                                                prefs.saveFavs(favs)
                                            },
                                            onHide = { p ->
                                                hidden = if (p in hidden) hidden - p else hidden + p
                                                prefs.saveHidden(hidden)
                                            },
                                            onSize = { p, v ->
                                                sizes = sizes + (p to v)
                                                prefs.saveSizes(sizes)
                                            }
                                        )
                                    }
                                                                        AnimatedVisibility(
                                        visible = s == "settings",
                                        enter = if (eAnim) {
                                            fadeIn(tween(240)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { h -> h / 24 }
                                        } else {
                                            EnterTransition.None
                                        },
                                        exit = if (eAnim) fadeOut(tween(160)) else ExitTransition.None
                                    ) {
                                        SettingsScreen(
                                            style = realStyle,
                                            cfg = cfg,
                                            onSet = onSet,
                                            onResetDock = {
                                                scalePct = 100
                                                spacing = 16
                                                corner = 18
                                                bottom = 0
                                                glowLvl = 2
                                                prefs.putInt("scale", 100)
                                                prefs.putInt("spacing", 16)
                                                prefs.putInt("corner", 18)
                                                prefs.putInt("bottom", 0)
                                                prefs.putInt("glow", 2)
                                            },
                                            cat = settingsCat,
                                            onCat = { settingsCat = it },
                                            query = settingsQuery,
                                            onQuery = { settingsQuery = it },
                                            onProfile = { applyProfile(it) },
                                            onUpdate = { upd.open(scope) },
                                            updBack = updBack,
                                            blocked = upd.visible,
                                            onBackupSave = {
                                                try {
                                                    backupSaver.launch(backupName())
                                                } catch (e: Exception) {
                                                    toast(ctx, "Esta TV no tiene selector de archivos.")
                                                }
                                            },
                                            onBackupShare = {
                                                if (!shareBackup(ctx, prefs)) {
                                                    toast(ctx, "No se pudo compartir la copia.")
                                                }
                                            },
                                            onRestorePick = {
                                                try {
                                                    backupPicker.launch(arrayOf("*/*"))
                                                } catch (e: Exception) {
                                                    toast(ctx, "Esta TV no tiene selector de archivos.")
                                                }
                                            },
                                            onWalls = { screen = "walls" },
                                            onEdit = { screen = "edit" },
                                            onInputs = {
                                                inputs = listInputs(ctx)
                                                screen = "inputs"
                                            },
                                            onOpen = { openSystem(ctx, it) },
                                            onSetHome = { requestHome(ctx) { homeLauncher.launch(it) } },
                                            onBackup = {
                                                scope.launch {
                                                    val ok = withContext(Dispatchers.IO) { writeBackup(ctx, prefs) }
                                                    toast(
                                                        ctx,
                                                        if (ok) "Copia guardada en: " + backupFile(ctx).path
                                                        else "No se pudo guardar la copia."
                                                    )
                                                }
                                            },
                                            onRestore = {
                                                scope.launch {
                                                    val ok = withContext(Dispatchers.IO) { readBackup(ctx, prefs) }
                                                    if (ok) {
                                                        toast(ctx, "Copia restaurada.")
                                                        findActivity(ctx)?.recreate()
                                                    } else {
                                                        toast(ctx, "No hay una copia válida para restaurar.")
                                                    }
                                                }
                                            },
                                            onNetPerm = {
                                                val perms = ArrayList<String>()
                                                perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
                                                if (Build.VERSION.SDK_INT >= 31) {
                                                    perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                                                }
                                                try {
                                                    netPerm.launch(perms.toTypedArray())
                                                } catch (e: Exception) {
                                                    toast(ctx, "Esta TV no permite pedir ese permiso.")
                                                }
                                            },
                                            onPin = { kind ->
                                                when (kind) {
                                                    "create" -> createFlow()
                                                    "change" -> askVerify { createFlow() }
                                                    "remove" -> askVerify {
                                                        cfg.setPin("")
                                                        cfg.setLockSettings(false)
                                                        cfg.setLockHidden(false)
                                                        pinAsk = null
                                                        toast(ctx, "PIN eliminado.")
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                                "inputs" -> InputsPanel(
                                    items = inputs,
                                    onPick = {
                                        openInput(ctx, it)
                                        screen = "home"
                                    },
                                    onClose = { screen = "home" }
                                )
                                "all" -> AllApps(
                                    visible, usage, recents, cfg.sort,
                                    { cfg.setSort(it) },
                                    { menuApp = it },
                                    focusSearch = searchFocus,
                                    loading = !appsLoaded,
                                    onHidden = if (hiddenApps.isNotEmpty()) {
                                        { guarded(cfg.lockHidden) { screen = "hidden" } }
                                    } else {
                                        null
                                    }
                                ) { openApp(it) }
                                "hidden" -> AllApps(
                                    hiddenApps, usage, recents, cfg.sort,
                                    { cfg.setSort(it) },
                                    { menuApp = it },
                                    title = "Apps ocultas"
                                ) { openApp(it) }
                                "walls" -> WallPicker(
                                    files = walls,
                                    current = if (wallName.isEmpty()) (walls.firstOrNull()?.name ?: "") else wallName,
                                    onPick = { n ->
                                        wallName = n
                                        prefs.wallpaper = n
                                    },
                                    onAdd = { picker.launch(arrayOf("image/*", "video/*")) }
                                )
                            }
                        }
                    }
                    val ma = menuApp
                    if (ma != null) {
                        val p = ma.pkg
                        AppMenu(
                            app = ma,
                            isFav = p in favs,
                            isHidden = p in hidden,
                            size = sizes[p] ?: 100,
                            onDismiss = { menuApp = null },
                            onFav = {
                                favs = if (p in favs) favs - p else favs + p
                                prefs.saveFavs(favs)
                            },
                            onMoveL = {
                                favs = move(favs, p, -1)
                                prefs.saveFavs(favs)
                            },
                            onMoveR = {
                                favs = move(favs, p, 1)
                                prefs.saveFavs(favs)
                            },
                            onHide = {
                                hidden = if (p in hidden) hidden - p else hidden + p
                                prefs.saveHidden(hidden)
                            },
                            onSize = { v ->
                                sizes = sizes + (p to v)
                                prefs.saveSizes(sizes)
                            },
                            onInfo = {
                                menuApp = null
                                openAppInfo(ctx, p)
                            },
                            onUninstall = {
                                menuApp = null
                                uninstallApp(ctx, p)
                            },
                            rowNames = (0..2).map { i ->
                                val ni = cfg.row(i)
                                if (ni > 0 && ni < ROW_NAMES.size) ROW_NAMES[ni] else null
                            },
                            inRow = (0..2).map { i -> p in rowApps[i] },
                            onRow = { i ->
                                val l = rowApps[i]
                                val nl = if (p in l) l - p else l + p
                                rowApps = rowApps.mapIndexed { j, x -> if (j == i) nl else x }
                                prefs.saveRowApps(i, nl)
                            }
                        )
                    }
                    if (showMore) {
                        MoreMenu(
                            onVoice = {
                                showMore = false
                                moreBack++
                                startVoice()
                            },
                            onInputs = {
                                showMore = false
                                inputs = listInputs(ctx)
                                screen = "inputs"
                            },
                            onPower = {
                                showMore = false
                                showPower = true
                            },
                            onClose = {
                                showMore = false
                                moreBack++
                            }
                        )
                    }
                    if (showQuick) {
                        QuickPanel(
                            cfg = cfg,
                            profile = profileSel,
                            onProfile = { applyProfile(it) },
                            onSettings = {
                                showQuick = false
                                quickBack++
                                guarded(cfg.lockSettings) { screen = "settings" }
                            },
                            onClose = {
                                showQuick = false
                                quickBack++
                            }
                        )
                    }
                                        if (showPower) {
                        PowerMenu(
                            accessOn = PowerService.instance != null,
                            onPick = { powerPick(it) },
                            onClose = { showPower = false }
                        )
                    }
                    if (showSaverHint) {
                        SaverHint(
                            onEnable = {
                                cfg.setSaver(true)
                                showSaverHint = false
                                saverSkip = true
                                focusTick++
                                toast(ctx, "Modo ahorro activado.")
                            },
                            onNever = {
                                cfg.setSaverHint(false)
                                showSaverHint = false
                                saverSkip = true
                                focusTick++
                            },
                            onClose = {
                                showSaverHint = false
                                saverSkip = true
                                focusTick++
                            }
                        )
                    }
                    if (upd.visible) {
                        UpdateDialog(
                            st = upd,
                            scope = scope,
                            onClose = {
                                upd.dismiss()
                                focusTick++
                                updBack++
                            }
                        )
                    }
                    val pa = pinAsk
                    if (pa != null) {
                        PinPad(pa, pinErr) { pinAsk = null }
                    }
                }
            }
        }
    }
}
