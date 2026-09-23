package app.touchblock

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

@Suppress("UseSwitchCompatOrMaterialCode")
class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var permissionCard: View
    private lateinit var restrictedHint: View
    private lateinit var enabledSwitch: Switch
    private lateinit var statusText: TextView
    private lateinit var zonesText: TextView
    private lateinit var showZonesSwitch: Switch

    /** Переключатели меняются из кода - их слушатели в это время молчат. */
    private var updating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupEdgeToEdge()
        prefs = Prefs(this)

        permissionCard = findViewById(R.id.permission_card)
        restrictedHint = findViewById(R.id.restricted_hint)
        enabledSwitch = findViewById(R.id.enabled_switch)
        statusText = findViewById(R.id.status_text)
        zonesText = findViewById(R.id.zones_text)
        showZonesSwitch = findViewById(R.id.show_zones_switch)

        // «Ограниченные настройки» для приложений не из магазина появились в Android 13
        restrictedHint.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE
        findViewById<View>(R.id.open_accessibility).setOnClickListener { openAccessibilitySettings() }
        findViewById<View>(R.id.open_app_info).setOnClickListener { openAppInfo() }
        findViewById<View>(R.id.edit_zones).setOnClickListener { editZones() }
        enabledSwitch.setOnCheckedChangeListener { _, checked -> if (!updating) setBlocking(checked) }
        showZonesSwitch.setOnCheckedChangeListener { _, checked -> if (!updating) prefs.showZones = checked }
    }

    override fun onResume() {
        super.onResume()
        prefs.storage.registerOnSharedPreferenceChangeListener(this)
        refresh()
    }

    override fun onPause() {
        prefs.storage.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    // Состояние меняется и из уведомления, и из редактора поверх этого экрана
    override fun onSharedPreferenceChanged(storage: SharedPreferences?, key: String?) = refresh()

    private fun refresh() {
        val serviceEnabled = BlockerService.isEnabled(this)
        permissionCard.visibility = if (serviceEnabled) View.GONE else View.VISIBLE
        val enabled = prefs.enabled && serviceEnabled
        statusText.setText(
            when {
                !enabled -> R.string.status_off
                prefs.paused -> R.string.status_paused
                else -> R.string.status_on
            },
        )
        zonesText.text = getString(R.string.zones_count, prefs.zones.size)
        updating = true
        enabledSwitch.isChecked = enabled
        showZonesSwitch.isChecked = prefs.showZones
        updating = false
    }

    private fun setBlocking(on: Boolean) {
        if (!on) {
            prefs.enabled = false
            return
        }
        requestNotificationPermission()
        prefs.paused = false
        // Запоминаем намерение сразу: блокировка заработает, как только включат службу
        prefs.enabled = true
        val service = BlockerService.instance
        if (service == null) {
            refresh()
            openAccessibilitySettings()
            return
        }
        // Без зон блокировать нечего - сразу открываем редактор
        if (prefs.zones.isEmpty()) service.openEditor()
    }

    private fun editZones() {
        val service = BlockerService.instance
        if (service == null) {
            Toast.makeText(this, R.string.service_not_running, Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        service.openEditor()
    }

    private fun openAccessibilitySettings() {
        // Сразу на страницу нашей службы; прошивки без такой страницы откроют общий список
        val component = ComponentName(this, BlockerService::class.java).flattenToString()
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) throw UnsupportedOperationException()
            startActivity(details)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }

    /** Рисуем под системными панелями и сами отступаем от них - одинаково на всех версиях. */
    private fun setupEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // С Android 15 это поведение по умолчанию, поэтому метод помечен устаревшим
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        val header = findViewById<View>(R.id.header)
        val content = findViewById<View>(R.id.content)
        val headerPadding = header.paddingTop
        val contentPadding = content.paddingBottom
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { _, insets ->
            val left: Int
            val top: Int
            val right: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                left = i.left; top = i.top; right = i.right; bottom = i.bottom
            } else {
                @Suppress("DEPRECATION")
                left = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                right = insets.systemWindowInsetRight
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            header.setPadding(headerPadding + left, headerPadding + top, headerPadding + right, headerPadding)
            content.setPadding(contentPadding + left, contentPadding, contentPadding + right, contentPadding + bottom)
            insets
        }
    }
}
