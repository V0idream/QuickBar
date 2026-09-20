package io.github.quickbar.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.quickbar.data.AppDatabase
import io.github.quickbar.data.ScriptEntity
import io.github.quickbar.data.ScriptStepEntity
import io.github.quickbar.data.SnippetEntity
import io.github.quickbar.data.StepType
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    database: AppDatabase,
    script: ScriptEntity,
    snippets: List<SnippetEntity>,
    onBack: () -> Unit,
    onRename: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val steps by database.scriptStepDao().observeForScript(script.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var editingStep by remember { mutableStateOf<ScriptStepEntity?>(null) }
    var showNewStep by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                    }
                },
                title = {
                    Column {
                        Text(script.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "拖动步骤调整执行顺序",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Rounded.Edit, "重命名")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewStep = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("添加步骤") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Text(
                    "执行时 QuickBar 会严格从上到下运行。建议跨输入框时保留“下一个输入框”或“IME 回车”，页面响应较慢时可插入“等待”。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (steps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "脚本还是空的，先添加一个步骤。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                ReorderableSteps(
                    modifier = Modifier.fillMaxSize(),
                    steps = steps,
                    snippets = snippets,
                    onEdit = { editingStep = it },
                    onDelete = { step ->
                        scope.launch { database.scriptStepDao().delete(step) }
                    },
                    onReorder = { reordered ->
                        scope.launch {
                            reordered.forEachIndexed { index, item ->
                                if (item.sortOrder != index) {
                                    database.scriptStepDao().update(item.copy(sortOrder = index))
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    if (showNewStep) {
        StepEditorDialog(
            initial = null,
            snippets = snippets,
            onDismiss = { showNewStep = false },
            onSave = { draft ->
                scope.launch {
                    database.scriptStepDao().insert(
                        draft.copy(
                            id = 0,
                            scriptId = script.id,
                            sortOrder = steps.size,
                        ),
                    )
                }
                showNewStep = false
            },
        )
    }

    editingStep?.let { current ->
        StepEditorDialog(
            initial = current,
            snippets = snippets,
            onDismiss = { editingStep = null },
            onSave = { draft ->
                scope.launch {
                    database.scriptStepDao().update(
                        draft.copy(
                            id = current.id,
                            scriptId = current.scriptId,
                            sortOrder = current.sortOrder,
                        ),
                    )
                }
                editingStep = null
            },
            onDelete = {
                scope.launch { database.scriptStepDao().delete(current) }
                editingStep = null
            },
        )
    }
}

@Composable
private fun ReorderableSteps(
    modifier: Modifier,
    steps: List<ScriptStepEntity>,
    snippets: List<SnippetEntity>,
    onEdit: (ScriptStepEntity) -> Unit,
    onDelete: (ScriptStepEntity) -> Unit,
    onReorder: (List<ScriptStepEntity>) -> Unit,
) {
    val listState = rememberLazyListState()
    var displaySteps by remember { mutableStateOf(steps) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(steps) {
        if (draggingId == null) displaySteps = steps
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(displaySteps, key = { it.id }) { step ->
            val isDragging = draggingId == step.id
            StepCard(
                step = step,
                snippets = snippets,
                onEdit = { onEdit(step) },
                onDelete = { onDelete(step) },
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else 0f
                        shadowElevation = if (isDragging) 12.dp.toPx() else 0f
                    },
                handleModifier = Modifier.pointerInput(step.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingId = step.id
                            dragOffset = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.y

                            val visible = listState.layoutInfo.visibleItemsInfo
                            val currentInfo = visible.firstOrNull { it.key == step.id }
                                ?: return@detectDragGesturesAfterLongPress
                            val currentCenter = currentInfo.offset + currentInfo.size / 2f + dragOffset
                            val targetInfo = visible.minByOrNull {
                                abs(currentCenter - (it.offset + it.size / 2f))
                            } ?: return@detectDragGesturesAfterLongPress
                            val targetId = targetInfo.key as? Long
                                ?: return@detectDragGesturesAfterLongPress
                            if (targetId == step.id) return@detectDragGesturesAfterLongPress

                            val from = displaySteps.indexOfFirst { it.id == step.id }
                            val to = displaySteps.indexOfFirst { it.id == targetId }
                            if (from >= 0 && to >= 0 && from != to) {
                                displaySteps = displaySteps.toMutableList().apply {
                                    add(to, removeAt(from))
                                }
                                dragOffset = 0f
                            }
                        },
                        onDragEnd = {
                            draggingId = null
                            dragOffset = 0f
                            onReorder(displaySteps)
                        },
                        onDragCancel = {
                            draggingId = null
                            dragOffset = 0f
                            onReorder(displaySteps)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun StepCard(
    step: ScriptStepEntity,
    snippets: List<SnippetEntity>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    val (title, detail) = stepDescription(step, snippets)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = "长按拖动",
                modifier = handleModifier.padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, "编辑步骤")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, "删除步骤")
            }
        }
    }
}

private fun stepDescription(
    step: ScriptStepEntity,
    snippets: List<SnippetEntity>,
): Pair<String, String> = when (step.type) {
    StepType.INSERT_SNIPPET -> {
        val snippet = snippets.firstOrNull { it.id == step.snippetId }
        "插入快捷内容" to (snippet?.let { "${it.name} · ${it.content}" } ?: "引用的快捷内容已删除")
    }
    StepType.INSERT_TEXT -> "插入自定义文本" to step.textValue.orEmpty()
    StepType.NEXT_FIELD -> "下一个输入框" to "Tab 语义：聚焦当前窗口中的下一个可编辑控件"
    StepType.IME_ENTER -> "IME 回车" to "触发当前编辑框的输入法动作；不支持时采用安全回退"
    StepType.DELAY -> "等待" to "${step.numberValue ?: 300L} ms"
    else -> "未知步骤" to step.type
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepEditorDialog(
    initial: ScriptStepEntity?,
    snippets: List<SnippetEntity>,
    onDismiss: () -> Unit,
    onSave: (ScriptStepEntity) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: StepType.INSERT_SNIPPET) }
    var snippetId by remember(initial?.id) {
        mutableStateOf(initial?.snippetId ?: snippets.firstOrNull()?.id)
    }
    var customText by remember(initial?.id) { mutableStateOf(initial?.textValue.orEmpty()) }
    var delayText by remember(initial?.id) {
        mutableStateOf((initial?.numberValue ?: 300L).toString())
    }
    var snippetMenuExpanded by remember { mutableStateOf(false) }

    val valid = when (type) {
        StepType.INSERT_SNIPPET -> snippetId != null
        StepType.INSERT_TEXT -> customText.isNotEmpty()
        StepType.DELAY -> delayText.toLongOrNull() != null
        else -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加步骤" else "编辑步骤") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StepTypeChoice("快捷内容", StepType.INSERT_SNIPPET, type) { type = it }
                    StepTypeChoice("自定义文本", StepType.INSERT_TEXT, type) { type = it }
                    StepTypeChoice("下一输入框", StepType.NEXT_FIELD, type) { type = it }
                    StepTypeChoice("IME 回车", StepType.IME_ENTER, type) { type = it }
                    StepTypeChoice("等待", StepType.DELAY, type) { type = it }
                }

                when (type) {
                    StepType.INSERT_SNIPPET -> {
                        Box {
                            OutlinedButton(
                                onClick = { snippetMenuExpanded = true },
                                enabled = snippets.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    snippets.firstOrNull { it.id == snippetId }?.name
                                        ?: "没有可用快捷内容",
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Rounded.KeyboardArrowDown, null)
                            }
                            DropdownMenu(
                                expanded = snippetMenuExpanded,
                                onDismissRequest = { snippetMenuExpanded = false },
                            ) {
                                snippets.forEach { snippet ->
                                    DropdownMenuItem(
                                        text = { Text(snippet.name) },
                                        onClick = {
                                            snippetId = snippet.id
                                            snippetMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    StepType.INSERT_TEXT -> {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            label = { Text("要输入的文本") },
                            minLines = 3,
                            maxLines = 7,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    StepType.NEXT_FIELD -> Text(
                        "把焦点移动到当前窗口的下一个可编辑输入框。它对应脚本中的 Tab 语义，不伪造硬件按键。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    StepType.IME_ENTER -> Text(
                        "优先触发当前输入框公开的 IME action。多行文本框不支持时插入换行，单行输入框则尝试移动到下一个输入框。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    StepType.DELAY -> {
                        OutlinedTextField(
                            value = delayText,
                            onValueChange = { delayText = it.filter(Char::isDigit) },
                            label = { Text("等待毫秒数") },
                            supportingText = { Text("执行时限制在 0–10000 ms。") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        ScriptStepEntity(
                            id = initial?.id ?: 0,
                            scriptId = initial?.scriptId ?: 0,
                            sortOrder = initial?.sortOrder ?: 0,
                            type = type,
                            snippetId = if (type == StepType.INSERT_SNIPPET) snippetId else null,
                            textValue = if (type == StepType.INSERT_TEXT) customText else null,
                            numberValue = if (type == StepType.DELAY) {
                                delayText.toLongOrNull()?.coerceIn(0L, 10_000L)
                            } else null,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, null)
                        Text("删除")
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun StepTypeChoice(
    label: String,
    value: String,
    selected: String,
    onSelected: (String) -> Unit,
) {
    FilterChip(
        selected = value == selected,
        onClick = { onSelected(value) },
        label = { Text(label) },
    )
}
