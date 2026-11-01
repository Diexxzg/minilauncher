package com.mini.launcher

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.SystemClock
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
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
        setContent { HomeRoot(tick, homeTick, resumeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
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
fun HomeRoot(tick: Int, homeTick: Int, resumeTick: Int) {
    val ctx = LocalContext.current
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
    var launching by remember { mutableStateOf<AppInfo?>(null) }
    var launchRect by remember { mutableStateOf<Rect?>(null) }
    val prog = remember { Animatable(0f) }

    val style = remember(scalePct, spacing, corner, bottom, glowLvl, sizes) {
        DockStyle(scalePct, spacing, corner, bottom, glowLvl, sizes)
    }

    LaunchedEffect(homeTick) { screen = "home" }

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
        if (!prefs.initDone && loaded.isNotEmpty()) {
            favs = loaded.take(6).map { it.pkg }
            prefs.saveFavs(favs)
            prefs.initDone = true
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val n = withContext(Dispatchers.IO) { readMedia(ctx) }
            if (!sameMedia(media, n)) media = n
            delay(1000)
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
        screen = when (screen) {
            "walls", "edit", "dock" -> "settings"
            else -> "home"
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

    val visible = remember(apps, hidden) { apps.filter { it.pkg !in hidden } }
    val dock = remember(visible, favs) {
        val byPkg = visible.associateBy { it.pkg }
        favs.mapNotNull { byPkg[it] }
    }

    fun startNow(a: AppInfo) {
        val opts = try {
            ActivityOptions.makeCustomAnimation(
                ctx, android.R.anim.fade_in, android.R.anim.fade_out
            ).toBundle()
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
        val r = TileBounds.last
        TileBounds.last = null
        if (r == null) {
            startNow(a)
            return
        }
        launchRect = r
        launching = a
        // La app se lanza ya; la animacion corre encima mientras arranca.
        startNow(a)
        scope.launch {
            prog.snapTo(0f)
            prog.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            delay(600)
            launching = null
            prog.snapTo(0f)
        }
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

    CompositionLocalProvider(LocalDock provides style) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1020))
                .onPreviewKeyEvent {
                    lastInput.longValue = SystemClock.elapsedRealtime()
                    false
                }
        ) {
            Crossfade(
                targetState = selected,
                animationSpec = tween(if (cfg.fade) 700 else 1),
                label = "wall"
            ) { f -> WallpaperLayer(f) }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x22000000), Color.Transparent, Color(0x99000000)))
                )
            )
            if (cfg.dim > 0) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = cfg.dim / 100f)))
            }
            when (screen) {
                "home" -> HomeScreen(
                    dock, m, art,
                    { openApp(it) },
                    { screen = "all" },
                    { screen = "settings" },
                    { openSystem(ctx, "wifi") },
                    {
                        inputs = listInputs(ctx)
                        screen = "inputs"
                    },
                    cfg,
                    recentApps,
                    { menuApp = it }
                )
                "ambient" -> AmbientScreen(cfg, m) { screen = "home" }
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
                    { menuApp = it }
                ) { openApp(it) }
                "settings" -> {
                    Box(Modifier.fillMaxSize().focusProperties { canFocus = false }) {
                        HomeScreen(dock, m, art, {}, {}, {}, {}, {}, cfg, recentApps)
                    }
                    SettingsScreen(
                        style = style,
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
                        }
                    )
                }
                "walls" -> WallPicker(
                    files = walls,
                    current = if (wallName.isEmpty()) (walls.firstOrNull()?.name ?: "") else wallName,
                    onPick = { n ->
                        wallName = n
                        prefs.wallpaper = n
                    },
                    onAdd = { picker.launch(arrayOf("image/*", "video/*")) }
                )
                "edit" -> EditApps(
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
                    }
                )
            }
            val la = launching
            val lr = launchRect
            if (la != null && lr != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val p = prog.value
                    val l = lerp(lr.left, 0f, p)
                    val t = lerp(lr.top, 0f, p)
                    val w = lerp(lr.width, size.width, p)
                    val h = lerp(lr.height, size.height, p)
                    val rad = lerp(18.dp.toPx(), 0f, p)
                    val path = Path()
                    path.addRoundRect(RoundRect(l, t, l + w, t + h, CornerRadius(rad, rad)))
                    clipPath(path) {
                        drawRect(Color(0xFF0B1020))
                        if (la.banner) {
                            drawImage(
                                la.art,
                                dstOffset = IntOffset(l.toInt(), t.toInt()),
                                dstSize = IntSize(w.toInt(), h.toInt())
                            )
                        }
                    }
                }
            }
        }
    }
}
