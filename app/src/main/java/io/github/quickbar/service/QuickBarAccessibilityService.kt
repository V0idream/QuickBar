package io.github.quickbar.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import io.github.quickbar.data.AppDatabase
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
    private var scriptJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        database = AppDatabase.get(this)
        input = InputController(this)
        overlay = OverlayController(
            service = this,
            onSnippetClick = { snippet ->
                if (!input.insertText(snippet.content)) {
                    toast("没有找到可编辑的输入框")
                }
            },
            onScriptClick = { script -> runScript(script.id, script.name) },
        )
        overlay.show()

        serviceScope.launch {
            combine(
                database.snippetDao().observeEnabled(),
                database.scriptDao().observeEnabled(),
            ) { snippets, scripts -> snippets to scripts }
                .collectLatest { (snippets, scripts) ->
                    overlay.update(snippets, scripts)
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The overlay intentionally stays available while the service is enabled.
        // All input actions still target only the currently focused editable node.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scriptJob?.cancel()
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
}
