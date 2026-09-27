package com.fossick.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fossick.app.ui.screens.HomeScreen
import com.fossick.app.ui.theme.FossickTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            FossickTheme {
                HomeScreen()
            }
        }
    }
}