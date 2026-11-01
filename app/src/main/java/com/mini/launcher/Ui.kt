package com.mini.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val f = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Text(
        f.format(now), color = Color(0xEBFFFFFF), fontSize = 22.sp,
        fontWeight = FontWeight.Medium
    )
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
fun SmallBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassBox(onClick, modifier.height(40.dp)) {
        Text(text, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp))
    }
}
