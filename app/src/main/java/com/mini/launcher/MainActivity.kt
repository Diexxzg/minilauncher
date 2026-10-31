package com.mini.launcher

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppInfo(
    val pkg: String,
    val label: String,
    val art: ImageBitmap,
    val banner: Boolean,
    val intent: Intent
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
    fun favs(): List<String> =
        (sp.getString("favs", "") ?: "").split(",").filter { it.isNotEmpty() }
    fun saveFavs(l: List<String>) {
        sp.edit().putString("favs", l.joinToString(",")).apply()
    }
    fun hidden(): Set<String> = (sp.getStringSet("hidden", emptySet()) ?: emptySet()).toSet()
    fun saveHidden(s: Set<String>) {
        sp.edit().putStringSet("hidden", s).apply()
    }
    var wallpaper: String
        get() = sp.getString("wall", "") ?: ""
        set(v) { sp.edit().putString("wall", v).apply() }
    var initDone: Boolean
        get() = sp.getBoolean("init", false)
        set(v) { sp.edit().putBoolean("init", v).apply() }
}

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val map = LinkedHashMap<String, AppInfo>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
        for (ri in pm.queryIntentActivities(q, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || map.containsKey(pkg)) continue
            val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
                ?: pm.getLaunchIntentForPackage(pkg)
                ?: continue
            val bn = ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm)
            val isBanner = bn != null
            val bmp = if (bn != null) bn.toBitmap(288, 168) else ri.loadIcon(pm).toBitmap(128, 128)
            map[pkg] = AppInfo(pkg, ri.loadLabel(pm).toString(), bmp.asImageBitmap(), isBanner, launch)
        }
    }
    return map.values.sortedBy { it.label.lowercase() }
}

fun wallDir(ctx: Context): File {
    val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
    return File(base, "wallpapers").also { it.mkdirs() }
}

fun listWalls(ctx: Context): List<File> =
    (wallDir(ctx).listFiles() ?: emptyArray())
        .filter { it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }
        .sortedBy { it.name }

fun loadBitmap(f: File, target: Int): ImageBitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1
    while (o.outWidth / (s * 2) >= target) s *= 2
    val o2 = BitmapFactory.Options().apply { inSampleSize = s }
    return BitmapFactory.decodeFile(f.path, o2)?.asImageBitmap()
}

fun importWall(ctx: Context, uri: Uri): String? {
    val name = "fondo_" + System.currentTimeMillis() + ".jpg"
    return try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            File(wallDir(ctx), name).outputStream().use { out -> input.copyTo(out) }
        }
        name
    } catch (e: Exception) {
        null
    }
}

fun move(list: List<String>, p: String, d: Int): List<String> {
    val i = list.indexOf(p)
    val j = i + d
    if (i < 0 || j < 0 || j >= list.size) return list
    val m = list.toMutableList()
    m.removeAt(i)
    m.add(j, p)
    return m
}

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
fun Modifier.autoFocus(): Modifier {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { fr.requestFocus() }
    }
    return this.focusRequester(fr)
}

@Composable
fun GlassBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) 1.05f else 1f, tween(200), label = "scale")
    Box(
        modifier = modifier
            .scale(s)
            .clip(shape)
            .background(if (focused) Color(0x55FFFFFF) else Color(0x22FFFFFF))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color(0xDDFFFFFF) else Color(0x33FFFFFF),
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun AppTile(app: AppInfo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassBox(onClick, modifier.size(144.dp, 84.dp)) {
        if (app.banner) {
            Image(app.art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(app.art, null, Modifier.size(40.dp))
                Text(
                    app.label, color = Color.White, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Text(
        fmt.format(now), color = Color(0xEBFFFFFF), fontSize = 22.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun HomeScreen(
    dock: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onAll: () -> Unit,
    onSettings: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopEnd).padding(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Clock()
            Spacer(Modifier.width(16.dp))
            GlassBox(onClick = onSettings, modifier = Modifier.size(44.dp), shape = CircleShape) {
                Text("⚙", color = Color.White, fontSize = 20.sp)
            }
        }
        val dockShape = RoundedCornerShape(28.dp)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(18.dp)
                .fillMaxWidth()
                .clip(dockShape)
                .background(Color(0x33FFFFFF))
                .border(1.dp, Color(0x44FFFFFF), dockShape)
                .padding(16.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
            ) {
                itemsIndexed(dock) { i, app ->
                    AppTile(app, if (i == 0) Modifier.autoFocus() else Modifier) { onLaunch(app) }
                }
                item {
                    GlassBox(
                        onClick = onAll,
                        modifier = (if (dock.isEmpty()) Modifier.autoFocus() else Modifier)
                            .size(144.dp, 84.dp)
                    ) {
                        Text("Todas las apps", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AllApps(apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(48.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            gridItems(apps) { app ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppTile(app) { onLaunch(app) }
                    Text(
                        app.label, color = Color.White, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PanelItem(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassBox(onClick, modifier.fillMaxWidth()) {
        Text(
            text, color = Color.White, fontSize = 16.sp,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        )
    }
}

@Composable
fun SettingsPanel(
    onWalls: () -> Unit,
    onEdit: () -> Unit,
    onAndroid: () -> Unit,
    onClose: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .padding(32.dp)
                .width(380.dp)
                .clip(shape)
                .background(Color(0x55FFFFFF))
                .border(1.dp, Color(0x55FFFFFF), shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Configuración", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            PanelItem("Cambiar fondo de pantalla", Modifier.autoFocus()) { onWalls() }
            PanelItem("Editar apps (favoritas, orden, ocultar)") { onEdit() }
            PanelItem("Abrir ajustes de Android") { onAndroid() }
            PanelItem("Cerrar") { onClose() }
        }
    }
}

@Composable
fun WallThumb(f: File, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, f) {
        value = withContext(Dispatchers.IO) { loadBitmap(f, 480) }
    }
    GlassBox(onClick, modifier.size(200.dp, 112.dp)) {
        thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (selected) {
            Text(
                "✓", color = Color.White, fontSize = 22.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            )
        }
    }
}

@Composable
fun WallPicker(files: List<File>, current: String, onPick: (String) -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).padding(48.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Fondos de pantalla", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)
            ) {
                item {
                    GlassBox(onClick = onAdd, modifier = Modifier.autoFocus().size(200.dp, 112.dp)) {
                        Text("+ Agregar fondo", color = Color.White, fontSize = 16.sp)
                    }
                }
                items(files) { f ->
                    WallThumb(f, f.name == current) { onPick(f.name) }
                }
            }
            Text(
                "Elige uno con OK. Para ver la lista completa mueve el control a la derecha.",
                color = Color(0xCCFFFFFF), fontSize = 13.sp
            )
        }
    }
}

@Composable
fun SmallBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassBox(onClick, modifier.height(40.dp)) {
        Text(text, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp))
    }
}

@Composable
fun EditApps(
    apps: List<AppInfo>,
    favs: List<String>,
    hidden: Set<String>,
    onFav: (String) -> Unit,
    onUp: (String) -> Unit,
    onDown: (String) -> Unit,
    onHide: (String) -> Unit
) {
    val ordered = favs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } } +
        apps.filter { it.pkg !in favs }
    Column(
        Modifier.fillMaxSize().background(Color(0xAA000000)).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Editar apps", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Text(
            "Las favoritas salen primero y en este orden en la barra de abajo.",
            color = Color(0xCCFFFFFF), fontSize = 13.sp
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
        ) {
            itemsIndexed(ordered) { i, app ->
                val isFav = app.pkg in favs
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(app.art, null, Modifier.size(36.dp))
                    Text(
                        app.label + if (app.pkg in hidden) "  (oculta)" else "",
                        color = Color.White, fontSize = 16.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    SmallBtn(
                        if (isFav) "★ Favorita" else "☆ Favorita",
                        if (i == 0) Modifier.autoFocus() else Modifier
                    ) { onFav(app.pkg) }
                    if (isFav) {
                        SmallBtn("▲") { onUp(app.pkg) }
                        SmallBtn("▼") { onDown(app.pkg) }
                    }
                    SmallBtn(if (app.pkg in hidden) "Mostrar" else "Ocultar") { onHide(app.pkg) }
                }
            }
        }
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
    var wallBmp by remember { mutableStateOf<ImageBitmap?>(null) }
    var screen by remember { mutableStateOf("home") }

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

    LaunchedEffect(wallName, walls) {
        val f = walls.firstOrNull { it.name == wallName } ?: walls.firstOrNull()
        wallBmp = if (f == null) null else withContext(Dispatchers.IO) { loadBitmap(f, 3840) }
    }

    BackHandler {
        screen = if (screen == "walls" || screen == "edit") "settings" else "home"
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

    fun launch(a: AppInfo) {
        try {
            ctx.startActivity(a.intent)
        } catch (e: Exception) {
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B1020))) {
        wallBmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x22000000), Color.Transparent, Color(0x77000000)))
            )
        )
        when (screen) {
            "home" -> HomeScreen(dock, { launch(it) }, { screen = "all" }, { screen = "settings" })
            "all" -> AllApps(visible) { launch(it) }
            "settings" -> SettingsPanel(
                onWalls = { screen = "walls" },
                onEdit = { screen = "edit" },
                onAndroid = {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                    }
                },
                onClose = { screen = "home" }
            )
            "walls" -> WallPicker(
                files = walls,
                current = if (wallName.isEmpty()) (walls.firstOrNull()?.name ?: "") else wallName,
                onPick = { n ->
                    wallName = n
                    prefs.wallpaper = n
                },
                onAdd = { picker.launch(arrayOf("image/*")) }
            )
            "edit" -> EditApps(
                apps = apps,
                favs = favs,
                hidden = hidden,
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
                }
            )
        }
    }
}
