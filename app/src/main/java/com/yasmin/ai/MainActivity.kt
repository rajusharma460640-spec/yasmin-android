package com.yasmin.ai

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.yasmin.ai.ui.ChatScreen
import com.yasmin.ai.ui.ChatViewModel
import com.yasmin.ai.ui.YasminTheme

class MainActivity : ComponentActivity() {

    // Same instance that ChatScreen's viewModel() receives (both use this activity as owner).
    private val chatVm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            YasminTheme {
                ChatScreen(chatVm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        chatVm.onAppVisible(true)
    }

    override fun onStop() {
        chatVm.onAppVisible(false)
        // Never keep the microphone open once the app is not on screen.
        chatVm.cancelListening()
        super.onStop()
    }
}
