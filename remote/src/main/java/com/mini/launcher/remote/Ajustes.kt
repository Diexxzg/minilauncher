package com.mini.launcher.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun Label(text: String) {
    Text(text, color = TXT, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun SwitchLine(title: String, on: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TXT, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Box(
            Modifier.width(58.dp).height(32.dp).clip(CircleShape).background(if (on) Color(0xFF3B2F78) else Color(0xFF2A2833)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.padding(4.dp).size(24.dp).clip(CircleShape).background(if (on) Color(0xFFD2C4FF) else Color(0xFF55525F)))
        }
    }
}

@Composable
internal fun AjustesScreen(cn: Conn) {
    val scope = rememberCoroutineScope()
    var schema by remember { mutableStateOf<Schema?>(null) }
    val vals = remember { mutableStateMapOf<Int, Int>() }
    var about by remember { mutableStateOf<List<Pair<String, List<Pair<String, String>>>>?>(null) }
    var err by remember { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf(0) }
    var open by remember { mutableStateOf(-1) }

    LaunchedEffect(cn.host, cn.token) {
        val r = withContext(Dispatchers.IO) { runCatching { cn.schema() } }
        val s = r.getOrNull()
        if (s != null) {
            schema = s
            vals.clear()
            vals.putAll(s.vals)
            err = ""
        } else err = if (r.isFailure) "Sin respuesta de la TV." else "La TV no dio sus ajustes."
    }

    fun note(r: Result<String?>) {
        err = r.fold({ it ?: "" }, { "Sin respuesta de la TV." })
    }

    fun setv(id: Int, v: Int) {
        vals[id] = v
        scope.launch { note(withContext(Dispatchers.IO) { runCatching { cn.setv(id, v) } }) }
    }

    fun sys(n: String) {
        scope.launch { note(withContext(Dispatchers.IO) { runCatching { cn.sys(n) } }) }
    }

    val s = schema
    val aboutIdx = (s?.cats?.size ?: 11) - 1

    LaunchedEffect(cat, s) {
        if (s != null && cat == aboutIdx && about == null) {
            about = withContext(Dispatchers.IO) { runCatching { cn.about() }.getOrNull() }
        }
    }

    Title("Ajustes de la TV", "Cambia el launcher desde aquí.")
    GlassField(q, { q = it }, "Buscar ajuste", glyph = "🔍")
    if (s != null) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            s.cats.forEachIndexed { i, n -> Chip(n, cat == i && q.isBlank()) { cat = i; q = ""; open = -1 } }
        }
    }
    if (err.isNotEmpty()) Text(err, color = RED, fontSize = 14.sp)
    if (s == null) return

    @Composable
    fun RowView(r: SRow) {
        when (r.kind) {
            "switch" -> SwitchLine(r.label, (vals[r.id] ?: 0) == 1) { setv(r.id, if ((vals[r.id] ?: 0) == 1) 0 else 1) }
            "choice" -> {
                val cur = vals[r.id] ?: 0
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().clickable { open = if (open == r.id) -1 else r.id }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.label, color = TXT, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(end = 10.dp))
                        Text(r.opts.getOrElse(cur) { "—" }, color = ACCL, fontSize = 14.sp, maxLines = 1)
                        Text(if (open == r.id) "  ▴" else "  ▾", color = MUT, fontSize = 14.sp)
                    }
                    if (open == r.id) {
                        r.opts.forEachIndexed { i, o ->
                            Row(Modifier.fillMaxWidth().clickable { setv(r.id, i); open = -1 }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(if (i == cur) P1 else EDGE))
                                Spacer(Modifier.width(12.dp))
                                Text(o, color = if (i == cur) TXT else MUT, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            "slider" -> {
                val cur = (vals[r.id] ?: r.mn).coerceIn(r.mn, r.mx)
                val span = (r.mx - r.mn).coerceAtLeast(1)
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(r.label, color = TXT, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(cur.toString() + r.unit, color = ACCL, fontSize = 14.sp)
                    }
                    GlassSlider(
                        (cur - r.mn).toFloat() / span, Modifier.fillMaxWidth(),
                        onChange = { f -> vals[r.id] = r.mn + Math.round(f * span / r.step) * r.step },
                        onDone = { f -> setv(r.id, (r.mn + Math.round(f * span / r.step) * r.step).coerceIn(r.mn, r.mx)) }
                    )
                }
            }
            "theme" -> {
                val cur = vals[r.id] ?: -1
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(r.label, color = TXT, fontSize = 15.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        s.themes.forEachIndexed { i, c ->
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                    .border(if (cur == i) 2.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { setv(r.id, i) }
                            )
                        }
                    }
                }
            }
            "action" -> Row(Modifier.fillMaxWidth().clickable { sys(r.act) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, color = TXT, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("›", color = TXT, fontSize = 22.sp)
            }
            else -> Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, color = MUT, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("Solo en la TV", color = MUT, fontSize = 12.sp)
            }
        }
    }

    if (q.isNotBlank()) {
        val hits = s.rows.filter { it.label.contains(q.trim(), ignoreCase = true) || it.sec.contains(q.trim(), ignoreCase = true) }
        if (hits.isEmpty()) Text("Nada coincide.", color = MUT, fontSize = 14.sp)
        hits.groupBy { it.cat }.forEach { (c, list) ->
            Label(s.cats.getOrElse(c) { "" })
            Glass(Modifier.fillMaxWidth(), radius = 26.dp) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) { list.forEach { RowView(it) } }
            }
        }
    } else if (cat == aboutIdx) {
        val a = about
        if (a == null) Text("Leyendo datos de la TV…", color = MUT, fontSize = 14.sp)
        a?.forEach { (title, lines) ->
            Label(title)
            Glass(Modifier.fillMaxWidth(), radius = 26.dp) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    lines.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(k, color = MUT, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(end = 10.dp))
                            Text(v, color = TXT, fontSize = 14.sp, modifier = Modifier.weight(1.2f))
                        }
                    }
                }
            }
        }
    } else {
        val list = s.rows.filter { it.cat == cat }
        if (list.isEmpty()) Text("Esta sección no tiene ajustes.", color = MUT, fontSize = 14.sp)
        list.groupBy { it.sec }.forEach { (sec, rs) ->
            if (sec.isNotEmpty()) Label(sec)
            Glass(Modifier.fillMaxWidth(), radius = 26.dp) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) { rs.forEach { RowView(it) } }
            }
        }
    }
    Text("Lo que dice «Solo en la TV» abre diálogos o permisos que se hacen con el mando.", color = MUT, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
}
