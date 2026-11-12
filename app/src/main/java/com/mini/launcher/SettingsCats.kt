package com.mini.launcher

/** Las 8 categorías de Configuración. Las filas se rellenan categoría por categoría. */
val SETTINGS_CATS: List<SCat8> by lazy {
    listOf(
        SCat8("palette", "Fondo y color", "Fondo, tema, acento y brillo", listOf("Fondo", "Color"), rowsFondo()),
        SCat8("layout", "Dock y diseño", "Tamaño, fichas y foco", listOf("Dock", "Fichas"), rowsDock()),
        SCat8("home", "Inicio", "Parte superior, reloj y clima", listOf("Parte superior", "Reloj", "Clima"), rowsInicio()),
        SCat8("bolt", "Pantalla y energía", "Tamaño, rendimiento y ambiente", listOf("Pantalla", "Rendimiento", "Modo ambiente"), rowsPantalla()),
        SCat8("music", "Música y apps", "Reproductor, apps y carpetas", listOf("Música", "Apps"), rowsMusicaApps()),
        SCat8("globe", "Sistema", "Conexiones, celular y mando", listOf("Conexiones", "Android", "Celular y mando", "Visitas"), rowsSistema()),
        SCat8("shield", "Seguridad y acceso", "PIN, permisos y accesibilidad", listOf("Seguridad", "Permisos", "Accesibilidad"), rowsSeguridad()),
        SCat8("info", "Acerca de", "Versión, equipo y copias", listOf("Versión", "Equipo", "Red", "Copias de seguridad", "Uso y diagnóstico"), rowsAcerca())
    )
}
