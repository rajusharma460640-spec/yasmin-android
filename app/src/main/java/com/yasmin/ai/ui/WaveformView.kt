package com.yasmin.ai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Animated speaking waveform. It is only composed while Yasmin is speaking, so its clock
 * (and all drawing) stops the moment speech ends.
 * NOTE: Android TTS does not expose the real audio level, so the bars are a simulated animation
 * driven by the "speaking" state, not a true measurement of the audio.
 */
@Composable
fun WaveformView(modifier: Modifier = Modifier, bars: Int = 28) {
    val clock = rememberClock(30)
    val color = MaterialTheme.colorScheme.secondary

    Canvas(modifier.fillMaxWidth().height(32.dp)) {
        val t = clock.value
        val gap = size.width / bars
        val bw = gap * 0.5f
        for (i in 0 until bars) {
            val envelope = 0.5f + 0.5f * sin(i * 0.45f + t * 1.3f)
            val a = abs(sin(t * 7f + i * 0.8f)) * abs(sin(t * 3.3f + i * 0.37f))
            val h = size.height * (0.12f + 0.88f * a * (0.4f + 0.6f * envelope))
            drawRoundRect(
                color = color.copy(alpha = 0.9f),
                topLeft = Offset(i * gap + (gap - bw) / 2f, (size.height - h) / 2f),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f)
            )
        }
    }
}
