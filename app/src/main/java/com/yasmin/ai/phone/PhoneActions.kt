package com.yasmin.ai.phone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import kotlin.math.roundToInt

object PhoneActions {
    private val TAG = Regex("""\[\[(\w+):([^\]]*)\]\]""")

    fun process(ctx: Context, reply: String): Pair<String, List<String>> {
        val notes = mutableListOf<String>()
        TAG.findAll(reply).forEach {
            notes.add(run(ctx, it.groupValues[1], it.groupValues[2].trim()))
        }
        return TAG.replace(reply, "").trim() to notes
    }

    fun appNames(ctx: Context): List<String> {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(q, 0)
            .map { it.loadLabel(pm).toString() }
            .distinct().sorted().take(120)
    }

    private fun run(ctx: Context, action: String, arg: String): String = try {
        when (action) {
            "open_app" -> openApp(ctx, arg)
            "torch" -> torch(ctx, arg == "on")
            "alarm" -> alarm(ctx, arg)
            "timer" -> timer(ctx, arg)
            "volume" -> volume(ctx, arg)
            "media" -> media(ctx, arg)
            "search" -> search(ctx, arg)
            "maps" -> maps(ctx, arg, false)
            "navigate" -> maps(ctx, arg, true)
            "settings" -> openSettings(ctx, arg)
            "sms" -> smsDraft(ctx, arg)
            "dial" -> dial(ctx, arg)
            else -> "Ye kaam abhi supported nahi: $action"
        }
    } catch (e: Exception) {
        "Fail: ${e.message}"
    }

    private fun start(ctx: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun openApp(ctx: Context, name: String): String {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(q, 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().equals(name, true) }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(name, true) }
            ?: return "App nahi mila: $name"
        val launch = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
            ?: return "App khul nahi sakta: $name"
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(launch)
        return "${hit.loadLabel(pm)} khol diya"
    }

    private fun torch(ctx: Context, on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Flash nahi mila"
        cm.setTorchMode(id, on)
        return if (on) "Torch on" else "Torch off"
    }

    private fun alarm(ctx: Context, arg: String): String {
        val (h, m) = arg.split(":").map { it.trim().toInt() }
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return "Alarm set: %02d:%02d".format(h, m)
    }

    private fun timer(ctx: Context, arg: String): String {
        val sec = arg.toInt()
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, sec)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return "Timer set: $sec sec"
    }

    // ---- New actions (all visible to the user; nothing is sent or called automatically) ----

    private fun volume(ctx: Context, arg: String): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val a = arg.trim().lowercase()
        val pct = a.removeSuffix("%").trim().toIntOrNull()
        val ui = AudioManager.FLAG_SHOW_UI
        return when {
            pct != null && pct in 0..100 -> {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (pct / 100f * max).roundToInt(), ui)
                "Volume $pct%"
            }
            a == "up" -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, ui)
                "Volume badha diya"
            }
            a == "down" -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, ui)
                "Volume kam kiya"
            }
            a == "mute" -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, ui)
                "Mute kiya"
            }
            a == "max" -> {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max, ui)
                "Volume full"
            }
            else -> "Volume ka matlab samajh nahi aaya: $arg"
        }
    }

    private fun media(ctx: Context, arg: String): String {
        val a = arg.trim().lowercase()
        val code = when (a) {
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return when (a) {
            "next" -> "Agla gaana"
            "previous" -> "Pichla gaana"
            else -> "Play/pause"
        }
    }

    private fun search(ctx: Context, arg: String): String {
        val q = arg.trim().take(200)
        if (q.isEmpty()) return "Kya search karna hai?"
        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))
        return if (start(ctx, Intent(Intent.ACTION_VIEW, uri))) "Search: $q" else "Browser nahi mila"
    }

    private fun maps(ctx: Context, arg: String, navigate: Boolean): String {
        val q = arg.trim().take(120)
        if (q.isEmpty()) return "Kahan ka map dikhana hai?"
        if (navigate && start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(q))))) {
            return "Rasta: $q"
        }
        return if (start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))))) {
            "Map: $q"
        } else {
            "Maps app nahi mili"
        }
    }

    private fun openSettings(ctx: Context, arg: String): String {
        val page = arg.trim().lowercase()
        val action = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return if (start(ctx, Intent(action))) {
            "Settings khuli (Android 10+ par Wi-Fi/Bluetooth ka switch aapko dabana hoga)"
        } else {
            "Settings nahi khuli"
        }
    }

    /** Opens the SMS app with a draft. The user picks the contact and presses Send. */
    private fun smsDraft(ctx: Context, arg: String): String {
        val body = arg.trim().take(300)
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        if (body.isNotEmpty()) i.putExtra("sms_body", body)
        return if (start(ctx, i)) "SMS draft khula. Contact chuno aur Send aap dabao" else "SMS app nahi mili"
    }

    /** Opens the dialer with the number filled in. The user presses the call button. */
    private fun dial(ctx: Context, arg: String): String {
        val num = arg.filter { it.isDigit() || it == '+' }
        if (num.count { it.isDigit() } < 5) return "Number samajh nahi aaya: $arg"
        return if (start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))))) {
            "Dialer khula ($num). Call button aap dabao"
        } else {
            "Dialer nahi khula"
        }
    }
}
