package com.example.projectbird

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.projectbird.app.navigation.ProjectBirdApp
import com.example.projectbird.ui.theme.ProjectBirdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ProjectBirdTheme {
                   ProjectBirdApp()
            }
        }
    }
}
