package com.mini.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    var wallBmp by remember { mutableStateOf<ImageBitmap?>(null) }
    var media by remember { mutableStateOf<MediaInfo?>(null) }
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

    LaunchedEffect(Unit) {
        while (true) {
            media = withContext(Dispatchers.IO) { readMedia(ctx) }
            delay(1000)
        }
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

    fun openSettings(action: String) {
        try {
            ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B1020))) {
        wallBmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x22000000), Color.Transparent, Color(0x99000000)))
            )
        )
        when (screen) {
            "home" -> HomeScreen(
                dock, media, null,
                { launch(it) },
                { screen = "all" },
                { screen = "settings" },
                { }
            )
            "all" -> AllApps(visible) { launch(it) }
            "settings" -> SettingsPanel(
                onWalls = { screen = "walls" },
                onEdit = { screen = "edit" },
                onOpen = { openSettings(it) },
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
