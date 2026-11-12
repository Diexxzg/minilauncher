package com.mini.launcher

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Configuración nueva: cat = -1 muestra la lista de categorías; 0..7 abre una categoría.
 * Atrás lo maneja MainActivity (goBack): railBack++ vuelve a la lista; desde la lista sale.
 * Parámetros marcados "sin uso" se quitan en la limpieza.
 */
@Composable
fun SettingsHost(
    style: DockStyle,
    cfg: Cfg,
    cat: Int,
    onCat: (Int) -> Unit,
    onRightFocus: (Boolean) -> Unit = {},
    railBack: Int = 0,
    onSet: (String, Int) -> Unit,
    onWalls: () -> Unit,
    onEdit: () -> Unit,
    onInputs: () -> Unit,
    onOpen: (String) -> Unit,
    onSetHome: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onNetPerm: () -> Unit,
    onPin: (String) -> Unit,
    onBackupSave: () -> Unit = {},
    onBackupShare: () -> Unit = {},
    onRestorePick: () -> Unit = {},
    onUpdate: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var permTick by remember { mutableIntStateOf(0) }
    var lastCat by remember { mutableIntStateOf(0) }
    val calPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    LaunchedEffect(cat) {
        onRightFocus(cat >= 0)
        if (cat >= 0) lastCat = cat
    }
    LaunchedEffect(railBack) {
        if (railBack > 0 && cat >= 0) onCat(-1)
    }
    val act: (String) -> Unit = { a ->
        when {
            a == "walls" -> onWalls()
            a == "edit" -> onEdit()
            a == "inputs" -> onInputs()
            a == "sethome" -> onSetHome()
            a == "netperm" -> onNetPerm()
            a == "update" -> onUpdate()
            a == "backup" -> onBackup()
            a == "restore" -> onRestore()
            a == "backupSave" -> onBackupSave()
            a == "backupShare" -> onBackupShare()
            a == "restorePick" -> onRestorePick()
            a.startsWith("open:") -> onOpen(a.removePrefix("open:"))
            a.startsWith("pin:") -> onPin(a.removePrefix("pin:"))
            a == "perm:cal" -> runCatching { calPerm.launch(Manifest.permission.READ_CALENDAR) }
            a == "perm:loc" -> runCatching { locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
        }
    }
    val sctx = SCtx(cfg, style, onSet, act, ctx, prefs, permTick)
    val dens = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Escena de diseño de 960×540: se escala a la pantalla real.
        val scale = minOf(maxWidth.value / 960f, maxHeight.value / 540f)
        CompositionLocalProvider(LocalDensity provides Density(dens.density * scale, dens.fontScale)) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)) {
                    if (cat < 0) SCategoryScreen(SETTINGS_CATS, lastCat, { onCat(it) })
                    else SListScreen(SETTINGS_CATS, cat, sctx, onCat)
                }
            }
        }
    }
}
