package app.touchblock

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Полноэкранный редактор зон. Хранит зоны в экранных координатах текущей ориентации
 * и переводит их в [Zone] только при сохранении или повороте экрана.
 */
@SuppressLint("ViewConstructor")
class ZoneEditorView(
    context: Context,
    private var geometry: ScreenGeometry,
    zones: List<Zone>,
    private val onFinish: (List<Zone>?) -> Unit,
) : FrameLayout(context) {

    private enum class Gesture { NONE, CREATE, MOVE, RESIZE }

    private val rects = zones.map { geometry.toScreen(it) }.toMutableList()
    private var selected = -1

    private var gesture = Gesture.NONE
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var startX = 0f
    private var startY = 0f
    private val original = RectF() // зона до начала перемещения или изменения размера
    private val draft = RectF() // новая зона, которую сейчас рисуют
    private var dragLeft = false
    private var dragTop = false
    private var dragRight = false
    private var dragBottom = false

    private val minSize = context.dp(24f)
    private val edgeTolerance = context.dp(28f)
    private val snapDistance = context.dp(16f)
    private val handleRadius = context.dp(6f)

    private val screenOffset = IntArray(2)
    // Пока система не прислала отступы, берём типовые высоты системных панелей
    private val barInsets = Rect(0, systemDimen("status_bar_height"), 0, systemDimen("navigation_bar_height"))

    private val fillPaint = paint(Paint.Style.FILL, R.color.zone_fill)
    private val strokePaint = paint(Paint.Style.STROKE, R.color.zone_stroke, 2f)
    private val selectedFillPaint = paint(Paint.Style.FILL, R.color.zone_selected_fill)
    private val selectedStrokePaint = paint(Paint.Style.STROKE, R.color.zone_selected_stroke, 3f)
    private val handlePaint = paint(Paint.Style.FILL, R.color.zone_selected_stroke)

    private val hint: TextView
    private val toolbar: LinearLayout
    private val moveButton: TextView
    private val deleteButton: TextView
    private val clearButton: TextView
    private var toolbarAtTop = false

    init {
        setWillNotDraw(false)
        setBackgroundColor(context.getColor(R.color.editor_dim))

        hint = TextView(context).apply {
            setText(R.string.editor_hint)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setShadowLayer(context.dp(4f), 0f, 0f, Color.BLACK)
        }
        addView(hint, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        moveButton = button("↑") {
            toolbarAtTop = !toolbarAtTop
            positionToolbar()
        }
        deleteButton = button(context.getString(R.string.editor_delete)) { deleteSelected() }
        clearButton = button(context.getString(R.string.editor_clear)) {
            rects.clear()
            selected = -1
            refreshControls()
        }
        val cancelButton = button(context.getString(R.string.editor_cancel)) { onFinish(null) }
        val doneButton = button(context.getString(R.string.editor_done)) { onFinish(result()) }
        doneButton.background = buttonBackground(context.getColor(R.color.accent))

        val row = LinearLayout(context)
        row.addView(moveButton, LinearLayout.LayoutParams(0, context.dp(44), 0.6f))
        for (button in listOf(deleteButton, clearButton, cancelButton, doneButton)) {
            row.addView(button, LinearLayout.LayoutParams(0, context.dp(44), 1f).apply {
                marginStart = context.dp(4)
            })
        }

        toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(context.getColor(R.color.toolbar_bg))
                cornerRadius = context.dp(16f)
            }
            val padding = context.dp(8)
            setPadding(padding, padding, padding, padding)
            isClickable = true // касания по панели не должны рисовать зону под ней
            addView(TextView(context).apply {
                setText(R.string.editor_tip)
                setTextColor(context.getColor(R.color.toolbar_tip))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(context.dp(6), context.dp(2), context.dp(6), context.dp(8))
            })
            addView(row)
        }
        addView(toolbar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                barInsets.set(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                barInsets.set(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
                )
            }
            positionToolbar()
            insets
        }

        positionToolbar()
        refreshControls()
    }

    fun setGeometry(newGeometry: ScreenGeometry) {
        cancelGesture()
        val zones = rects.map { geometry.toZone(it) }
        geometry = newGeometry
        rects.clear()
        zones.mapTo(rects) { geometry.toScreen(it) }
        positionToolbar()
        invalidate()
    }

    private fun result(): List<Zone> = rects.map { geometry.toZone(it) }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        getLocationOnScreen(screenOffset)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(-screenOffset[0].toFloat(), -screenOffset[1].toFloat())
        rects.forEachIndexed { i, rect ->
            if (i != selected) {
                canvas.drawRect(rect, fillPaint)
                canvas.drawRect(rect, strokePaint)
            }
        }
        rects.getOrNull(selected)?.let { rect ->
            canvas.drawRect(rect, selectedFillPaint)
            canvas.drawRect(rect, selectedStrokePaint)
            for (x in floatArrayOf(rect.left, rect.centerX(), rect.right)) {
                for (y in floatArrayOf(rect.top, rect.centerY(), rect.bottom)) {
                    if (x != rect.centerX() || y != rect.centerY()) canvas.drawCircle(x, y, handleRadius, handlePaint)
                }
            }
        }
        if (gesture == Gesture.CREATE) {
            canvas.drawRect(draft, selectedFillPaint)
            canvas.drawRect(draft, selectedStrokePaint)
        }
        canvas.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Следим только за первым пальцем: случайные вторые касания не ломают жест
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                startGesture(screenX(event, 0), screenY(event, 0))
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index >= 0) moveGesture(screenX(event, index), screenY(event, index))
            }
            MotionEvent.ACTION_POINTER_UP ->
                if (event.getPointerId(event.actionIndex) == pointerId) finishGesture()
            MotionEvent.ACTION_UP -> finishGesture()
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        invalidate()
        return true
    }

    private fun screenX(event: MotionEvent, index: Int) = event.getX(index) + screenOffset[0]

    private fun screenY(event: MotionEvent, index: Int) = event.getY(index) + screenOffset[1]

    private fun startGesture(x: Float, y: Float) {
        startX = x
        startY = y
        val current = rects.getOrNull(selected)
        if (current != null && grabEdges(current, x, y)) {
            gesture = Gesture.RESIZE
            original.set(current)
            return
        }
        val hit = rects.indexOfLast { it.contains(x, y) }
        if (hit >= 0) {
            selected = hit
            gesture = Gesture.MOVE
            original.set(rects[hit])
        } else {
            selected = -1
            gesture = Gesture.CREATE
            draft.set(x, y, x, y)
        }
        refreshControls()
    }

    /** Запоминает, за какие края выбранной зоны взялся палец. false — ни за какие. */
    private fun grabEdges(rect: RectF, x: Float, y: Float): Boolean {
        if (x < rect.left - edgeTolerance || x > rect.right + edgeTolerance ||
            y < rect.top - edgeTolerance || y > rect.bottom + edgeTolerance
        ) return false
        // Внутри зоны край не толще трети её размера, чтобы за середину можно было перетаскивать
        val insideX = minOf(edgeTolerance, rect.width() / 3)
        val insideY = minOf(edgeTolerance, rect.height() / 3)
        dragLeft = x < rect.left + insideX
        dragRight = x > rect.right - insideX
        dragTop = y < rect.top + insideY
        dragBottom = y > rect.bottom - insideY
        return dragLeft || dragRight || dragTop || dragBottom
    }

    private fun moveGesture(x: Float, y: Float) {
        val width = geometry.width.toFloat()
        val height = geometry.height.toFloat()
        when (gesture) {
            Gesture.CREATE -> draft.set(
                snap(minOf(startX, x), width), snap(minOf(startY, y), height),
                snap(maxOf(startX, x), width), snap(maxOf(startY, y), height),
            )
            Gesture.MOVE -> rects[selected].offsetTo(
                snapMove(original.left + x - startX, original.width(), width),
                snapMove(original.top + y - startY, original.height(), height),
            )
            Gesture.RESIZE -> {
                val rect = rects[selected]
                rect.set(original)
                if (dragLeft) rect.left = snap(original.left + x - startX, width).coerceAtMost(rect.right - minSize)
                if (dragRight) rect.right = snap(original.right + x - startX, width).coerceAtLeast(rect.left + minSize)
                if (dragTop) rect.top = snap(original.top + y - startY, height).coerceAtMost(rect.bottom - minSize)
                if (dragBottom) rect.bottom = snap(original.bottom + y - startY, height).coerceAtLeast(rect.top + minSize)
            }
            Gesture.NONE -> Unit
        }
    }

    /** Не даёт выйти за экран и «примагничивает» к краю, если палец рядом с ним. */
    private fun snap(value: Float, limit: Float) = when {
        value < snapDistance -> 0f
        value > limit - snapDistance -> limit
        else -> value
    }

    private fun snapMove(start: Float, size: Float, limit: Float) = when {
        start < snapDistance -> 0f
        start + size > limit - snapDistance -> limit - size
        else -> start
    }

    private fun finishGesture() {
        if (gesture == Gesture.CREATE && draft.width() >= minSize && draft.height() >= minSize) {
            rects += RectF(draft)
            selected = rects.lastIndex
        }
        gesture = Gesture.NONE
        pointerId = MotionEvent.INVALID_POINTER_ID
        refreshControls()
    }

    private fun cancelGesture() {
        if (gesture == Gesture.MOVE || gesture == Gesture.RESIZE) rects[selected].set(original)
        gesture = Gesture.NONE
        pointerId = MotionEvent.INVALID_POINTER_ID
        refreshControls()
    }

    private fun deleteSelected() {
        if (selected < 0) return
        rects.removeAt(selected)
        selected = -1
        refreshControls()
    }

    private fun refreshControls() {
        deleteButton.setActive(selected >= 0)
        clearButton.setActive(rects.isNotEmpty())
        hint.visibility = if (rects.isEmpty() && gesture == Gesture.NONE) VISIBLE else GONE
        invalidate()
    }

    private fun positionToolbar() {
        val margin = context.dp(12)
        val maxWidth = context.dp(480)
        val available = geometry.width - barInsets.left - barInsets.right - 2 * margin
        toolbar.layoutParams = (toolbar.layoutParams as LayoutParams).apply {
            width = if (available > maxWidth) maxWidth else LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER_HORIZONTAL or if (toolbarAtTop) Gravity.TOP else Gravity.BOTTOM
            setMargins(
                margin + barInsets.left, margin + barInsets.top,
                margin + barInsets.right, margin + barInsets.bottom,
            )
        }
        moveButton.text = if (toolbarAtTop) "↓" else "↑"
    }

    private fun button(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        maxLines = 1
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        setAutoSizeTextTypeUniformWithConfiguration(9, 15, 1, TypedValue.COMPLEX_UNIT_SP)
        setPadding(context.dp(4), 0, context.dp(4), 0)
        background = buttonBackground(context.getColor(R.color.toolbar_button))
        setOnClickListener { onClick() }
    }

    private fun buttonBackground(color: Int): Drawable {
        val radius = context.dp(10f)
        val shape = GradientDrawable().apply { setColor(color); cornerRadius = radius }
        val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = radius }
        return RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.toolbar_ripple)), shape, mask)
    }

    private fun TextView.setActive(active: Boolean) {
        isEnabled = active
        alpha = if (active) 1f else 0.35f
    }

    private fun paint(style: Paint.Style, color: Int, strokeDp: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = style
        this.color = context.getColor(color)
        strokeWidth = context.dp(strokeDp)
    }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun systemDimen(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else 0
    }
}
