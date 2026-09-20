package io.github.quickbar.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

class InputController(
    private val service: AccessibilityService,
) {
    fun insertText(textToInsert: String): Boolean {
        if (textToInsert.isEmpty()) return true
        val node = focusedEditable() ?: return false
        if (node.isPassword) return pasteFallback(node, textToInsert)

        val current = node.text?.toString().orEmpty()
        val rawStart = node.textSelectionStart
        val rawEnd = node.textSelectionEnd
        val start = if (rawStart in 0..current.length) rawStart else current.length
        val end = if (rawEnd in start..current.length) rawEnd else start
        val replacement = buildString(current.length - (end - start) + textToInsert.length) {
            append(current, 0, start)
            append(textToInsert)
            append(current, end, current.length)
        }

        val setTextArgs = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                replacement,
            )
        }
        val replaced = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextArgs)
        if (replaced) {
            val cursor = start + textToInsert.length
            val selectionArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
            return true
        }

        return pasteFallback(node, textToInsert)
    }

    fun focusNextField(): Boolean {
        val root = activeRoot() ?: return false
        val editable = mutableListOf<AccessibilityNodeInfo>()
        collectEditableNodes(root, editable)
        if (editable.isEmpty()) return false

        val focusedIndex = editable.indexOfFirst { it.isFocused }
        val target = when {
            focusedIndex >= 0 && focusedIndex + 1 < editable.size -> editable[focusedIndex + 1]
            focusedIndex == -1 -> editable.first()
            else -> return false
        }

        val focused = target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (focused) {
            target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        }
        return focused
    }

    fun imeEnter(): Boolean {
        val node = focusedEditable() ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val imeEnter = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER
            if (node.actionList.any { it.id == imeEnter.id } && node.performAction(imeEnter.id)) {
                return true
            }
        }

        // On older/unsupported widgets, a multiline editor can receive a newline.
        // For single-line fields, moving focus is safer than replacing the field.
        return if (node.isMultiLine) insertText("\n") else focusNextField()
    }

    fun hasFocusedEditable(): Boolean = focusedEditable() != null

    private fun focusedEditable(): AccessibilityNodeInfo? {
        val root = activeRoot() ?: return null
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let {
            if (it.isEditable && it.isVisibleToUser) return it
        }
        return findFocusedEditable(root)
    }

    private fun activeRoot(): AccessibilityNodeInfo? {
        service.rootInActiveWindow?.let { return it }
        return service.windows
            .asSequence()
            .filter { it.isActive || it.isFocused }
            .mapNotNull { it.root }
            .firstOrNull()
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findFocusedEditable(child)?.let { return it }
        }
        return null
    }

    private fun collectEditableNodes(
        node: AccessibilityNodeInfo,
        result: MutableList<AccessibilityNodeInfo>,
    ) {
        if (node.isEditable && node.isVisibleToUser && node.isEnabled) {
            result += node
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectEditableNodes(it, result) }
        }
    }

    private fun pasteFallback(node: AccessibilityNodeInfo, text: String): Boolean {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QuickBar", text))
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }
}

