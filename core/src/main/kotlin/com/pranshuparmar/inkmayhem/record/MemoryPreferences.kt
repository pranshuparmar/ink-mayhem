package com.pranshuparmar.inkmayhem.record

import com.badlogic.gdx.Preferences

/**
 * [Preferences] held in memory: a replay starts from the recorded launch's preferences without
 * touching the desktop's own. Values may be typed or strings (desktop preferences store
 * everything as strings), and the getters accept either.
 */
class MemoryPreferences(initial: Map<String, Any> = emptyMap()) : Preferences {
    private val m = HashMap<String, Any>(initial)

    override fun putBoolean(key: String, v: Boolean) = apply { m[key] = v }
    override fun putInteger(key: String, v: Int) = apply { m[key] = v }
    override fun putLong(key: String, v: Long) = apply { m[key] = v }
    override fun putFloat(key: String, v: Float) = apply { m[key] = v }
    override fun putString(key: String, v: String) = apply { m[key] = v }
    override fun put(vals: Map<String, *>) = apply { vals.forEach { (k, v) -> if (v != null) m[k] = v } }
    override fun getBoolean(key: String) = getBoolean(key, false)
    override fun getInteger(key: String) = getInteger(key, 0)
    override fun getLong(key: String) = getLong(key, 0L)
    override fun getFloat(key: String) = getFloat(key, 0f)
    override fun getString(key: String) = getString(key, "")
    override fun getBoolean(key: String, d: Boolean) = when (val v = m[key]) {
        is Boolean -> v
        is String -> v.toBooleanStrictOrNull() ?: d
        else -> d
    }
    override fun getInteger(key: String, d: Int) = when (val v = m[key]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: d
        else -> d
    }
    override fun getLong(key: String, d: Long) = when (val v = m[key]) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: d
        else -> d
    }
    override fun getFloat(key: String, d: Float) = when (val v = m[key]) {
        is Number -> v.toFloat()
        is String -> v.toFloatOrNull() ?: d
        else -> d
    }
    override fun getString(key: String, d: String) = m[key]?.toString() ?: d
    override fun get(): Map<String, *> = m
    override fun contains(key: String) = key in m
    override fun clear() = m.clear()
    override fun remove(key: String) { m.remove(key) }
    override fun flush() = Unit

    companion object {
        /** The preferences a recording started with. */
        fun of(prefs: List<InputLog.Pref>): MemoryPreferences = MemoryPreferences(
            prefs.associate { p ->
                p.key to when (p.type) {
                    'b' -> p.value.toBoolean()
                    'i' -> p.value.toInt()
                    'l' -> p.value.toLong()
                    'f' -> p.value.toFloat()
                    else -> p.value
                }
            },
        )
    }
}
