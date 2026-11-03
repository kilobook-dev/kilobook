package com.odolog.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.odolog.spike.ui.DashboardScreen

class MainActivity : ComponentActivity() {
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DashboardScreen(viewModel)
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
