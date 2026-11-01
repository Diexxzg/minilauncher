package com.mini.launcher

import android.app.ActivityOptions
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    private var tick by mutableIntStateOf(0)
    private var homeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addOnNewIntentListener { homeTick++ }
        setContent { HomeRoot(tick, homeTick) }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }
}

@Composable
fun HomeRoot(tick: Int, homeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(ctx) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
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
    var launching by remember { mutableStateOf<AppInfo?>(null) }
    var launchRect by remember { mutableStateOf<Rect?>(null) }
    val prog = remember { Animatable(0f) }

    val style = remember(scalePct, spacing, corner, bottom, glowLvl, sizes) {
        DockStyle(scalePct, spacing, corner, bottom, glowLvl, sizes)
    }

    LaunchedEffect(homeTick) { screen = "home" }

    LaunchedEffect(tick) {
        val loaded = withContext(Dispatchers.IO) { loadApps(ctx) }
        apps = loaded
        walls = withContext(Dispatchers.IO) { listWalls(ctx) }
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
    val selected = walls.firstOrNull { it.name == wallName } ?: walls.firstOrNull()

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

    val visible = apps.filter { it.pkg !in hidden }
    val dock = favs.mapNotNull { p -> visible.firstOrNull { it.pkg == p } }

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

    fun openApp(a: AppInfo) {
        val r = TileBounds.last
        TileBounds.last = null
        if (r == null) {
            startNow(a)
            return
        }
        launchRect = r
        launching = a
        scope.launch {
            prog.snapTo(0f)
            prog.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
            startNow(a)
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

    CompositionLocalProvider(LocalDock provides style) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B1020))) {
            WallpaperLayer(selected)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x22000000), Color.Transparent, Color(0x99000000)))
                )
            )
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
                    }
                )
                "inputs" -> InputsPanel(
                    items = inputs,
                    onPick = {
                        openInput(ctx, it)
                        screen = "home"
                    },
                    onClose = { screen = "home" }
                )
                "all" -> AllApps(visible) { openApp(it) }
                "settings" -> SettingsPanel(
                    onDock = { screen = "dock" },
                    onWalls = { screen = "walls" },
                    onEdit = { screen = "edit" },
                    onOpen = { openSystem(ctx, it) },
                    onClose = { screen = "home" }
                )
                "dock" -> DockSettings(
                    style = style,
                    onSet = onSet,
                    onReset = {
                        scalePct = 100
                        spacing = 16
                        corner = 18
                        bottom = 0
                        glowLvl = 2
                        sizes = emptyMap()
                        prefs.putInt("scale", 100)
                        prefs.putInt("spacing", 16)
                        prefs.putInt("corner", 18)
                        prefs.putInt("bottom", 0)
                        prefs.putInt("glow", 2)
                        prefs.saveSizes(emptyMap())
                    }
                )
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
