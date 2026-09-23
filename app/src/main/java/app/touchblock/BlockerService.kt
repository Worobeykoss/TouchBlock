package app.touchblock

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Служба специальных возможностей, которая держит над экраном прозрачные окна размером с зоны.
 * Касание, начавшееся внутри такого окна, достаётся ему и дальше не передаётся.
 *
 * Окна служб спецвозможностей (TYPE_ACCESSIBILITY_OVERLAY), в отличие от обычных окон
 * «поверх других приложений», система не прячет на экранах Настроек, в диалогах разрешений
 * и в приложениях, которые запрещают наложения. Кроме того, они лежат выше строки состояния,
 * панели навигации, клавиатуры и экрана блокировки.
 *
 * Службу запускает система, когда пользователь включает её в настройках спецвозможностей.
 * Состояние (зоны, вкл/пауза, подсветка) берётся из [Prefs] и применяется при каждом изменении.
 */
class BlockerService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var display: Display
    private lateinit var windowManager: WindowManager
    private lateinit var uiContext: Context
    private lateinit var geometry: ScreenGeometry

    private val zoneViews = mutableListOf<View>()
    private var editor: ZoneEditorView? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val current = ScreenGeometry.of(display)
            if (current == geometry) return
            geometry = current
            editor?.let {
                it.setGeometry(current)
                windowManager.updateViewLayout(it, overlayParams(0, 0, current.width, current.height))
            }
            showZones()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        val displayManager = getSystemService(DisplayManager::class.java)
        display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        // Окна спецвозможностей добавляются только через WindowManager самой службы: в нём её токен
        windowManager = getSystemService(WindowManager::class.java)
        uiContext = ContextThemeWrapper(this, R.style.OverlayTheme)
        geometry = ScreenGeometry.of(display)
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        createNotificationChannel()
        prefs.storage.registerOnSharedPreferenceChangeListener(this)
        instance = this
        render()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        if (::prefs.isInitialized) {
            prefs.storage.unregisterOnSharedPreferenceChangeListener(this)
            getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
            removeZoneViews()
            editor?.let { windowManager.removeView(it) }
            editor = null
            getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(storage: SharedPreferences?, key: String?) = render()

    private fun render() {
        showZones()
        updateNotification()
    }

    fun openEditor() {
        if (editor != null) return
        removeZoneViews()
        val view = ZoneEditorView(uiContext, geometry, prefs.zones, ::closeEditor)
        windowManager.addView(view, overlayParams(0, 0, geometry.width, geometry.height))
        editor = view
        updateNotification()
    }

    /** [result] — новые зоны или null, если настройку отменили. */
    private fun closeEditor(result: List<Zone>?) {
        editor?.let { windowManager.removeView(it) }
        editor = null
        if (result != null) prefs.zones = result
        render()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showZones() {
        removeZoneViews()
        if (!prefs.enabled || prefs.paused || editor != null) return
        val highlight = prefs.showZones
        for (zone in prefs.zones) {
            val rect = geometry.toScreen(zone)
            val left = floor(rect.left).toInt()
            val top = floor(rect.top).toInt()
            val width = (ceil(rect.right).toInt() - left).coerceAtLeast(1)
            val height = (ceil(rect.bottom).toInt() - top).coerceAtLeast(1)
            val view = View(uiContext)
            if (highlight) view.background = zoneBackground()
            view.setOnTouchListener { _, _ -> true }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Просим систему не распознавать в зоне жест «назад» от края экрана
                // (Android выполняет это не больше чем для 200dp по высоте у каждого края)
                view.systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
            }
            windowManager.addView(view, overlayParams(left, top, width, height))
            zoneViews += view
        }
    }

    private fun removeZoneViews() {
        zoneViews.forEach { windowManager.removeView(it) }
        zoneViews.clear()
    }

    private fun zoneBackground() = GradientDrawable().apply {
        setColor(getColor(R.color.zone_fill))
        setStroke(dp(2), getColor(R.color.zone_stroke))
    }

    // Координаты абсолютные, поэтому гравитация LEFT, а не START
    @SuppressLint("RtlHardcoded")
    private fun overlayParams(x: Int, y: Int, width: Int, height: Int) =
        WindowManager.LayoutParams(
            width, height, x, y,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Уведомление с кнопками управления висит, пока блокировка включена. */
    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (prefs.enabled) manager.notify(NOTIFICATION_ID, buildNotification()) else manager.cancel(NOTIFICATION_ID)
    }

    private fun buildNotification(): Notification {
        val title = when {
            editor != null -> R.string.notif_editing
            prefs.paused -> R.string.notif_paused
            else -> R.string.notif_active
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle(getString(title))
            .setContentText(getString(R.string.notif_zones, prefs.zones.size))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
        if (editor == null) {
            val editZones = PendingIntent.getActivity(
                this, 1, Intent(this, EditZonesActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                broadcastAction(
                    if (prefs.paused) R.string.action_resume else R.string.action_pause,
                    NotificationActionReceiver.ACTION_TOGGLE_PAUSE,
                ),
            )
            builder.addAction(action(R.string.action_edit, editZones))
            builder.addAction(broadcastAction(R.string.action_stop, NotificationActionReceiver.ACTION_STOP))
        }
        return builder.build()
    }

    private fun broadcastAction(label: Int, action: String): Notification.Action {
        val intent = Intent(this, NotificationActionReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return action(label, pending)
    }

    private fun action(label: Int, intent: PendingIntent) =
        Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_block), getString(label), intent)
            .build()

    companion object {
        private const val CHANNEL_ID = "blocker"
        private const val NOTIFICATION_ID = 1

        /** Запущенная системой служба или null, если она выключена в спецвозможностях. */
        var instance: BlockerService? = null
            private set

        /** Включена ли служба в настройках спецвозможностей. */
        fun isEnabled(context: Context): Boolean {
            val self = ComponentName(context, BlockerService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
        }
    }
}
