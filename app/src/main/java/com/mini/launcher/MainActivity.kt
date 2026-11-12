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
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Puente entre MainActivity y Configuracion (version 4.0). Configuracion lo usa sin cambiar
 * la firma de SettingsScreen: pide el servidor del celular, arranca el temporizador de sueno
 * y avisa cuando cambia algo que MainActivity debe releer.
 */
object HomeBridge {
    /** Temporizador de sueno (30): Configuracion llama start(mins) o cancel(). */
    val sleep = SleepTimer()
    /** Configuracion pone true al abrir "Conexion con el celular" y false al salir. */
    var phoneOn by mutableStateOf(false)
    /** MainActivity los llena cuando el servidor ya esta activo (vacios si esta apagado). */
    var phoneAddress by mutableStateOf("")
    var phoneCode by mutableStateOf("")
    /** Sube cuando un celular se empareja, para refrescar la lista en la TV. */
    var phonesTick by mutableIntStateOf(0)
    /** Subir en 1 despues de guardar carpetas (prefs.saveGroups). */
    var groupsTick by mutableIntStateOf(0)
    /** Subir en 1 para volver a aplicar nombres, iconos y brillos propios sobre las apps. */
    var appsTick by mutableIntStateOf(0)
}

private const val SEARCH_KEY = "recentSearches"

private fun loadSearches(prefs: Prefs): List<String> =
    prefs.getStr(SEARCH_KEY, "").split("\u0001").filter { it.isNotBlank() }.take(8)

/** Guarda una busqueda reciente (maximo 8, sin repetidas) y devuelve la lista nueva. */
private fun pushSearch(prefs: Prefs, q: String): List<String> {
    val t = q.trim()
    val cur = loadSearches(prefs)
    if (t.length < 2) return cur
    val l = (listOf(t) + cur.filter { !it.equals(t, ignoreCase = true) }).take(8)
    prefs.putStr(SEARCH_KEY, l.joinToString("\u0001"))
    return l
}

@Suppress("DEPRECATION")
private fun appVersionCode(ctx: Context): Int = try {
    val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
} catch (e: Exception) {
    0
}

/** Aviso de app nueva (19): ofrece anadirla al dock. */
@Composable
private fun NewAppDialog(label: String, onAdd: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .clip(shape)
                .background(Color(0xF0101624))
                .border(1.dp, Color(0x33FFFFFF), shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Nueva app instalada", color = Color.White, fontSize = 20.sp)
            Text(label, color = Color(0xB3FFFFFF), fontSize = 15.sp)
            ActionRow("Añadir al dock", Modifier.autoFocus()) { onAdd() }
            ActionRow("Ahora no") { onClose() }
        }
    }
}

class MainActivity : ComponentActivity() {
    // tick: solo cambia al instalar/desinstalar/actualizar apps (recarga la lista).
    private var tick by mutableIntStateOf(0)
    private var homeTick by mutableIntStateOf(0)
    // resumeTick: al volver de una app; solo relee la carpeta de fondos (barato).
    private var resumeTick by mutableIntStateOf(0)
    // appPaused: el launcher no esta en primer plano (hay otra app abierta): pausa el fondo animado.
    private var appPaused by mutableStateOf(false)
    // addedPkg: ultima app recien instalada (no actualizada); HomeRoot ofrece anadirla al dock.
    private var addedPkg by mutableStateOf("")

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i != null && i.action == Intent.ACTION_PACKAGE_ADDED &&
                !i.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            ) {
                val p = i.data?.schemeSpecificPart
                if (!p.isNullOrEmpty()) addedPkg = p
            }
            tick++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Home1.checkTrial(this)
        Safe.armReset(this)
        AutoBackup.runIfDue(this)
        if (Safe.takeNotice(this)) toast(this, "Modo seguro: el launcher falló 3 veces al abrir y volvió a ajustes mínimos. Tu copia está en Acerca de › Restaurar copia automática.")
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, pkgReceiver, f, ContextCompat.RECEIVER_EXPORTED)
        Remap.load(this)
        addOnNewIntentListener { i ->
            homeTick++
            if (i.getIntExtra("dAct", 0) != 0) route(i)
        }
        if (savedInstanceState == null) route(intent)
        setContent { HomeRoot(tick, homeTick, resumeTick, appPaused, addedPkg) { addedPkg = "" } }
    }

    private fun route(i: Intent?) {
        val a = i?.getIntExtra("dAct", 0) ?: 0
        if (a == 98) Daily.ring(this, i?.getStringExtra("dMsg") ?: "") else if (a != 0) ActBus.post(a)
    }

    override fun onResume() {
        super.onResume()
        appPaused = false
        resumeTick++
        Perf.resolveLaunch(this)
        Home1.ensureAccess(this)
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
fun HomeRoot(
    tick: Int,
    homeTick: Int,
    resumeTick: Int,
    appPaused: Boolean = false,
    addedPkg: String = "",
    onAddedHandled: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val baseDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(ctx) }
    val cfg = remember { Cfg(prefs) }
    // baseApps: lista tal como la carga el sistema; apps: con nombre, brillo e icono propios (16).
    var baseApps by remember { mutableStateOf(AppCache.apps) }
    var apps by remember { mutableStateOf(applyOverrides(ctx, prefs, AppCache.apps)) }
    var ovTick by remember { mutableIntStateOf(0) }
    var favs by remember { mutableStateOf(prefs.favs()) }
    var habitTick by remember { mutableIntStateOf(0) }
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
    var pinAsk by remember { mutableStateOf<PinAsk?>(null) }
    var pinErr by remember { mutableStateOf<String?>(null) }
    var showPower by remember { mutableStateOf(false) }
    var searchFocus by remember { mutableStateOf(false) }
    var focusTick by remember { mutableIntStateOf(0) }
    var lastDockPkg by remember { mutableStateOf<String?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var moreBack by remember { mutableIntStateOf(0) }
    // Accesos rapidos (pulsacion larga en ajustes), carga inicial y categoria abierta en Configuracion.
    var showQuick by remember { mutableStateOf(false) }
    var quickBack by remember { mutableIntStateOf(0) }
    var appsLoaded by remember { mutableStateOf(AppCache.apps.isNotEmpty()) }
    // Configuracion: categoria seleccionada en la columna izquierda (siempre hay una).
    var settingsCat by remember { mutableIntStateOf(-1) }
    // true mientras el foco esta en las opciones (derecha); Atras primero lo devuelve a las categorias.
    var settingsRight by remember { mutableStateOf(false) }
    var railBack by remember { mutableIntStateOf(0) }
    var saverSkip by remember { mutableStateOf(false) }
    // Actualizador (GitHub Releases): cuadro, aviso diario y vuelta del foco a Configuracion.
    val upd = remember { UpdaterState(ctx.applicationContext, prefs) }
    // Barra superior que se oculta sola: oculta, foco dentro de ella y aviso para reiniciar el conteo.
    var barHidden by remember { mutableStateOf(false) }
    var barFocus by remember { mutableStateOf(false) }
    var barTick by remember { mutableIntStateOf(0) }
    var prevScreen by remember { mutableStateOf("home") }
    var accentTarget by remember { mutableStateOf(Color.White) }
    val dockState = rememberLazyListState()

    // Version 4.0: carpetas, busquedas, celular, nota, novedades, aviso de app nueva y clima.
    val view = LocalView.current
    var groupMap by remember { mutableStateOf(prefs.groups()) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var recentSearches by remember { mutableStateOf(loadSearches(prefs)) }
    var extQuery by remember { mutableStateOf("") }
    var iconPkg by remember { mutableStateOf<String?>(null) }
    var newAppAsk by remember { mutableStateOf<AppInfo?>(null) }
    var whatsNew by remember { mutableStateOf<Pair<String, String>?>(null) }
    val sleep = HomeBridge.sleep
    val verCode = remember { appVersionCode(ctx) }

    // Modo ahorro: valores efectivos. Los ajustes guardados no se tocan, asi que
    // al apagar el modo todo vuelve a como estaba.
    val eAnim = !cfg.saver
    val eFade = !cfg.saver
    val eGlow = if (cfg.saver) 0 else glowLvl
    val eDim = cfg.dim

    // style: lo que se dibuja (con el brillo efectivo). realStyle: lo que muestra Configuracion.
    val style = remember(scalePct, spacing, corner, bottom, eGlow, sizes) {
        DockStyle(scalePct, spacing, corner, bottom, eGlow, sizes)
    }
    val realStyle = remember(scalePct, spacing, corner, bottom, glowLvl, sizes) {
        DockStyle(scalePct, spacing, corner, bottom, glowLvl, sizes)
    }

    // Tema de color: se fija antes de dibujar nada y se actualiza al cambiarlo.
    val themeIdx = Themes.pick(cfg)
    remember { Themes.set(themeIdx, cfg.accentCustom); 0 }
    SideEffect { Themes.set(themeIdx, cfg.accentCustom) }

    // Tecla Home: si ya estas en el inicio, vuelve al principio de la fila, devuelve el
    // foco a la primera ficha y sale del modo ambiente / menus.
    // Barra superior oculta: solo con el inicio libre (sin paneles ni menus) y sin foco en la barra.
    val homeFree = screen == "home" && menuApp == null && !showMore && !showQuick &&
        !showPower && pinAsk == null && !upd.visible &&
        openFolder == null && newAppAsk == null && whatsNew == null
    val barFree = homeFree
    LaunchedEffect(cfg.hideBar, cfg.hideBarSecs, barFree, barFocus, barTick) {
        barHidden = false
        if (!cfg.hideBar || !barFree || barFocus) return@LaunchedEffect
        while (true) {
            val left = cfg.hideBarSecs * 1000L - (SystemClock.elapsedRealtime() - lastInput.longValue)
            if (left <= 0L) {
                barHidden = true
                break
            }
            delay(left)
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
                    !showPower && pinAsk == null
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
        openFolder = null
        if (homeTick > 0) {
            lastDockPkg = null
            dockState.animateScrollToItem(0)
            focusTick++
        }
    }
    LaunchedEffect(screen) {
        if (screen == "home") {
            favs = prefs.favs()
            groupMap = prefs.groups()
            habitTick++
        }
    }

        // Al cerrar Configuracion el inicio no se recompone: se devuelve el foco a la primera ficha.
    LaunchedEffect(screen) {
        if (screen == "home" && prevScreen == "settings") focusTick++
        if (screen == "home") {
            settingsCat = -1
            settingsRight = false
        }
        if (screen == "home" || (screen != "all" && prevScreen == "all")) extQuery = ""
        if (screen != "settings") HomeBridge.phoneOn = false
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
        val shown = withContext(Dispatchers.IO) { applyOverrides(ctx, prefs, loaded) }
        AppCache.apps = loaded
        baseApps = loaded
        apps = shown
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

    // ---- Version 4.0: efectos ----
    // Nombres, iconos y brillos propios (16): se vuelven a aplicar sobre la lista cargada.
    LaunchedEffect(ovTick, HomeBridge.appsTick) {
        if (ovTick == 0 && HomeBridge.appsTick == 0) return@LaunchedEffect
        apps = withContext(Dispatchers.IO) { applyOverrides(ctx, prefs, baseApps) }
    }

    // Carpetas editadas desde Configuracion (17).
    LaunchedEffect(HomeBridge.groupsTick) { groupMap = prefs.groups() }

    // Temporizador de sueno (30): al cumplirse pasa a pantalla negra o modo ambiente.
    LaunchedEffect(sleep.active) {
        while (sleep.active) {
            delay(2000)
            if (sleep.due()) {
                screen = if (cfg.sleepAction == 1) "ambient" else "black"
                break
            }
        }
    }

    // Conexion con el celular (36-38): el servidor solo vive mientras Configuracion lo pida.
    // Con «Mantener la conexión activa» (apagado por defecto) sigue vivo mientras el launcher viva.
    val wantPhone = HomeBridge.phoneOn || cfg.ex("phoneKeep") == "1"
    LaunchedEffect(wantPhone) {
        if (!wantPhone) return@LaunchedEffect
        val ui = Handler(Looper.getMainLooper())
        val srv = PhoneServer(
            ctx.applicationContext, prefs,
            onText = { t ->
                ui.post {
                    extQuery = t
                    if (screen == "home" || screen == "all") {
                        searchFocus = false
                        screen = "all"
                    } else {
                        toast(ctx, "Texto recibido del celular.")
                    }
                }
            },
            onWall = {
                ui.post {
                    scope.launch { walls = withContext(Dispatchers.IO) { listWalls(ctx) } }
                }
            },
            onConfig = { ui.post { findActivity(ctx)?.recreate() } },
            onPair = { ui.post { HomeBridge.phonesTick++ } },
            onKey = { k ->
                ui.post {
                    findActivity(ctx)?.let { act ->
                        act.dispatchKeyEvent(AKey(AKey.ACTION_DOWN, k))
                        act.dispatchKeyEvent(AKey(AKey.ACTION_UP, k))
                    }
                }
            },
            onSchema = { RemoteSchema.json(cfg, prefs, ctx.applicationContext) },
            onSetRow = { id, v -> ui.post { RemoteSchema.apply(cfg, prefs, ctx.applicationContext, id, v) } }
        )
        val ok = withContext(Dispatchers.IO) { srv.start() }
        if (!ok) {
            toast(ctx, "No se pudo abrir la conexión con el celular.")
            HomeBridge.phoneOn = false
            return@LaunchedEffect
        }
        HomeBridge.phoneCode = srv.code
        HomeBridge.phoneAddress = withContext(Dispatchers.IO) { srv.address() }
        try {
            awaitCancellation()
        } finally {
            srv.stop()
            HomeBridge.phoneAddress = ""
            HomeBridge.phoneCode = ""
        }
    }

    LaunchedEffect(cfg.uniformIcons) { UiPrefs.uniform = cfg.uniformIcons }

    // Aviso de app nueva (19): al instalar una app se ofrece anadirla al dock.
    LaunchedEffect(addedPkg, apps) {
        if (addedPkg.isEmpty()) return@LaunchedEffect
        val a = apps.firstOrNull { it.pkg == addedPkg } ?: return@LaunchedEffect
        if (cfg.newAppAlert && a.pkg !in favs) newAppAsk = a
        onAddedHandled()
    }

    // Novedades (35): tras actualizar, se muestran las notas del ultimo Release.
    LaunchedEffect(appsLoaded) {
        if (!appsLoaded || verCode == 0) return@LaunchedEffect
        if (cfg.lastVersion == 0) {
            cfg.setLastVersion(verCode)
        } else if (cfg.lastVersion != verCode) {
            delay(4000)
            val n = withContext(Dispatchers.IO) { fetchReleaseNotes() }
            if (n != null) {
                while (screen != "home" || menuApp != null) delay(2000)
                whatsNew = n
            }
        }
    }


    // Musica: se relee solo cuando cambia una sesion de media (callbacks), mas un repaso por minuto.
    var mediaTick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(Notif.link) {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
        val comp = android.content.ComponentName(ctx, MediaListener::class.java)
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val cbs = HashMap<android.media.session.MediaController, android.media.session.MediaController.Callback>()
        fun attach(list: List<android.media.session.MediaController>?) {
            for ((c, k) in cbs) try { c.unregisterCallback(k) } catch (e: Exception) { }
            cbs.clear()
            for (c in list ?: emptyList()) {
                val k = object : android.media.session.MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) { mediaTick++ }
                    override fun onMetadataChanged(metadata: android.media.MediaMetadata?) { mediaTick++ }
                    override fun onSessionDestroyed() { mediaTick++ }
                }
                try { c.registerCallback(k, h); cbs[c] = k } catch (e: Exception) { }
            }
            mediaTick++
        }
        val sl = android.media.session.MediaSessionManager.OnActiveSessionsChangedListener { attach(it) }
        try {
            msm.addOnActiveSessionsChangedListener(sl, comp, h)
            attach(msm.getActiveSessions(comp))
        } catch (e: Exception) { }
        onDispose {
            try { msm.removeOnActiveSessionsChangedListener(sl) } catch (e: Exception) { }
            for ((c, k) in cbs) try { c.unregisterCallback(k) } catch (e: Exception) { }
            cbs.clear()
        }
    }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { Pair(mediaTick, minute) }.collect {
            val n = withContext(Dispatchers.IO) { readMedia(ctx) }
            if (!sameMedia(media, n)) media = n
        }
    }

    // Estado real de Wi-Fi / Ethernet / Bluetooth.
    var netTick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { netTick++ }
            override fun onLost(network: android.net.Network) { netTick++ }
            override fun onCapabilitiesChanged(network: android.net.Network, c: android.net.NetworkCapabilities) { netTick++ }
        }
        try { cm.registerDefaultNetworkCallback(cb) } catch (e: Exception) { }
        val rx = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { netTick++ }
        }
        val f = android.content.IntentFilter().apply {
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(android.net.wifi.WifiManager.RSSI_CHANGED_ACTION)
        }
        try { ContextCompat.registerReceiver(ctx, rx, f, ContextCompat.RECEIVER_EXPORTED) } catch (e: Exception) { }
        onDispose {
            try { cm.unregisterNetworkCallback(cb) } catch (e: Exception) { }
            try { ctx.unregisterReceiver(rx) } catch (e: Exception) { }
        }
    }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { Pair(netTick, minute) }.collect {
            delay(400)
            val n = withContext(Dispatchers.IO) { readNet(ctx) }
            if (n != net) net = n
        }
    }

    // Notificaciones sin leer por app.
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { Pair(Notif.tick, minute) }.collect {
            val u = withContext(Dispatchers.IO) { unreadCounts(ctx.packageName) }
            if (u != unread) unread = u
        }
    }

    val m = media
    val art = remember(m?.title, m?.artist, m?.bitmap != null) { m?.bitmap?.asImageBitmap() }
    val mm = minute
    val selected: File? = if (walls.isEmpty()) null else walls.firstOrNull { it.name == wallName } ?: walls.first()

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

    // Al volver de "restablecer/importar" se reabre Configuracion; en el primer uso se ofrece el asistente.
    LaunchedEffect(Unit) {
        delay(400)
        val r = prefs.getInt("reopenCat", -1)
        if (r >= 0) {
            prefs.putInt("reopenCat", -1)
            settingsCat = r.coerceIn(0, 7)
            screen = "settings"
        }
    }

    // Un solo lugar para "Atras": lo usan la tecla (antes de que llegue a nada mas) y BackHandler.
    fun goBack() {
        when {
            pinAsk != null -> pinAsk = null
            openFolder != null -> openFolder = null
            newAppAsk != null -> newAppAsk = null
            upd.visible -> {
                upd.dismiss()
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
            screen == "settings" && settingsRight -> railBack++
            screen == "hidden" -> screen = "all"
            else -> screen = "home"
        }
    }

    BackHandler { goBack() }
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

    // Icono propio de una app (16).
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val p = iconPkg
        iconPkg = null
        if (uri != null && p != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { importIcon(ctx, uri, p) }
                toast(ctx, if (ok) "Icono guardado." else "No se pudo usar esa imagen.")
                if (ok) ovTick++
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

    // Perfil para ninos (34): solo las apps permitidas. Carpetas (17): sus apps no salen sueltas en el dock.
    val visible = remember(apps, hidden) { apps.filter { it.pkg !in hidden } }
    val hiddenApps = remember(apps, hidden) { apps.filter { it.pkg in hidden } }
    val folderNames = remember(groupMap) { groupMap.keys.take(MAX_FOLDERS) }
    val inFolders = remember(groupMap, folderNames) {
        folderNames.flatMap { groupMap[it] ?: emptyList() }.toSet()
    }
    val dock = remember(visible, favs, inFolders, habitTick) {
        val byPkg = visible.associateBy { it.pkg }
        favs.filter { it !in inFolders }.mapNotNull { byPkg[it] }
    }
    val groupList = remember(groupMap, folderNames, visible) {
        val byPkg = visible.associateBy { it.pkg }
        folderNames.map { n -> n to (groupMap[n] ?: emptyList()).mapNotNull { byPkg[it] } }
    }
    val newPkgs = remember(visible, seen) { visible.map { it.pkg }.filter { it !in seen }.toSet() }
    val badges = remember(newPkgs, unread) { Badges(newPkgs, unread) }

    fun startNow(a: AppInfo, b: Rect?) {
        val opts = try {
            if (cfg.openAnim && !cfg.saver && b != null && b.width > 1f && b.height > 1f) {
                // Animacion (27): la app crece desde la ficha que se pulso.
                ActivityOptions.makeScaleUpAnimation(
                    view, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt()
                ).toBundle()
            } else {
                ActivityOptions.makeCustomAnimation(ctx, 0, 0).toBundle()
            }
        } catch (e: Exception) {
            null
        }
        try {
            ctx.startActivity(a.intent, opts)
        } catch (e: Exception) {
        }
    }

    val homeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    remember { Perf.load(ctx); 0 }
    var home1Open by remember { mutableStateOf(Home1.firstRunPending(ctx)) }
    if (home1Open) HomeDialog({ home1Open = false }) { requestHome(ctx) { homeLauncher.launch(it) } }
    fun openAppNow(a: AppInfo) {
        recents = prefs.pushRecent(a.pkg)
        usage = prefs.bumpUsage(a.pkg)
        Perf.noteLaunch(a.pkg)
        prefs.bumpDay(a.pkg)
        seen = prefs.markSeen(a.pkg)
        val b = TileBounds.last
        TileBounds.last = null
        startNow(a, b)
    }

    fun openApp(a: AppInfo) { openAppNow(a) }

        // Carpetas (17): una app esta en una sola carpeta a la vez.
    fun toggleFolder(p: String, i: Int) {
        val n = folderNames.getOrNull(i) ?: return
        val inIt = p in (groupMap[n] ?: emptyList())
        val nm = LinkedHashMap<String, List<String>>()
        for ((k, v) in groupMap) {
            nm[k] = if (k == n) {
                if (inIt) v - p else v + p
            } else {
                v - p
            }
        }
        groupMap = nm
        prefs.saveGroups(nm)
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
        if (launchAssistant(ctx)) return
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
    // ---- Rendimiento: ahorro automatico, limpieza nocturna y pre-calentado ----
    LaunchedEffect(Perf.autoSaver) {
        if (!Perf.autoSaver) {
            cfg.lowRam = false
            return@LaunchedEffect
        }
        while (true) {
            cfg.lowRam = Perf.ramLow(ctx)
            delay(30_000)
        }
    }
    LaunchedEffect(Perf.nightClean, Perf.warmOn) {
        while (Perf.nightClean || Perf.warmOn) {
            val cal = java.util.Calendar.getInstance()
            val today = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
            val nowM = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            if (Perf.nightClean && nowM in 180..210 && prefs.getInt("cleanLast", 0) != today) {
                prefs.putInt("cleanLast", today)
                Perf.cleanAsync(ctx, true)
            }
            val at = Perf.warmHour * 60
            if (Perf.warmOn && Perf.warmPkg.isNotEmpty() && screen == "home" && !appPaused &&
                nowM >= at && nowM <= at + 30 && prefs.getInt("warmLast", 0) != today
            ) {
                prefs.putInt("warmLast", today)
                Perf.warm(ctx)
            }
            delay(30_000)
        }
    }

    // ---- Botones de color (10) ----
    fun runColorAction(a: Int) {
        when (a) {
            1 -> {
                searchFocus = false
                screen = "all"
            }
            2 -> guarded(cfg.lockSettings) { screen = "settings" }
            3 -> {
                inputs = listInputs(ctx)
                screen = "inputs"
            }
            4 -> {
                searchFocus = true
                screen = "all"
            }
            5 -> showPower = true
            6 -> guarded(cfg.lockSettings) { screen = "edit" }
            7 -> screen = "ambient"
            8 -> screen = "black"
            9 -> {
                Perf.cleanAsync(ctx)
            }
        }
    }
    // Acciones pedidas desde fuera (remapeo).
    LaunchedEffect(ActBus.n) {
        val a = ActBus.act
        if (a == 0) return@LaunchedEffect
        ActBus.act = 0
        runColorAction(a)
    }

    val uiScale = cfg.uiScale / 100f
    // Vista previa en vivo: en Apariencia y Dock el fondo no se oscurece ni se desenfoca,
    // y la primera ficha del dock se ve "enfocada" para que se note cada cambio.
    val preview = screen == "settings" && settingsPreviewCat(settingsCat)
    val sideOpen = screen == "settings" || screen == "edit"
    // Ancho que ocupa el panel lateral: el inicio se hace mas angosto para no quedar debajo.
    val availW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / uiScale -
        2f * cfg.overscan / uiScale
    val sideSpec: androidx.compose.animation.core.AnimationSpec<androidx.compose.ui.unit.Dp> =
        if (eAnim) tween(260) else androidx.compose.animation.core.snap()
    val sideInset by animateDpAsState(
        if (sideOpen && screen != "settings") (sidePanelWidth(availW, false) + 20f).dp else 0.dp, sideSpec, label = "sideInset"
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

    // Fondo animado (video / GIF): se pausa con paneles, menus o cuando hay otra app abierta.
    val wallPaused = (screen == "ambient" && (!cfg.ambAnim || cfg.saver)) ||
        (cfg.pauseWall &&
            (appPaused || (screen != "home" && screen != "ambient" && !preview) || menuApp != null || showPower || showMore))
    // Si el control va muy lento varias veces seguidas, se sugiere el modo ahorro.
        SlowWatch(
        active = homeFree && !cfg.saver && Perf.autoFluid && !saverSkip,
        lastInputMs = { lastInput.longValue },
        onSlow = {
            cfg.setFluid(true)
            saverSkip = true
            toast(ctx, "Fluidez máxima activada automáticamente.")
        }
    )
        Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1020))
            .onPreviewKeyEvent {
                lastInput.longValue = SystemClock.elapsedRealtime()
                val k = it.key
                // Atras en Configuracion se atiende aqui, antes que cualquier otra cosa: una
                // pulsacion = un nivel. Se ignoran las repeticiones al mantener la tecla.
                if (k == Key.Back && screen == "settings") {
                    if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) {
                        goBack()
                    }
                    return@onPreviewKeyEvent true
                }
                // Botones de color (13): solo con el inicio libre y fuera del perfil para ninos.
                if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0 &&
                    homeFree
                ) {
                    val act = colorKeyAction(cfg, k)
                    val cx = colorKeyIndex(k)?.let { ci -> cfg.colorExtra(ci) } ?: ""
                    if (cx.isNotEmpty() && Remap.runExtra(ctx, cx)) return@onPreviewKeyEvent true
                    if (act != null) {
                        if (act != 0) runColorAction(act)
                        return@onPreviewKeyEvent act != 0
                    }
                }
                // Remapeo de botones cuando la accesibilidad no esta activa (solo dentro del launcher).
                if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0 &&
                    Remap.learn == null && PowerService.instance == null && screen != "settings" &&
                    !Remap.blocked(it.nativeKeyEvent.keyCode)
                ) {
                    val rv = Remap.map[it.nativeKeyEvent.keyCode]
                    if (rv != null) {
                        if (Remap.runExtra(ctx, rv)) return@onPreviewKeyEvent true
                        if (rv.startsWith("p:")) {
                            AppCache.apps.firstOrNull { a -> a.pkg == rv.substring(2) }?.let { a -> openApp(a) }
                            return@onPreviewKeyEvent true
                        }
                        val rn = rv.removePrefix("a:").toIntOrNull() ?: 0
                        if (rn in 1..9) {
                            runColorAction(rn)
                            return@onPreviewKeyEvent true
                        }
                    }
                }
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
        ) {
            Crossfade(
                targetState = selected,
                animationSpec = tween(if (eFade) 700 else 1),
                label = "wall"
            ) { f -> WallpaperLayer(f, wallPaused) }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x22000000), Color.Transparent, Color(0x99000000)))
                )
            )
            if (eDim > 0) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = eDim / 100f)))
            }
        }
        if (screen == "ambient" && cfg.ex("ambBlack") == "1") {
            BlackScreen { screen = "home" }
        } else if (screen == "ambient") {
            AmbientScreen(cfg, m) { screen = "home" }
        } else if (screen == "black") {
            BlackScreen { screen = "home" }
        } else {
            CompositionLocalProvider(
                LocalDock provides style,
                LocalDensity provides Density(
                    baseDensity.density * uiScale,
                    baseDensity.fontScale * (if (cfg.bigText) 1.25f else 1f)
                ),
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
                LocalBadges provides badges,
                LocalThickFocus provides cfg.thickFocus,
                LocalHighContrast provides cfg.highContrast
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
                            transitionSpec = { androidx.compose.animation.ContentTransform(EnterTransition.None, ExitTransition.None) },
                            label = "screen",
                            contentKey = { if (it == "home" || it == "settings" || it == "edit") "homeLayer" else it }
                        ) { s ->
                            when (s) {
                                "home", "settings", "edit" -> Box(Modifier.fillMaxSize()) {
                                    // El inicio queda debajo de Configuracion: asi no se duplica al abrir o cerrar el panel.
                                    val live = s == "home" && screen == "home"
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(end = sideInset)
                                            .focusProperties { canFocus = live && !showMore && !showQuick && !upd.visible }
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
                                            onMenu = { if (live) menuApp = it },
                                            net = net,
                                            onMore = { if (live) showMore = true },
                                            moreBack = moreBack,
                                            onFocusApp = { a, f ->
                                                if (f) lastDockPkg = a.pkg
                                            },
                                            dockState = dockState,
                                            focusTick = focusTick,
                                            restorePkg = lastDockPkg,
                                            loading = !appsLoaded,
                                            previewDock = preview,
                                            onQuick = { if (live) showQuick = true },
                                            quickBack = quickBack,
                                            sidePanel = s == "settings" || s == "edit",
                                            groups = groupList,
                                            onFolder = { if (live) openFolder = it }
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
                                            onReorder = { l ->
                                                favs = l
                                                prefs.saveFavs(l)
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
                                        SettingsHost(
                                            style = realStyle,
                                            cfg = cfg,
                                            onSet = onSet,
                                            cat = settingsCat,
                                            onCat = { settingsCat = it },
                                            onRightFocus = { settingsRight = it },
                                            railBack = railBack,
                                            onUpdate = { upd.open(scope) },
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
                                    onClose = { screen = "home" },
                                    prefs = prefs
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
                                    },
                                    recentSearches = recentSearches,
                                    onSearched = { q -> recentSearches = pushSearch(prefs, q) },
                                    externalQuery = extQuery
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
                    val ma = menuApp?.let { m -> apps.firstOrNull { it.pkg == m.pkg } ?: m }
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
                            customName = prefs.names()[p] ?: "",
                            onRename = { v ->
                                prefs.setName(p, v)
                                ovTick++
                            },
                            glowIndex = glowPresetIndex(prefs, p),
                            onGlow = { gi ->
                                prefs.setGlow(p, GLOW_PRESETS.getOrNull(gi)?.second)
                                ovTick++
                            },
                            hasIcon = hasCustomIcon(ctx, p),
                            onIcon = {
                                iconPkg = p
                                try {
                                    iconPicker.launch(arrayOf("image/*"))
                                    } catch (e: Exception) {
                                    toast(ctx, "Esta TV no tiene selector de archivos.")
                                }
                            },
                            onIconReset = {
                                resetIcon(ctx, p)
                                ovTick++
                            },
                            folderNames = folderNames,
                            inFolder = folderNames.map { n -> p in (groupMap[n] ?: emptyList()) },
                            onFolder = { fi -> toggleFolder(p, fi) }
                        )
                    }
                    if (showMore) {
                        MoreMenu(
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
                            net = net,
                            media = m,
                            onWifi = {
                                showQuick = false
                                quickBack++
                                openSystem(ctx, "wifi")
                            },
                            onAmbient = {
                                showQuick = false
                                quickBack++
                                screen = "ambient"
                            },
                            onWalls = {
                                showQuick = false
                                quickBack++
                                screen = "walls"
                            },
                            onInputs = {
                                showQuick = false
                                quickBack++
                                inputs = listInputs(ctx)
                                screen = "inputs"
                            },
                            onSearch = {
                                showQuick = false
                                quickBack++
                                searchFocus = true
                                screen = "all"
                            },
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
                    if (upd.visible) {
                        UpdateDialog(
                            st = upd,
                            scope = scope,
                            onClose = {
                                upd.dismiss()
                                focusTick++
                            }
                        )
                    }
                    val pa = pinAsk
                    if (pa != null) {
                        PinPad(pa, pinErr) { pinAsk = null }
                    }
                    val of = openFolder
                    if (of != null) {
                        FolderDialog(
                            name = of,
                            apps = groupList.firstOrNull { it.first == of }?.second ?: emptyList(),
                            onLaunch = {
                                openFolder = null
                                TileBounds.last = null
                                openApp(it)
                            },
                            onMenu = { menuApp = it },
                            onClose = { openFolder = null }
                        )
                    }
                    val na = newAppAsk
                    if (na != null) {
                        NewAppDialog(
                            label = na.label,
                            onAdd = {
                                if (na.pkg !in favs) {
                                    favs = favs + na.pkg
                                    prefs.saveFavs(favs)
                                }
                                newAppAsk = null
                                toast(ctx, "Añadida al dock.")
                            },
                            onClose = { newAppAsk = null }
                        )
                    }
                    val wn = whatsNew
                    if (wn != null) {
                        WhatsNewDialog(
                            title = wn.first,
                            body = wn.second,
                            onClose = {
                                cfg.setLastVersion(verCode)
                                whatsNew = null
                                focusTick++
                            }
                        )
                    }
                }
            }
        }
    }
}
