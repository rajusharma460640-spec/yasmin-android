package com.yasmin.ai.access

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

data class ScreenRead(
    val ok: Boolean,
    val packageName: String,
    val text: String,
    val note: String
)

/**
 * Optional screen reader. It does nothing until the user turns it on in Android's
 * Accessibility settings, and then it only reads the screen when Yasmin asks it to
 * (readScreen). It ignores all screen events and never stores or logs what it sees.
 */
class YasminAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Toast.makeText(this, "Yasmin screen reader ON", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally ignored: we only read the screen on request.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Reads the visible text of the screen that is in front right now. */
    fun readScreen(): ScreenRead {
        val root = rootInActiveWindow
            ?: return ScreenRead(false, "", "", "Screen abhi padh nahi paayi (koi window active nahi).")
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg in BLOCKED_PACKAGES) {
            return ScreenRead(false, pkg, "", "Is app ki screen suraksha ke liye nahi padhi jaati.")
        }
        val out = StringBuilder()
        var hidden = false
        var count = 0

        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > MAX_DEPTH || count > MAX_NODES || out.length > MAX_CHARS) return
            count++
            if (node.isVisibleToUser) {
                if (node.isPassword) {
                    hidden = true
                } else {
                    val t = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
                    if (t.isNotEmpty()) {
                        if (SENSITIVE.containsMatchIn(t)) hidden = true
                        else out.append(t).append('\n')
                    }
                }
            }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }

        walk(root, 0)
        val note = if (hidden) "Kuch sensitive text (password ya OTP jaisa) chhupa diya gaya." else ""
        return ScreenRead(true, pkg, out.toString().trim().take(MAX_CHARS), note)
    }

    companion object {
        @Volatile
        var instance: YasminAccessibilityService? = null
            private set

        private const val MAX_DEPTH = 30
        private const val MAX_NODES = 600
        private const val MAX_CHARS = 4000

        private val SENSITIVE = Regex(
            "\\botp\\b|one[- ]time|verification code|passcode|\\bpin\\b|\\bcvv\\b|ओटीपी|पासवर्ड|पिन",
            RegexOption.IGNORE_CASE
        )

        private val BLOCKED_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "com.google.android.apps.walletnfcrel",
            "com.android.vending",
            "com.x8bit.bitwarden",
            "com.lastpass.lpandroid",
            "com.onepassword.android"
        )
    }
}
