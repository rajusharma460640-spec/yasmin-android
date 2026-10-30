package com.yasmin.ai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class AvatarState { Idle, Listening, Thinking, Speaking }

private val PRIMARY = Color(0xFFC77DFF)
private val LIGHT = Color(0xFFE9C9FF)
private val DEEP = Color(0xFF4A1F78)
private val TEAL = Color(0xFF5EEAD4)
private val FACE = Color(0xFF1B1D2B)
private val MOUTH_DARK = Color(0xFF2A1238)

/**
 * A clock in seconds that ticks only while the composable using it is on screen.
 * fps = 0 means every frame (~60 fps); otherwise it ticks at roughly that rate to save battery.
 * Frames are not delivered while the app is in the background.
 */
@Composable
fun rememberClock(fps: Int): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(fps) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            time.floatValue = (time.floatValue + (now - last) / 1_000_000_000f) % 1000f
            last = now
            if (fps > 0) delay(1000L / fps)
        }
    }
    return time
}

/**
 * Original fictional AI avatar drawn with Canvas (no images, no real person's face).
 * Idle: slow breathing + blinking at ~15 fps. Thinking: sweeping arcs. Speaking: mouth + glow.
 * Listening: ripples (drawn, but triggered only once voice input exists in Stage G).
 */
@Composable
fun AvatarView(
    state: AvatarState,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 120.dp
) {
    val clock = rememberClock(if (state == AvatarState.Idle) 15 else 0)

    Canvas(modifier.size(avatarSize)) {
        val t = clock.value
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f
        val center = Offset(cx, cy)
        val u = r * 0.95f * (1f + 0.015f * sin(t * 1.6f))
        val ringR = r * 0.92f
        val pulse = abs(sin(t * 6f))

        // Listening ripples
        if (state == AvatarState.Listening) {
            for (k in 0..1) {
                val f = (t * 0.7f + k * 0.5f) % 1f
                drawCircle(
                    color = TEAL.copy(alpha = (1f - f) * 0.6f),
                    radius = r * (0.8f + 0.2f * f),
                    center = center,
                    style = Stroke(width = r * 0.02f)
                )
            }
        }

        // Soft glow behind the avatar while speaking
        if (state == AvatarState.Speaking) {
            drawCircle(
                color = PRIMARY.copy(alpha = 0.05f + 0.08f * pulse),
                radius = ringR,
                center = center
            )
        }

        // Ring
        val ringColor = when (state) {
            AvatarState.Idle -> PRIMARY.copy(alpha = 0.35f)
            AvatarState.Speaking -> PRIMARY.copy(alpha = 0.5f + 0.5f * pulse)
            AvatarState.Thinking -> PRIMARY.copy(alpha = 0.25f)
            AvatarState.Listening -> TEAL.copy(alpha = 0.8f)
        }
        drawCircle(color = ringColor, radius = ringR, center = center, style = Stroke(width = r * 0.02f))

        // Thinking arcs
        if (state == AvatarState.Thinking) {
            drawArc(
                color = TEAL,
                startAngle = (t * 220f) % 360f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(cx - ringR, cy - ringR),
                size = Size(ringR * 2f, ringR * 2f),
                style = Stroke(width = r * 0.04f, cap = StrokeCap.Round)
            )
            drawArc(
                color = PRIMARY.copy(alpha = 0.7f),
                startAngle = 180f - (t * 140f) % 360f,
                sweepAngle = 40f,
                useCenter = false,
                topLeft = Offset(cx - ringR, cy - ringR),
                size = Size(ringR * 2f, ringR * 2f),
                style = Stroke(width = r * 0.03f, cap = StrokeCap.Round)
            )
        }

        // Hair (back layer)
        val hair = Path().apply {
            moveTo(cx - 0.55f * u, cy - 0.05f * u)
            cubicTo(cx - 0.75f * u, cy - 0.75f * u, cx + 0.75f * u, cy - 0.75f * u, cx + 0.55f * u, cy - 0.05f * u)
            cubicTo(cx + 0.78f * u, cy + 0.35f * u, cx + 0.55f * u, cy + 0.70f * u, cx + 0.38f * u, cy + 0.74f * u)
            cubicTo(cx + 0.45f * u, cy + 0.40f * u, cx + 0.40f * u, cy + 0.25f * u, cx + 0.30f * u, cy + 0.15f * u)
            lineTo(cx - 0.30f * u, cy + 0.15f * u)
            cubicTo(cx - 0.40f * u, cy + 0.25f * u, cx - 0.45f * u, cy + 0.40f * u, cx - 0.38f * u, cy + 0.74f * u)
            cubicTo(cx - 0.55f * u, cy + 0.70f * u, cx - 0.78f * u, cy + 0.35f * u, cx - 0.55f * u, cy - 0.05f * u)
            close()
        }
        drawPath(
            path = hair,
            brush = Brush.verticalGradient(
                colors = listOf(PRIMARY, DEEP),
                startY = cy - 0.6f * u,
                endY = cy + 0.75f * u
            )
        )

        // Face plate
        drawOval(
            color = FACE,
            topLeft = Offset(cx - 0.34f * u, cy - 0.40f * u),
            size = Size(0.68f * u, 0.80f * u)
        )
        drawOval(
            color = PRIMARY.copy(alpha = 0.6f),
            topLeft = Offset(cx - 0.34f * u, cy - 0.40f * u),
            size = Size(0.68f * u, 0.80f * u),
            style = Stroke(width = 0.012f * u)
        )

        // Side-swept fringe
        val fringe = Path().apply {
            moveTo(cx - 0.36f * u, cy - 0.05f * u)
            cubicTo(cx - 0.30f * u, cy - 0.45f * u, cx + 0.10f * u, cy - 0.46f * u, cx + 0.36f * u, cy - 0.20f * u)
            cubicTo(cx + 0.10f * u, cy - 0.28f * u, cx - 0.15f * u, cy - 0.22f * u, cx - 0.36f * u, cy - 0.05f * u)
            close()
        }
        drawPath(path = fringe, color = LIGHT.copy(alpha = 0.9f))

        // Forehead light
        drawCircle(
            color = TEAL,
            radius = 0.022f * u,
            center = Offset(cx, cy - 0.33f * u)
        )

        // Eyes (blink every ~4 seconds)
        val blinkPhase = t % 4.2f
        val blink = if (blinkPhase < 0.16f) {
            1f - sin(blinkPhase / 0.16f * PI.toFloat()) * 0.92f
        } else {
            1f
        }
        val look = if (state == AvatarState.Thinking) -0.04f * u else 0f
        val ew = 0.13f * u
        val eh = (0.095f * u * blink).coerceAtLeast(0.012f * u)
        val ey = cy - 0.02f * u + look
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * 0.14f * u
            drawCircle(color = TEAL.copy(alpha = 0.15f), radius = 0.09f * u, center = Offset(ex, ey))
            drawRoundRect(
                color = TEAL,
                topLeft = Offset(ex - ew / 2f, ey - eh / 2f),
                size = Size(ew, eh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(eh / 2f, eh / 2f)
            )
        }

        // Mouth
        val my = cy + 0.22f * u
        when (state) {
            AvatarState.Speaking -> {
                val open = abs(sin(t * 9f)) * (0.5f + 0.5f * abs(sin(t * 4.1f)))
                val mw = 0.13f * u
                val mh = (0.025f + 0.085f * open) * u
                val tl = Offset(cx - mw / 2f, my - mh / 2f)
                drawOval(color = MOUTH_DARK, topLeft = tl, size = Size(mw, mh))
                drawOval(color = LIGHT, topLeft = tl, size = Size(mw, mh), style = Stroke(width = 0.012f * u))
            }
            AvatarState.Thinking -> {
                drawLine(
                    color = LIGHT,
                    start = Offset(cx - 0.05f * u, my),
                    end = Offset(cx + 0.05f * u, my),
                    strokeWidth = 0.016f * u,
                    cap = StrokeCap.Round
                )
            }
            AvatarState.Listening -> {
                drawCircle(
                    color = LIGHT,
                    radius = 0.03f * u,
                    center = Offset(cx, my),
                    style = Stroke(width = 0.014f * u)
                )
            }
            AvatarState.Idle -> {
                drawArc(
                    color = LIGHT,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(cx - 0.09f * u, my - 0.05f * u),
                    size = Size(0.18f * u, 0.10f * u),
                    style = Stroke(width = 0.018f * u, cap = StrokeCap.Round)
                )
            }
        }
    }
}
