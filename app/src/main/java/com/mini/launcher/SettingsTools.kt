package com.mini.launcher

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

// Herramientas de Configuracion: restablecer una categoria, exportar/importar solo el diseño,
// descripciones bajo las opciones y el asistente de primer uso.

/** Ajustes (claves de Prefs) que borra "Restablecer". Las categorias que no estan aqui no se restablecen. */
val RESET_KEYS: Map<Int, List<String>> = mapOf(
    0 to listOf(
        "pauseWall", "theme", "accentCustom", "glow", "glowSrc", "dim"
    ),
    1 to listOf(
        "scale", "spacing", "corner", "glow", "bigClock", "uniformIcons", "focusStyle", "openAnim", "bottom", "tileStyle"
    ),
    2 to listOf(
        "topMode", "topScale", "bigClock", "clock24", "showDate", "dateFmt", "showWeather", "fahrenheit", "hideBar", "hideBarSecs"
    ),
    3 to listOf(
        "uiScale", "overscan", "saver", "fluid",
        "sounds", "ambient", "burnIn", "sleepAction",
        "ambDimOn", "ambDimLvl", "ambAnim", "ambPosTime", "ambPosDate", "ambPosMusic", "ambPosIcons"
    ),
    4 to listOf("showMusic", "musicSize", "musicPos"),
    6 to listOf(
        "keyRed", "keyGreen", "keyYellow", "keyBlue"
    ),
    9 to listOf("bigText", "highContrast", "thickFocus")
)

/** Explicaciones cortas que salen bajo las opciones menos obvias (clave = nombre interno de la opcion). */
val DESCS: Map<String, String> = mapOf(
    "Perfil de diseño" to "Combina tamaño, separación y brillo en un solo paso.",
    "Rotación de fondos" to "Cambia el fondo solo, según el horario elegido.",
    "Transición suave" to "Funde un fondo con el siguiente en vez de cambiarlo de golpe.",
    "Pausar fondo animado" to "Detiene el fondo en movimiento mientras hay un panel abierto.",
    "Fondo según app enfocada" to "El fondo toma el color o el banner de la app seleccionada.",
    "Presentación de fondos" to "Pasa por todos tus fondos automáticamente.",
    "Clima en el fondo" to "Muestra efectos de lluvia, nubes o sol detrás del inicio.",
    "Tema automático" to "Cambia entre tema de día y de noche según la hora.",
    "Color de acento propio" to "Reemplaza el color del tema por el que elijas.",
    "Brillo de color" to "Intensidad del resplandor alrededor de la ficha enfocada.",
    "Color del brillo" to "De dónde sale el color del resplandor.",
    "Oscurecer fondo" to "Ayuda a leer mejor sobre fondos muy claros.",
    "Tamaño de la interfaz" to "Agranda o reduce todo el launcher.",
    "Margen de seguridad" to "Aleja los elementos de los bordes si tu TV recorta la imagen.",
    "Iconos uniformes" to "Muestra cada imagen completa sobre un fondo del color de la app, para que todas se vean igual.",
    "App de la escena" to "Se abre al activar la escena (botón de color o Panel rápido). Púlsala otra vez para volver a como estaba.",
    "Rutina diaria" to "Una vez al día, si el launcher está en el inicio, hasta 30 minutos después de la hora elegida.",
    "Perfil activo" to "Cada perfil guarda su dock, filas, tema y diseño. Un perfil nuevo empieza como copia del actual.",
    "Fluidez máxima" to "Apaga de una vez desenfoque, brillo, fondos animados y animaciones para ir más fluido.",
    "Modo ahorro" to "Desactiva efectos pesados. Útil en TV con poca memoria.",
    "Sugerir modo ahorro" to "Avisa cuando el launcher vaya lento.",
    "Protección contra quemado" to "Mueve un poco los elementos fijos para cuidar la pantalla.",
    "Fondo animado en reposo" to "Si tu fondo es GIF, WebP o video, se mueve también en reposo. Se pausa con fluidez máxima o modo ahorro.",
    "Posición de la hora" to "Elige en qué parte de la pantalla va cada elemento; «Oculto» lo quita.",
    "Modo ambiente" to "Pantalla de reposo con reloj tras un rato sin usar el control.",
    "Tarjeta de la app enfocada" to "Muestra datos de la app seleccionada bajo el dock.",
    "Elementos de arriba" to "Cuánta información se ve en la parte superior.",
    "Fila Continuar" to "Muestra lo último que usaste para retomarlo rápido.",
    "Ocultar barra superior" to "La barra se esconde sola tras unos segundos sin usar.",
    "Ubicación automática" to "Usa la ubicación de la TV para el clima en vez de la ciudad elegida.",
    "Proteger ajustes" to "Pide el PIN para abrir Configuración.",
    "Proteger apps ocultas" to "Pide el PIN para ver las apps ocultas.",
    "Perfil para niños" to "Limita el inicio a las apps que elijas.",
    "Alto contraste" to "Aumenta la diferencia entre los textos y el fondo.",
    "Foco grueso" to "Marco de selección más visible.",
    "Abrir app al encender" to "Abre una app automáticamente cuando enciendes la TV.",
    "Exportar diseño" to "Guarda solo el aspecto (tema, dock, fondo…), sin apps ni PIN.",
    "Importar diseño" to "Reemplaza el aspecto actual por el del archivo elegido.",
    "Asistente de configuración" to "Repasa fondo, tema, pantalla y permisos paso a paso."
)

// ---------- Botones y filas de cuadros ----------

