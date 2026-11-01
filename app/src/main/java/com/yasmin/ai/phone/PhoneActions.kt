package com.yasmin.ai.phone

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.AlarmClock

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
            else -> "Ye kaam abhi supported nahi: $action"
        }
    } catch (e: Exception) {
        "Fail: ${e.message}"
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
}
