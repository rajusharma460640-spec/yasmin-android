package com.yasmin.ai.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.yasmin.ai.data.ChatMessage

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onMicPermissionResult(granted)
    }
    fun onMicClick() {
        if (vm.isListening) {
            vm.toggleMic()
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.startListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val listState = rememberLazyListState()
    val itemCount = vm.messages.size + if (vm.isThinking) 1 else 0
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val avatarState = vm.avatarState
    val speaking = avatarState == AvatarState.Speaking
    val dim = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)

    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Yasmin", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text("AI assistant (main insaan nahi hoon)", style = MaterialTheme.typography.bodySmall, color = dim)
            }
            IconButton(onClick = { vm.updateVoiceOutput(!vm.voiceOutput) }) {
                Text(if (vm.voiceOutput) "\uD83D\uDD0A" else "\uD83D\uDD07", fontSize = 20.sp)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
            }
        }

        // Avatar (tap while speaking to stop)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AvatarView(
                state = avatarState,
                avatarSize = if (imeVisible) 56.dp else 120.dp,
                modifier = Modifier.clickable(enabled = speaking) { vm.stopSpeaking() }
            )
            if (!imeVisible) {
                val label = when (avatarState) {
                    AvatarState.Thinking -> "Yasmin soch rahi hai..."
                    AvatarState.Speaking -> "Yasmin bol rahi hai (roknay ke liye avatar dabao)"
                    AvatarState.Listening -> "Sun rahi hoon..."
                    AvatarState.Idle -> ""
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = dim, modifier = Modifier.height(18.dp))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (imeVisible && !speaking) 0.dp else 32.dp)
                    .padding(horizontal = 56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (speaking) WaveformView()
            }
        }

        if (!vm.hasApiKey) {
            Text(
                "API key set nahi hai. Upar Settings icon dabao aur key daalo.",
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.messages, key = { it.id }) { Bubble(it) }
            if (vm.isThinking) {
                item { TypingIndicator() }
            }
        }

        vm.error?.let {
            Text(
                it,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        vm.voiceNotice?.let {
            Text(
                it,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (vm.isListening) {
            Text(
                if (vm.speech.partial.isEmpty()) "Bolo, main sun rahi hoon..." else vm.speech.partial,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (vm.micDenied) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Mic permission nahi mili, isliye voice input band hai.",
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }) { Text("App settings") }
            }
        }

        val canSend = input.isNotBlank() && !vm.isThinking
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val listening = vm.isListening
            IconButton(
                onClick = { onMicClick() },
                modifier = Modifier.background(
                    if (listening) MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                    else androidx.compose.ui.graphics.Color.Transparent,
                    CircleShape
                )
            ) {
                Text(if (listening) "\u23F9\uFE0F" else "\uD83C\uDFA4", fontSize = 22.sp)
            }
            Text(
                if (vm.micLang == "hi") "HI" else "EN",
                Modifier
                    .clickable { vm.updateMicLang(if (vm.micLang == "hi") "en" else "hi") }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Yasmin se baat karo...") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            IconButton(
                onClick = {
                    vm.send(input)
                    input = ""
                },
                enabled = canSend
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(vm = vm, onDismiss = { showSettings = false })
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val isUser = m.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                m.content,
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Only composed while the AI is thinking, so its animation stops automatically afterwards. */
@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    val a by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 600, delayMillis = i * 150),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot$i"
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .alpha(a)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoicePicker(label: String, lang: String, vm: ChatViewModel) {
    val ready = vm.speaker.ready
    val voices = remember(ready) { vm.voicesFor(lang) }
    var open by remember { mutableStateOf(false) }
    val selected = if (lang == "hi") vm.voiceHi else vm.voiceEn

    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        if (voices.isEmpty()) {
            Text(
                if (!ready) {
                    "Voice engine load ho raha hai..."
                } else {
                    "Koi voice nahi mili. Phone Settings > Text-to-speech mein is bhasha ka voice data download karo."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { open = true }) {
                        Text(if (selected.isEmpty()) "Default voice" else selected)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(
                            text = { Text("Default voice") },
                            onClick = {
                                vm.setVoice(lang, "")
                                open = false
                            }
                        )
                        voices.forEach { v ->
                            DropdownMenuItem(
                                text = {
                                    Text(v.name + if (v.isNetworkConnectionRequired) " (online)" else "")
                                },
                                onClick = {
                                    vm.setVoice(lang, v.name)
                                    open = false
                                }
                            )
                        }
                    }
                }
                TextButton(onClick = { vm.previewVoice(lang, selected) }) { Text("Test") }
            }
        }
    }
}

@Composable
private fun SettingsDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
    var baseUrl by remember { mutableStateOf(vm.baseUrl) }
    var model by remember { mutableStateOf(vm.model) }
    var apiKey by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val host = runCatching { java.net.URI(baseUrl).host }.getOrNull() ?: "AI provider"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Privacy: aapke messages $host ko bheje jaate hain jawab ke liye. " +
                        "History sirf is phone mein save hoti hai. " +
                        "Mic se bola hua audio phone ki speech service (aam taur par Google) process karti hai; kai phones par ye online hota hai.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (vm.hasApiKey) "API key (saved, naya daalo to badlegi)" else "API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true
                )

                Text("Voice (turant lagu hota hai)", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Voice output", Modifier.weight(1f))
                    Switch(checked = vm.voiceOutput, onCheckedChange = { vm.updateVoiceOutput(it) })
                }
                Text("Bhasha", style = MaterialTheme.typography.bodySmall)
                listOf("auto" to "Auto (jawab ki bhasha ke hisaab se)", "hi" to "Hindi", "en" to "English")
                    .forEach { (key, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.updateLangMode(key) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = vm.langMode == key, onClick = { vm.updateLangMode(key) })
                            Text(label)
                        }
                    }
                Text("Speed: " + "%.1f".format(vm.speechRate) + "x", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = vm.speechRate,
                    onValueChange = { vm.updateSpeechRate(it) },
                    valueRange = 0.5f..1.5f
                )
                Text("Volume: " + (vm.volume * 100).toInt() + "%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = vm.volume,
                    onValueChange = { vm.updateVolume(it) },
                    valueRange = 0.2f..1f
                )
                VoicePicker("Hindi voice", "hi", vm)
                VoicePicker("English voice", "en", vm)
                Text(
                    "Android voices ka gender app ko nahi pata chalta. Test dabake suno aur jo female lage wo chuno. " +
                        "\"online\" voices ka text phone ke bahar speech engine ko ja sakta hai. " +
                        "Phone ke volume buttons bhi kaam karte hain.",
                    style = MaterialTheme.typography.bodySmall
                )

                TextButton(onClick = { confirmClear = true }) {
                    Text("Conversation history delete karo", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveSettings(baseUrl, model, apiKey)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("History delete karein?") },
            text = { Text("Saari purani chat is phone se hamesha ke liye hat jayegi.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory()
                    confirmClear = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}
