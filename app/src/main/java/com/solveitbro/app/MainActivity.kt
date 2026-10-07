package com.solveitbro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.solveitbro.app.navigation.SolveItBroNavHost
import com.solveitbro.app.ui.theme.SolveItBroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SolveItBroTheme {
                SolveItBroNavHost()
            }
        }
    }
}
