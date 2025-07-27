// MainActivity

package com.di.fitric

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.di.fitric.ui.MainAppShell
import com.di.fitric.ui.theme.FitRicTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Handles drawing behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)   // draw edge-to-edge
        WindowInsetsControllerCompat(window, window.decorView).let { c ->
            c.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())        // status + nav gone
        }
        setContent {
            // DadsFitnessAssistantTheme will contain your high-contrast theme
            FitRicTheme {
                MainAppShell()
            }
        }
    }
}
