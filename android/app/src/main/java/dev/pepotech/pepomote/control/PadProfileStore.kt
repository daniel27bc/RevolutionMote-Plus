package dev.pepotech.pepomote.control

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Los perfiles de diseño guardados y cuál está activo. Sin perfil activo se
 * usa el diseño original del mando (el de la app de siempre).
 */
object PadProfileStore {
    private const val PREFS = "pad_profiles"
    private const val KEY_LIST = "profiles"
    private const val KEY_ACTIVE = "active"

    private val _profiles = MutableStateFlow<List<PadProfile>>(emptyList())
    val profiles: StateFlow<List<PadProfile>> = _profiles.asStateFlow()

    private val _active = MutableStateFlow<String?>(null)
    /** Nombre del perfil activo; null = diseño original. */
    val active: StateFlow<String?> = _active.asStateFlow()

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _profiles.value = PadProfileJson.listFromString(sp.getString(KEY_LIST, null))
        _active.value = sp.getString(KEY_ACTIVE, null)?.takeIf { name -> _profiles.value.any { it.name == name } }
        loaded = true
    }

    fun activeProfile(): PadProfile? = _active.value?.let { name -> _profiles.value.firstOrNull { it.name == name } }

    /** Guarda (o sustituye por nombre) y lo deja activo. [oldName]: si se ha renombrado. */
    fun save(context: Context, profile: PadProfile, oldName: String? = null) {
        val list = _profiles.value.filter { it.name != profile.name && it.name != oldName } + profile
        _profiles.value = list
        _active.value = profile.name
        persist(context)
    }

    fun activate(context: Context, name: String?) {
        _active.value = name?.takeIf { n -> _profiles.value.any { it.name == n } }
        persist(context)
    }

    fun delete(context: Context, name: String) {
        _profiles.value = _profiles.value.filter { it.name != name }
        if (_active.value == name) _active.value = null
        persist(context)
    }

    fun deleteAll(context: Context) {
        _profiles.value = emptyList()
        _active.value = null
        persist(context)
    }

    /**
     * Copia de seguridad: los perfiles en un JSON para guardar en un archivo
     * o compartir (sobrevive a desinstalar la app). [only]: solo ese perfil.
     */
    fun exportJson(only: PadProfile? = null): String {
        val arr = org.json.JSONArray()
        for (p in only?.let { listOf(it) } ?: _profiles.value) arr.put(PadProfileJson.toJson(p))
        return org.json.JSONObject()
            .put("app", "RevolutionMote+")
            .put("type", "pad-profiles")
            .put("version", 1)
            .put("profiles", arr)
            .toString(2)
    }

    /**
     * Importa perfiles de [text] (lo que sale de [exportJson], un perfil
     * suelto o una lista). Un nombre que ya existe se guarda con otro nombre:
     * nunca pisa nada. Devuelve cuántos se han importado (0 = no era válido).
     */
    fun importJson(context: Context, text: String): Int {
        val parsed = try {
            val t = text.trim()
            val arr = when {
                t.startsWith("[") -> org.json.JSONArray(t)
                else -> {
                    val o = org.json.JSONObject(t)
                    o.optJSONArray("profiles") ?: org.json.JSONArray().put(o)
                }
            }
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { PadProfileJson.fromJson(it) } }
        } catch (_: Exception) {
            emptyList()
        }
        if (parsed.isEmpty()) return 0
        val names = _profiles.value.map { it.name }.toMutableList()
        val added = parsed.map { p ->
            val name = PadProfiles.uniqueName(p.name, names)
            names += name
            p.copy(name = name)
        }
        _profiles.value = _profiles.value + added
        persist(context)
        return added.size
    }

    private fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIST, PadProfileJson.listToString(_profiles.value))
            .putString(KEY_ACTIVE, _active.value)
            .apply()
    }
}
