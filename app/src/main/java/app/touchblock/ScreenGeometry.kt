package app.touchblock

import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.view.Display
import android.view.Surface

/** Размер экрана в пикселях в текущей ориентации и поворот относительно естественной. */
data class ScreenGeometry(val width: Int, val height: Int, val rotation: Int) {

    private val sideways get() = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    private val naturalWidth get() = (if (sideways) height else width).toFloat()
    private val naturalHeight get() = (if (sideways) width else height).toFloat()

    /** Зона → прямоугольник в экранных координатах текущей ориентации. */
    fun toScreen(zone: Zone): RectF {
        val a = naturalToScreen(zone.left * naturalWidth, zone.top * naturalHeight)
        val b = naturalToScreen(zone.right * naturalWidth, zone.bottom * naturalHeight)
        return RectF(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
    }

    /** Прямоугольник на экране → зона в долях естественной ориентации. */
    fun toZone(rect: RectF): Zone {
        val a = screenToNatural(rect.left, rect.top)
        val b = screenToNatural(rect.right, rect.bottom)
        return Zone(
            (minOf(a.x, b.x) / naturalWidth).coerceIn(0f, 1f),
            (minOf(a.y, b.y) / naturalHeight).coerceIn(0f, 1f),
            (maxOf(a.x, b.x) / naturalWidth).coerceIn(0f, 1f),
            (maxOf(a.y, b.y) / naturalHeight).coerceIn(0f, 1f),
        )
    }

    // ROTATION_90 — телефон повёрнут против часовой стрелки, ROTATION_270 — по часовой.
    private fun naturalToScreen(x: Float, y: Float) = when (rotation) {
        Surface.ROTATION_90 -> PointF(y, naturalWidth - x)
        Surface.ROTATION_180 -> PointF(naturalWidth - x, naturalHeight - y)
        Surface.ROTATION_270 -> PointF(naturalHeight - y, x)
        else -> PointF(x, y)
    }

    private fun screenToNatural(x: Float, y: Float) = when (rotation) {
        Surface.ROTATION_90 -> PointF(naturalWidth - y, x)
        Surface.ROTATION_180 -> PointF(naturalWidth - x, naturalHeight - y)
        Surface.ROTATION_270 -> PointF(y, naturalHeight - x)
        else -> PointF(x, y)
    }

    companion object {
        fun of(display: Display): ScreenGeometry {
            val size = Point()
            // Для дисплея из DisplayManager возвращает полный логический размер экрана
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            return ScreenGeometry(size.x, size.y, display.rotation)
        }
    }
}
