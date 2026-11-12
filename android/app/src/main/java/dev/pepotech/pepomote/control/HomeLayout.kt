package dev.pepotech.pepomote.control

/** Bloques del inicio: las tarjetas se agrupan por lo que se quiere hacer. */
enum class HomeGroup { Consoles, Pc, Tools }

/**
 * Cada tarjeta del inicio que abre un modo. «Conectar» y «Servidor» no están
 * aquí: son la fila de conexión, siempre arriba.
 *
 * [pcOnly]: con un servidor Android al otro lado no se ofrece (allí no hay
 * puntero, Wii U ni mando de Xbox virtual).
 */
enum class HomeEntry(val id: String, val group: HomeGroup, val pcOnly: Boolean = false) {
    // El mando de Wii (puntero): el modo principal, el primero
    Pointer("pointer", HomeGroup.Consoles, pcOnly = true),
    Dolphin("dolphin", HomeGroup.Consoles),
    WiiU("wiiu", HomeGroup.Consoles, pcOnly = true),
    Switch("switch", HomeGroup.Consoles),
    RetroArch("retroarch", HomeGroup.Consoles),
    Nunchuk("nunchuk", HomeGroup.Consoles, pcOnly = true),
    Gamepad("gamepad", HomeGroup.Pc, pcOnly = true),
    Kbm("kbm", HomeGroup.Pc),
    Settings("settings", HomeGroup.Tools);

    companion object {
        fun byId(id: String?): HomeEntry? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Qué tarjetas salen en el inicio y dónde. Funciones puras (HomeLayoutTest):
 * los favoritos van primero, en el orden en que se fijaron, y dejan de salir
 * en su bloque para no verse dos veces.
 */
object HomeLayout {
    /** Como mucho, una fila larga de favoritos: más ya no es «lo que más uso». */
    const val MAX_FAVORITES = 6

    fun available(androidReceiver: Boolean): List<HomeEntry> =
        HomeEntry.entries.filter { !(androidReceiver && it.pcOnly) }

    /** Los favoritos guardados que se pueden ofrecer ahora, en su orden. */
    fun favorites(saved: List<String>, androidReceiver: Boolean): List<HomeEntry> {
        val ok = available(androidReceiver)
        return saved.mapNotNull { HomeEntry.byId(it) }.distinct().filter { it in ok && canPin(it) }
    }

    /** Las tarjetas de un bloque, sin las que ya están en favoritos. */
    fun group(group: HomeGroup, saved: List<String>, androidReceiver: Boolean): List<HomeEntry> {
        val favs = favorites(saved, androidReceiver)
        return available(androidReceiver).filter { it.group == group && it !in favs }
    }

    /** Ajustes no se fija ni se «continúa»: no es un modo. */
    fun canPin(entry: HomeEntry): Boolean = entry != HomeEntry.Settings

    /** Fija o quita; al fijar va al final, y si no cabe, sale el más antiguo. */
    fun toggle(saved: List<String>, entry: HomeEntry): List<String> {
        if (!canPin(entry)) return saved
        val clean = saved.filter { HomeEntry.byId(it) != null }.distinct()
        return if (entry.id in clean) clean - entry.id else (clean + entry.id).takeLast(MAX_FAVORITES)
    }

    /** Pone un favorito el primero (si no lo era, lo fija). */
    fun moveFirst(saved: List<String>, entry: HomeEntry): List<String> {
        if (!canPin(entry)) return saved
        val clean = saved.filter { HomeEntry.byId(it) != null }.distinct()
        return (listOf(entry.id) + (clean - entry.id)).take(MAX_FAVORITES)
    }

    /** Lo último que se abrió, si ahora se puede ofrecer. */
    fun last(savedId: String?, androidReceiver: Boolean): HomeEntry? =
        HomeEntry.byId(savedId)?.takeIf { canPin(it) && it in available(androidReceiver) }

    fun parse(csv: String?): List<String> =
        csv.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun join(ids: List<String>): String = ids.joinToString(",")
}
