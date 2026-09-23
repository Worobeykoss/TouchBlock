package app.touchblock

/**
 * Зона блокировки в долях экрана (0..1) в «естественной» ориентации устройства.
 * Поэтому при повороте телефона зона остаётся на том же физическом участке стекла.
 */
data class Zone(val left: Float, val top: Float, val right: Float, val bottom: Float) {

    companion object {
        fun encode(zones: List<Zone>): String =
            zones.joinToString(";") { "${it.left},${it.top},${it.right},${it.bottom}" }

        fun decode(value: String): List<Zone> = value.split(';').mapNotNull { part ->
            val v = part.split(',').mapNotNull { it.toFloatOrNull() }
            if (v.size == 4) Zone(v[0], v[1], v[2], v[3]) else null
        }
    }
}
