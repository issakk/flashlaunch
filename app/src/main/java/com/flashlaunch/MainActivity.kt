package com.flashlaunch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashlaunch.ui.AppsViewModel
import com.flashlaunch.ui.FlashLaunchTheme
import com.flashlaunch.ui.HomeScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlashLaunchTheme {
                val viewModel: AppsViewModel = viewModel()
                HomeScreen(viewModel)
            }
        }
    }
}
