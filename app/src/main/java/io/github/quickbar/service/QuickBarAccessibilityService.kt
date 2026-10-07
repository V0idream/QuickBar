package io.github.quickbar.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import io.github.quickbar.data.AppDatabase
import io.github.quickbar.data.OverlayPreferences
import io.github.quickbar.data.ScriptEntity
import io.github.quickbar.data.SnippetEntity
import io.github.quickbar.data.StepType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class QuickBarAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var database: AppDatabase
    private lateinit var input: InputController
    private lateinit var overlay: OverlayController
    private lateinit var clipboardCapture: ClipboardCaptureController
    private var scriptJob: Job? = null
    private var currentSnippets: List<SnippetEntity> = emptyList()
    private var currentScripts: List<ScriptEntity> = emptyList()
    private val clipboardShortcuts = ArrayDeque<ClipboardShortcut>()
    private var lastClipboardText: String? = null
    private var lastClipboardAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        database = AppDatabase.get(this)
        input = InputController(this)
        clipboardCapture = ClipboardCaptureController(this) { text ->
            addClipboardShortcut(text)
        }
        overlay = OverlayController(
            service = this,
            onSnippetClick = { snippet ->
                if (!input.insertText(snippet.content)) {
                    toast("没有找到可编辑的输入框")
                }
            },
            onScriptClick = { script -> runScript(script.id, script.name) },
            onClipboardClick = { item ->
                if (!input.insertText(item.content)) {
                    toast("没有找到可编辑的输入框")
                }
            },
            onClipboardToggle = { enabled -> setClipboardMonitoring(enabled) },
            onQuotePasteClick = { pasteClipboardQuote() },
        )
        overlay.show()
        clipboardCapture.setEnabled(OverlayPreferences.clipboardMonitoring(this))

        serviceScope.launch {
            combine(
                database.snippetDao().observeEnabled(),
                database.scriptDao().observeEnabled(),
            ) { snippets, scripts -> snippets to scripts }
                .collectLatest { (snippets, scripts) ->
                    currentSnippets = snippets
                    currentScripts = scripts
                    refreshOverlay()
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (::clipboardCapture.isInitialized) clipboardCapture.onAccessibilityEvent(event)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scriptJob?.cancel()
        if (::clipboardCapture.isInitialized) clipboardCapture.destroy()
        if (::overlay.isInitialized) overlay.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun runScript(scriptId: Long, scriptName: String) {
        if (scriptJob?.isActive == true) {
            toast("已有自动化正在执行")
            return
        }
        scriptJob = serviceScope.launch {
            val steps = database.scriptStepDao().getForScript(scriptId)
            if (steps.isEmpty()) {
                toast("脚本“$scriptName”没有步骤")
                return@launch
            }

            for ((index, step) in steps.withIndex()) {
                val ok = when (step.type) {
                    StepType.INSERT_SNIPPET -> {
                        val snippet = step.snippetId?.let { database.snippetDao().getById(it) }
                        snippet != null && input.insertText(snippet.content)
                    }
                    StepType.INSERT_TEXT -> input.insertText(step.textValue.orEmpty())
                    StepType.NEXT_FIELD -> input.focusNextField()
                    StepType.IME_ENTER -> input.imeEnter()
                    StepType.DELAY -> {
                        delay((step.numberValue ?: 300L).coerceIn(0L, 10_000L))
                        true
                    }
                    else -> false
                }

                if (!ok) {
                    toast("脚本在第 ${index + 1} 步中止：目标输入框不可用")
                    return@launch
                }

                if (step.type != StepType.DELAY) delay(140)
            }
            toast("脚本“$scriptName”执行完成")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun pasteClipboardQuote() {
        val current = clipboardCapture.currentClipboardText()
        val recent = clipboardShortcuts.firstOrNull()
            ?.takeIf { OverlayPreferences.clipboardMonitoring(this) &&
                SystemClock.uptimeMillis() - lastClipboardAt < 120_000L }
            ?.content
        val text = current ?: recent
        if (text.isNullOrBlank()) {
            toast("未读取到文本，请开启剪贴板快捷项并重新复制")
            return
        }
        if (!input.insertQuote(text)) {
            toast("请先打开当前对话并聚焦输入框后重试")
        }
    }

    private fun setClipboardMonitoring(enabled: Boolean) {
        OverlayPreferences.setClipboardMonitoring(this, enabled)
        clipboardCapture.setEnabled(enabled)
        refreshOverlay()
        toast(if (enabled) "剪贴板快捷项已开启" else "剪贴板快捷项已关闭")
    }

    private fun addClipboardShortcut(content: String) {
        val normalized = content.trimEnd('\r', '\n')
        if (normalized.isBlank()) return

        val now = SystemClock.uptimeMillis()
        if (normalized == lastClipboardText && now - lastClipboardAt < 900L) return
        lastClipboardText = normalized
        lastClipboardAt = now

        clipboardShortcuts.addFirst(
            ClipboardShortcut(
                id = SystemClock.elapsedRealtimeNanos(),
                content = normalized,
            ),
        )
        while (clipboardShortcuts.size > 20) clipboardShortcuts.removeLast()
        refreshOverlay()
    }

    private fun refreshOverlay() {
        if (!::overlay.isInitialized) return
        overlay.update(
            snippets = currentSnippets,
            scripts = currentScripts,
            clipboardEnabled = OverlayPreferences.clipboardMonitoring(this),
            clipboardItems = clipboardShortcuts.toList(),
        )
    }
}
