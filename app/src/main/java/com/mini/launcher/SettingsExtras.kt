package com.mini.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Cuadros y filas nuevas de Configuracion (carpetas, perfil para ninos, color de acento,
// celular, estadisticas, novedades, errores, temporizador de sueno). Los textos van en strings.xml.

/** Marco comun de los cuadros nuevos (mismo estilo que los demas cuadros del launcher). */
@Composable
fun ExtraDialog(
    title: String,
    onClose: () -> Unit,
    maxWidth: Int = 520,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Sin esto el primer OK solo "entraba" al cuadro: ahora el foco cae en el primer boton solo.
        val fm = androidx.compose.ui.platform.LocalFocusManager.current
        LaunchedEffect(Unit) {
            delay(120)
            runCatching { fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }
        }
        Column(
            Modifier
                .widthIn(max = maxWidth.dp)
                .heightIn(max = 460.dp)
                .clip(shape)
                .background(Color(0xF0101624))
                .border(1.dp, Color(0x33FFFFFF), shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = Color.White, fontSize = 20.sp)
            content()
        }
    }
}

/**
 * Fila de Configuracion que abre un cuadro. El cuadro recibe la funcion para cerrarse y, al
 * cerrarlo, el foco vuelve a esta fila.
 */
@Composable
fun DialogRow(text: String, modifier: Modifier = Modifier, dialog: @Composable (() -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var back by remember { mutableIntStateOf(0) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(back) {
        if (back > 0) {
            delay(200)
            runCatching { fr.requestFocus() }
        }
    }
    ActionRow(text, modifier.focusRequester(fr)) { open = true }
    if (open) {
        dialog {
            open = false
            back++
        }
    }
}

/** Tarjeta de solo lectura con una sola parada de foco (para leer textos largos con las flechas). */
@Composable
private fun ExtraCard(text: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Text(
        text,
        color = Color(0xE6FFFFFF),
        fontSize = 12.sp,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Color(0x2BFFFFFF) else Color(0x0DFFFFFF))
            .border(1.dp, if (focused) Color(0x66FFFFFF) else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(10.dp)
    )
}

// ---------- Temporizador de sueno (30) ----------

@Composable
fun SleepRow(modifier: Modifier = Modifier) {
    val sleep = HomeBridge.sleep
    var last by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            tick++
        }
    }
    val active = sleep.active
    val left = remember(tick, active) { sleep.remainingMins() }
    val sel = if (active) last else 0
    val label = if (active) {
        stringResource(R.string.sleep_label_left, left)
    } else {
        stringResource(R.string.sleep_label)
    }
    ChoiceRow(label, SLEEP_OPTIONS, sel, modifier) { i ->
        last = i
        if (SLEEP_MINS[i] == 0) sleep.cancel() else sleep.start(SLEEP_MINS[i])
    }
}

// ---------- Estadisticas (32) ----------

@Composable
fun StatsDialog(prefs: Prefs, onClose: () -> Unit) {
    val items = remember { topWeek(prefs, AppCache.apps) }
    ExtraDialog(stringResource(R.string.stats_title), onClose) {
        UsageChart(items, Themes.current.acc)
        ActionRow(stringResource(R.string.x_close), Modifier.autoFocus()) { onClose() }
    }
}

// ---------- Conexion con el celular (36-38) ----------

@Composable
fun PhoneDialog(onClose: () -> Unit) {
    DisposableEffect(Unit) {
        HomeBridge.phoneOn = true
        onDispose { HomeBridge.phoneOn = false }
    }
    var late by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(12_000)
        late = true
    }
    val addr = HomeBridge.phoneAddress
    val code = HomeBridge.phoneCode
    ExtraDialog(stringResource(R.string.phone_title), onClose) {
        if (addr.isEmpty() || code.isEmpty()) {
            Text(
                stringResource(if (late) R.string.phone_failed else R.string.phone_connecting),
                color = Color(0xE6FFFFFF), fontSize = 15.sp
            )
        } else {
            Text(stringResource(R.string.phone_steps), color = Color(0xB3FFFFFF), fontSize = 14.sp)
            Text("http://" + addr, color = Themes.current.acc, fontSize = 24.sp)
            Text(stringResource(R.string.phone_code, code), color = Color.White, fontSize = 30.sp)
            Text(stringResource(R.string.phone_use), color = Color(0x99FFFFFF), fontSize = 12.sp)
        }
        ActionRow(stringResource(R.string.x_close), Modifier.autoFocus()) { onClose() }
    }
}

// ---------- Perfil para ninos (34) ----------

@Composable
fun KidsAppsDialog(prefs: Prefs, onClose: () -> Unit) {
    val apps = remember { AppCache.apps.sortedBy { it.label.lowercase() } }
    var sel by remember { mutableStateOf(prefs.kidsApps()) }
    fun save(s: Set<String>) {
        prefs.saveKidsApps(s)
        sel = s
        HomeBridge.kidsTick++
    }
    ExtraDialog(stringResource(R.string.kids_title), onClose, 560) {
        Text(stringResource(R.string.kids_hint), color = Color(0xB3FFFFFF), fontSize = 13.sp)
        if (apps.isEmpty()) {
            Text(stringResource(R.string.kids_none), color = Color(0xE6FFFFFF), fontSize = 14.sp)
        } else {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(apps, key = { _, a -> a.pkg }) { i, a ->
                    SwitchRow(a.label, a.pkg in sel, if (i == 0) Modifier.autoFocus() else Modifier) { on ->
                        save(if (on) sel + a.pkg else sel - a.pkg)
                    }
                }
            }
        }
        ActionRow(stringResource(R.string.kids_clear)) { save(emptySet()) }
        ActionRow(stringResource(R.string.x_close), if (apps.isEmpty()) Modifier.autoFocus() else Modifier) { onClose() }
    }
}

// ---------- Selector de una app (arranque, 18) ----------

@Composable
fun AppPickerDialog(title: String, current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val apps = remember { AppCache.apps.sortedBy { it.label.lowercase() } }
    val none = stringResource(R.string.picker_none)
    ExtraDialog(title, onClose) {
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item(key = "none") {
                ActionRow((if (current.isEmpty()) "✓ " else "") + none, Modifier.autoFocus()) { onPick("") }
            }
            items(apps, key = { it.pkg }) { a ->
                ActionRow((if (a.pkg == current) "✓ " else "") + a.label) { onPick(a.pkg) }
            }
        }
        ActionRow(stringResource(R.string.x_cancel)) { onClose() }
    }
}

// ---------- Carpetas del dock (17) ----------

@Composable
fun FoldersDialog(prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var groups by remember { mutableStateOf(prefs.groups()) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val closeFr = remember { FocusRequester() }
    val takenMsg = stringResource(R.string.folders_taken)
    val newTitle = stringResource(R.string.folders_new_title)
    val renameTitle = stringResource(R.string.folders_rename_title)
    val nameHint = stringResource(R.string.folders_name_hint)

    // Al cambiar algo o cerrar un cuadro, el foco vuelve a "Cerrar" para no perderse.
    LaunchedEffect(tick) {
        if (tick > 0) {
            delay(250)
            runCatching { closeFr.requestFocus() }
        }
    }

    fun commit(g: Map<String, List<String>>) {
        prefs.saveGroups(g)
        groups = g
        HomeBridge.groupsTick++
    }

    fun taken(name: String, except: String?): Boolean =
        groups.keys.any { it.equals(name, ignoreCase = true) && !it.equals(except ?: "", ignoreCase = true) }

    ExtraDialog(stringResource(R.string.folders_title), onClose) {
        Text(
            stringResource(R.string.folders_hint, MAX_FOLDERS),
            color = Color(0xB3FFFFFF), fontSize = 13.sp
        )
        groups.entries.forEachIndexed { i, e ->
            ActionRow(
                stringResource(R.string.folders_rename, e.key, e.value.size),
                if (i == 0) Modifier.autoFocus() else Modifier
            ) { renaming = e.key }
            ActionRow(stringResource(R.string.folders_delete, e.key)) { deleting = e.key }
        }
        if (groups.size < MAX_FOLDERS) {
            ActionRow(
                stringResource(R.string.folders_create),
                if (groups.isEmpty()) Modifier.autoFocus() else Modifier
            ) { creating = true }
        }
        ActionRow(stringResource(R.string.x_close), Modifier.focusRequester(closeFr)) { onClose() }
    }

    if (creating) {
        TextEditDialog(
            title = newTitle,
            initial = "",
            hint = nameHint,
            maxLen = 20,
            onSave = { v ->
                val nn = v.trim()
                if (nn.isEmpty()) {
                    creating = false
                    tick++
                } else if (taken(nn, null)) {
                    toast(ctx, takenMsg)
                } else {
                    val out = LinkedHashMap<String, List<String>>(groups)
                    out[nn] = emptyList()
                    commit(out)
                    creating = false
                    tick++
                }
            },
            onClose = {
                creating = false
                tick++
            }
        )
    }

    val rn = renaming
    if (rn != null) {
        TextEditDialog(
            title = renameTitle,
            initial = rn,
            hint = nameHint,
            maxLen = 20,
            onSave = { v ->
                val nn = v.trim()
                if (nn.isEmpty() || nn == rn) {
                    renaming = null
                    tick++
                } else if (taken(nn, rn)) {
                    toast(ctx, takenMsg)
                } else {
                    val out = LinkedHashMap<String, List<String>>()
                    for ((k, apps) in groups) out[if (k == rn) nn else k] = apps
                    commit(out)
                    renaming = null
                    tick++
                }
            },
            onClose = {
                renaming = null
                tick++
            }
        )
    }

    val dl = deleting
    if (dl != null) {
        ExtraDialog(
            stringResource(R.string.folders_delete_title, dl),
            onClose = {
                deleting = null
                tick++
            }
        ) {
            Text(stringResource(R.string.folders_delete_text), color = Color(0xB3FFFFFF), fontSize = 14.sp)
            ActionRow(stringResource(R.string.folders_delete_yes), Modifier.autoFocus()) {
                val out = LinkedHashMap<String, List<String>>(groups)
                out.remove(dl)
                commit(out)
                deleting = null
                tick++
            }
            ActionRow(stringResource(R.string.x_cancel)) {
                deleting = null
                tick++
            }
        }
    }
}

// ---------- Color de acento propio (21) ----------

@Composable
fun AccentPickerDialog(current: Int, onSave: (Int) -> Unit, onClear: () -> Unit, onClose: () -> Unit) {
    val start = remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(if (current != 0) current else Themes.current.acc.toArgb(), hsv)
        hsv
    }
    var hue by remember { mutableIntStateOf((start[0] / 10f).toInt().coerceIn(0, 35) * 10) }
    var sat by remember { mutableIntStateOf(((start[1] * 20f).toInt() * 5).coerceIn(30, 100)) }
    var v by remember { mutableIntStateOf(((start[2] * 20f).toInt() * 5).coerceIn(50, 100)) }
    val preview = Color.hsv(hue.toFloat(), sat / 100f, v / 100f)
    ExtraDialog(stringResource(R.string.accent_title), onClose) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(preview)
        )
        SliderRow(stringResource(R.string.accent_hue), hue, 0, 350, 10, "°", Modifier.autoFocus()) { hue = it }
        SliderRow(stringResource(R.string.accent_sat), sat, 30, 100, 5, "%") { sat = it }
        SliderRow(stringResource(R.string.accent_val), v, 50, 100, 5, "%") { v = it }
        ActionRow(stringResource(R.string.accent_use)) {
            onSave(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), sat / 100f, v / 100f)))
        }
        if (current != 0) ActionRow(stringResource(R.string.accent_clear)) { onClear() }
        ActionRow(stringResource(R.string.x_cancel)) { onClose() }
    }
}

// ---------- Novedades (35) ----------

@Composable
fun ReleaseNotesFlow(onClose: () -> Unit) {
    var res by remember { mutableStateOf<Pair<String, String>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { fetchReleaseNotes() }
        if (r == null) failed = true else res = r
    }
    val r = res
    if (r != null) {
        WhatsNewDialog(r.first, r.second, onClose)
    } else {
        ExtraDialog(stringResource(R.string.x_news), onClose) {
            Text(
                stringResource(if (failed) R.string.news_failed else R.string.news_loading),
                color = Color(0xE6FFFFFF), fontSize = 15.sp
            )
            ActionRow(stringResource(R.string.x_close), Modifier.autoFocus()) { onClose() }
        }
    }
}

// ---------- Visor de errores (42) ----------

@Composable
fun ErrorsDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf(ErrorLog.read(ctx)) }
    ExtraDialog(stringResource(R.string.errors_title), onClose, 600) {
        if (entries.isEmpty()) {
            Text(stringResource(R.string.errors_empty), color = Color(0xE6FFFFFF), fontSize = 15.sp)
        } else {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(entries) { i, e ->
                    ExtraCard(e, if (i == 0) Modifier.autoFocus() else Modifier)
                }
            }
            ActionRow(stringResource(R.string.errors_clear)) {
                ErrorLog.clear(ctx)
                entries = emptyList()
            }
        }
        ActionRow(stringResource(R.string.x_close), if (entries.isEmpty()) Modifier.autoFocus() else Modifier) {
            onClose()
        }
    }
}
