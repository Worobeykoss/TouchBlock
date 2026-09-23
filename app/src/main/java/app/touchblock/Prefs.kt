package app.touchblock

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt

class Prefs(context: Context) {

    val storage: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var zones: List<Zone>
        get() = Zone.decode(storage.getString(KEY_ZONES, null).orEmpty())
        set(value) = storage.edit().putString(KEY_ZONES, Zone.encode(value)).apply()

    /** Пользователь включил блокировку. Работает она, только когда включена и служба спецвозможностей. */
    var enabled: Boolean
        get() = storage.getBoolean(KEY_ENABLED, false)
        set(value) = storage.edit().putBoolean(KEY_ENABLED, value).apply()

    var paused: Boolean
        get() = storage.getBoolean(KEY_PAUSED, false)
        set(value) = storage.edit().putBoolean(KEY_PAUSED, value).apply()

    var showZones: Boolean
        get() = storage.getBoolean(KEY_SHOW_ZONES, true)
        set(value) = storage.edit().putBoolean(KEY_SHOW_ZONES, value).apply()

    private companion object {
        const val KEY_ZONES = "zones"
        const val KEY_ENABLED = "enabled"
        const val KEY_PAUSED = "paused"
        const val KEY_SHOW_ZONES = "show_zones"
    }
}

fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
