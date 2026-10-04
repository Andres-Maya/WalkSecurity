package com.andres.walksecurity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.andres.walksecurity.ui.navigation.WalkSecurityRoot
import com.andres.walksecurity.ui.theme.WalkSecurityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WalkSecurityTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WalkSecurityRoot()
                }
            }
        }
    }
}
