package com.flashlaunch

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashlaunch.data.Prefs
import com.flashlaunch.ui.AppsViewModel
import com.flashlaunch.ui.FlashLaunchTheme
import com.flashlaunch.ui.HomeScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bottom-anchored panel: the area above stays transparent.
        window.setGravity(Gravity.BOTTOM)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0f)
        applyPanelHeight(Prefs(this).panelHeight)

        setContent {
            FlashLaunchTheme {
                val viewModel: AppsViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.panelHeight) {
                    applyPanelHeight(state.panelHeight)
                }

                // Close the panel right after an app was launched.
                LaunchedEffect(state.lastLaunchedAt) {
                    if (state.lastLaunchedAt > 0L) {
                        viewModel.consumeLaunchSignal()
                        finish()
                    }
                }

                HomeScreen(viewModel, onClose = { finish() })
            }
        }
    }

    private fun applyPanelHeight(percent: Int) {
        val metrics = resources.displayMetrics
        val height = (metrics.heightPixels * percent / 100f).toInt()
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
        window.setGravity(Gravity.BOTTOM)
    }
}
