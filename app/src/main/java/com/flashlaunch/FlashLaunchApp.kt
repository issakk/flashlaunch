package com.flashlaunch

import android.app.Application
import com.flashlaunch.ui.AppsViewModel

/**
 * The panel activity finishes as soon as an app is launched, so an
 * activity-scoped ViewModel would reload the app list (and re-scan
 * PackageManager) on every open. Keeping it here means the list, the sort
 * order and the privilege state survive between opens: the panel paints from
 * memory on the first frame and refreshes in the background.
 */
class FlashLaunchApp : Application() {
    val appsViewModel: AppsViewModel by lazy { AppsViewModel(this) }
}
