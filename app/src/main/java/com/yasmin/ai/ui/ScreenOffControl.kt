package com.yasmin.ai.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yasmin.ai.wake.AssistantService
import com.yasmin.ai.wake.WakeModel

/** One-line switch: "Screen-off Hey Yasmin" ON/OFF, with consent and permissions. */
@Composable
fun ScreenOffControl() {
    val context = LocalContext.current
    val on = AssistantService.running
    var showConsent by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }

    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    fun startIfReady() {
        when {
            !granted(Manifest.permission.RECORD_AUDIO) ->
                notice = "Mic permission nahi mili."
            Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS) ->
                notice = "Notification permission chahiye, taaki mic ON hone par Stop button dikhe."
            !WakeModel.isInstalled(context) ->
                notice = "Offline model install nahi hai. Settings mein purane Hey Yasmin switch ko ek baar ON karke model download karo, phir use OFF kar do."
            else -> {
                notice = ""
                AssistantService.start(context)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startIfReady() }

    fun requestAndStart() {
        val need = mutableListOf<String>()
        if (!granted(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isEmpty()) startIfReady() else launcher.launch(need.toTypedArray())
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            if (on) "\uD83C\uDF19 Screen-off \"Hey Yasmin\": ON (band karne ke liye dabao)"
            else "\uD83C\uDF19 Screen-off \"Hey Yasmin\": OFF (chalu karne ke liye dabao)",
            Modifier
                .fillMaxWidth()
                .clickable {
                    if (on) AssistantService.stop(context) else showConsent = true
                }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (on) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        val shown = if (on && AssistantService.message.isNotEmpty()) AssistantService.message else notice
        if (shown.isNotEmpty()) {
            Text(
                shown,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }

    if (showConsent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val unrestricted = pm.isIgnoringBatteryOptimizations(context.packageName)
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text("Screen-off Hey Yasmin") },
            text = {
                Column {
                    Text(
                        "Chalu karne par Yasmin ka mic tab tak ON rahega jab tak aap Stop na karo, " +
                            "screen off hone par bhi.\n\n" +
                            "• \"Hey Yasmin\" pehchanne ki aawaz phone par offline process hoti hai aur save nahi hoti.\n" +
                            "• \"Hey Yasmin\" ke baad aapka sawaal phone ki speech service (aam taur par Google) " +
                            "text mein badalti hai; kai phones par ye online hota hai. Phir wo text AI provider " +
                            "(Groq) ko bheja jaata hai aur chat history mein save hota hai.\n" +
                            "• Battery zyada lagegi. Notification mein Stop button hai.\n" +
                            "• Settings ke purane Hey Yasmin switch ko OFF rakho.\n\n" +
                            if (unrestricted) "Battery: Unrestricted \u2713"
                            else "Battery abhi restricted hai, isliye phone screen off par ise band kar sakta hai.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }) { Text("Battery settings kholo") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConsent = false
                    requestAndStart()
                }) { Text("Shuru karo") }
            },
            dismissButton = {
                TextButton(onClick = { showConsent = false }) { Text("Cancel") }
            }
        )
    }
}
