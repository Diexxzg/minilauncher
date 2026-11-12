package com.mini.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val S_TEXT = Color(0xFFF1F4FA)
private val S_TEXT2 = Color(0xFFA8B1C3)
private val S_DARK = Color(0xFF0B0D11)
private val S_RAIL = Color(0xFF0B0D11)

/** Panel plano de Configuración (548×492 en la escena de 960×540): sin cristal ni desenfoque. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val acc = Themes.current.acc
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .width(548.dp)
            .height(492.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF191E26), Color(0xFF0E1116))))
            .border(1.dp, Color(0x1FFFFFFF), shape)
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
    ) {
        content()
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Brush.horizontalGradient(listOf(acc, Color.Transparent)))
        )
    }
}

/** Pantalla de categorías: 8 filas de 50 dp. Atrás lo maneja MainActivity. */
@Composable
fun SCategoryScreen(
    cats: List<SCat8>,
    startIndex: Int,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SPanel(modifier) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp)) {
            Text("Configuración", color = S_TEXT, fontSize = 20.sp)
            Text(
                "${cats.size} categorías · Atrás sale con una sola pulsación",
                color = S_TEXT2, fontSize = 11.sp, maxLines = 1
            )
            Spacer(Modifier.height(12.dp))
            cats.forEachIndexed { i, c ->
                SCatRow(
                    c,
                    if (i == startIndex.coerceIn(0, cats.lastIndex)) Modifier.autoFocusOn(Unit) else Modifier
                ) { onOpen(i) }
                if (i < cats.lastIndex) Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SCatRow(c: SCat8, modifier: Modifier, onClick: () -> Unit) {
    val acc = Themes.current.acc
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(
                if (focused) Brush.horizontalGradient(listOf(acc.copy(alpha = 0.33f), acc.copy(alpha = 0.18f)))
                else Brush.horizontalGradient(listOf(Color(0x0AFFFFFF), Color(0x0AFFFFFF)))
            )
            .border(if (focused) 2.dp else 0.dp, if (focused) acc else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (focused) acc else acc.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(c.icon, if (focused) S_DARK else acc, 18)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, color = S_TEXT, fontSize = 14.sp, maxLines = 1)
            Text(c.desc, color = S_TEXT2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = if (focused) acc else S_TEXT2, fontSize = 18.sp)
    }
}

/** Lista de una categoría: riel de iconos a la izquierda, título, filas (LazyColumn) y pie con ayudas. */
@Composable
fun SListScreen(
    cats: List<SCat8>,
    cat: Int,
    ctx: SCtx,
    onCat: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val acc = Themes.current.acc
    val cur = cat.coerceIn(0, cats.lastIndex)
    val c = cats[cur]
    var railTouched by remember { mutableStateOf(false) }
    val railFr = remember { List(cats.size) { FocusRequester() } }
    SPanel(modifier) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(68.dp).fillMaxHeight().background(S_RAIL).padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                cats.forEachIndexed { i, k ->
                    SRailItem(k.icon, i == cur, Modifier.focusRequester(railFr[i])) { railTouched = true; onCat(i) }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp, end = 12.dp, top = 18.dp, bottom = 8.dp)) {
                Text(c.name, color = S_TEXT, fontSize = 18.sp, maxLines = 1)
                Text(c.desc, color = S_TEXT2, fontSize = 11.sp, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                key(cur) {
                    val flat = c.flat(ctx)
                    val ls = rememberLazyListState()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = ls,
                            modifier = Modifier.fillMaxSize().focusProperties { left = railFr[cur] }.drawBehind {
                                val info = ls.layoutInfo
                                val total = info.totalItemsCount
                                val vis = info.visibleItemsInfo.size
                                if (total > vis && vis > 0) {
                                    drawRoundRect(
                                        Color(0x33FFFFFF),
                                        Offset(size.width - 3.dp.toPx(), size.height * ls.firstVisibleItemIndex / total),
                                        Size(3.dp.toPx(), size.height * vis / total),
                                        CornerRadius(2.dp.toPx())
                                    )
                                }
                            },
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(flat, key = { if (it is SRow) it.id else it as String }) { it ->
                                if (it is SRow) {
                                    val first = flat.firstOrNull { x -> x is SRow && x.focusable } === it
                                    CompositionLocalProvider(LocalRowIcon provides it.icon) {
                                        it.content(ctx, if (first && !railTouched) Modifier.autoFocusOn(Unit) else Modifier)
                                    }
                                } else {
                                    SSecLabel(it as String)
                                }
                            }
                        }
                        if (ls.canScrollForward) {
                            Box(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0E1116))))
                            )
                        }
                    }
                }
                Text(
                    "↑↓ mover · OK elegir · Atrás volver",
                    color = S_TEXT2, fontSize = 10.sp, maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SRailItem(icon: String, active: Boolean, modifier: Modifier, onFocus: () -> Unit) {
    val acc = Themes.current.acc
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .size(44.dp)
            .clip(shape)
            .background(if (active) acc else Color.Transparent)
            .then(if (focused && !active) Modifier.border(2.dp, acc, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(onClick = onFocus),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(icon, if (active) S_DARK else S_TEXT2, 22)
    }
}

@Composable
private fun SSecLabel(text: String) {
    val acc = Themes.current.acc
    Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(acc))
        Spacer(Modifier.width(7.dp))
        Text(text.uppercase(), color = Color(0xFF9BA5B8), fontSize = 10.sp, maxLines = 1)
    }
}
