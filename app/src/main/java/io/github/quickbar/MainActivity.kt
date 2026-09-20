package io.github.quickbar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.quickbar.data.AppDatabase
import io.github.quickbar.ui.QuickBarApp
import io.github.quickbar.ui.QuickBarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = AppDatabase.get(this)
        setContent {
            QuickBarTheme {
                QuickBarApp(database)
            }
        }
    }
}

