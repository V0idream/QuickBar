package io.github.quickbar.service

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class ClipboardCaptureController(
    context: Context,
    private val onTextCopied: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private var enabled = false
    private var recentSelection: String? = null
    private var recentSelectionAt = 0L

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        if (enabled) captureClipboardText()
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (enabled) {
            clipboard.addPrimaryClipChangedListener(listener)
        } else {
            clipboard.removePrimaryClipChangedListener(listener)
            recentSelection = null
            recentSelectionAt = 0L
        }
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!enabled || event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            cacheSelection(event)
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && looksLikeCopyAction(event)) {
            handler.postDelayed({
                if (!captureClipboardText()) {
                    val cached = recentSelection
                    if (!cached.isNullOrBlank() &&
                        SystemClock.uptimeMillis() - recentSelectionAt <= 8_000L
                    ) {
                        onTextCopied(cached)
                    }
                }
            }, 90L)
        }
    }

    fun destroy() {
        if (enabled) clipboard.removePrimaryClipChangedListener(listener)
        handler.removeCallbacksAndMessages(null)
        enabled = false
    }

    /** A one-shot read initiated by the user; does not start clipboard monitoring. */
    fun currentClipboardText(): String? = runCatching {
        val clip = clipboard.primaryClip
        when {
            clip == null || clip.itemCount == 0 -> null
            clip.description.label?.toString() == "QuickBar" -> null
            isSensitive(clip.description) -> null
            else -> clip.getItemAt(0).text?.toString()?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun cacheSelection(event: AccessibilityEvent) {
        val source = event.source ?: return
        if (source.isPassword) return
        if (source.isShowingHintText) return

        val fullText = source.text?.toString().orEmpty()
        if (fullText.isEmpty()) return

        val rawStart = source.textSelectionStart
        val rawEnd = source.textSelectionEnd
        val start = minOf(rawStart, rawEnd)
        val end = maxOf(rawStart, rawEnd)
        if (start < 0 || end <= start || end > fullText.length) return

        recentSelection = fullText.substring(start, end)
        recentSelectionAt = SystemClock.uptimeMillis()
    }

    private fun looksLikeCopyAction(event: AccessibilityEvent): Boolean {
        val parts = buildList {
            event.text.mapNotNullTo(this) { it?.toString() }
            event.contentDescription?.toString()?.let(::add)
            event.source?.text?.toString()?.let(::add)
            event.source?.contentDescription?.toString()?.let(::add)
        }
        return parts.any { raw ->
            val value = raw.trim().lowercase()
            value == "复制" || value.startsWith("复制") || value == "copy" || value.startsWith("copy ")
        }
    }

    private fun captureClipboardText(): Boolean {
        val text = currentClipboardText() ?: return false
        onTextCopied(text)
        return true
    }

    private fun isSensitive(description: ClipDescription): Boolean {
        val extras = description.extras ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
        } else {
            extras.getBoolean("android.content.extra.IS_SENSITIVE", false)
        }
    }
}
