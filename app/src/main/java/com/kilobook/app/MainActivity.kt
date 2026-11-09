package com.kilobook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.kilobook.app.ui.MainScreen
import com.kilobook.app.ui.theme.KiloTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KiloTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onScreenStart()
    }

    override fun onStop() {
        viewModel.onScreenStop()
        super.onStop()
    }
}
