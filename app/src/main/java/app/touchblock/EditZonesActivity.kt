package app.touchblock

import android.app.Activity
import android.os.Bundle

/**
 * Кнопка «Зоны» в уведомлении открывает эту невидимую активность, а не службу напрямую:
 * запуск активности сворачивает шторку, и редактор сразу виден поверх текущего приложения.
 */
class EditZonesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BlockerService.instance?.openEditor()
        finish()
    }
}
