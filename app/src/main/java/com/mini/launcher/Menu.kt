package com.mini.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

fun toast(ctx: Context, s: String) {
    Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
}

fun findActivity(c: Context): Activity? {
    var x: Context = c
    while (x is ContextWrapper) {
        if (x is Activity) return x
        x = x.baseContext
    }
    return null
}

fun tryIntent(ctx: Context, i: Intent): Boolean {
    return try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }
}

fun openAppInfo(ctx: Context, pkg: String) {
    val ok = tryIntent(
        ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
    )
    if (!ok) toast(ctx, "Esta TV no permite abrir la información de la app.")
}

fun uninstallApp(ctx: Context, pkg: String) {
    val ok = tryIntent(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    if (!ok) toast(ctx, "Esta TV no permite desinstalar desde aquí.")
}

/** Pide ser el launcher predeterminado con el selector oficial de Android (sin ADB). */
fun requestHome(ctx: Context, launch: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT >= 29) {
        val rm = ctx.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            if (rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                toast(ctx, "Este launcher ya es el inicio predeterminado.")
                return
            }
            try {
                launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            } catch (e: Exception) {
            }
        }
    }
    if (!tryStart(ctx, "android.settings.HOME_SETTINGS")) {
        toast(
            ctx,
            "Tu TV no permite este método. Cámbialo en Ajustes > Apps > Apps predeterminadas."
        )
    }
}

@Composable
fun AppMenu(
    app: AppInfo,
    isFav: Boolean,
    isHidden: Boolean,
    size: Int,
    onDismiss: () -> Unit,
    onFav: () -> Unit,
    onMoveL: () -> Unit,
    onMoveR: () -> Unit,
    onHide: () -> Unit,
    onSize: (Int) -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    rowNames: List<String?> = emptyList(),
    inRow: List<Boolean> = emptyList(),
    onRow: (Int) -> Unit = {},
    // Mejora 16: nombre propio, color de brillo e icono propio (valores por defecto: sin cambios).
    customName: String = "",
    onRename: (String) -> Unit = {},
    glowIndex: Int = 0,
    onGlow: (Int) -> Unit = {},
    hasIcon: Boolean = false,
    onIcon: (() -> Unit)? = null,
    onIconReset: () -> Unit = {},
    // Mejora 17: carpetas del dock (nombres, si la app esta en cada una, y alternar).
    folderNames: List<String> = emptyList(),
    inFolder: List<Boolean> = emptyList(),
    onFolder: (Int) -> Unit = {}
) {
    val shape = RoundedCornerShape(26.dp)
    var renaming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(customName.ifEmpty { app.label }) }
    Dialog(
        onDismissRequest = { if (renaming) renaming = false else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .width(520.dp)
                .heightIn(max = 480.dp)
                .clip(shape)
                .background(Color(0xF0101624))
                .border(1.dp, Color(0x33FFFFFF), shape)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(app.art, null, Modifier.size(44.dp))
                Text(
                    app.label, color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (renaming) {
                // Editor de nombre: con el control escribes con el teclado de la TV; Atrás vuelve al menú.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 40) draft = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            onRename(draft)
                            renaming = false
                        }),
                        modifier = Modifier.fillMaxWidth().autoFocus()
                    )
                }
                ActionRow("Guardar nombre") {
                    onRename(draft)
                    renaming = false
                }
                ActionRow("Volver al nombre original") {
                    onRename("")
                    draft = app.label
                    renaming = false
                }
                ActionRow("Cancelar") { renaming = false }
            } else {
            ActionRow(
                if (isFav) "Quitar de favoritas" else "Agregar a favoritas",
                Modifier.autoFocus()
            ) { onFav() }
            if (isFav) {
                ActionRow("Mover a la izquierda") { onMoveL() }
                ActionRow("Mover a la derecha") { onMoveR() }
            }
            for (i in rowNames.indices) {
                val rn = rowNames[i]
                if (rn != null) {
                    val on = inRow.getOrElse(i) { false }
                    SwitchRow("En la fila " + rn, on) { v -> if (v != on) onRow(i) }
                }
            }
            for (i in folderNames.indices) {
                val on = inFolder.getOrElse(i) { false }
                SwitchRow("En la carpeta " + folderNames[i], on) { v -> if (v != on) onFolder(i) }
            }
            SliderRow("Tamaño", size, 50, 200, 10, "%") { onSize(it) }
            ActionRow(if (isHidden) "Mostrar en el inicio" else "Ocultar") { onHide() }
            ActionRow("Cambiar nombre") {
                draft = customName.ifEmpty { app.label }
                renaming = true
            }
            ChoiceRow("Color de brillo", GLOW_PRESETS.map { it.first }, glowIndex) { onGlow(it) }
            if (onIcon != null) {
                ActionRow(if (hasIcon) "Cambiar icono propio" else "Elegir icono propio") { onIcon() }
                if (hasIcon) ActionRow("Quitar icono propio") { onIconReset() }
            }
            ActionRow("Información de la app") { onInfo() }
            ActionRow("Desinstalar") { onUninstall() }
            }
        }
    }
}
