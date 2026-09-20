package io.github.quickbar.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.quickbar.data.AppDatabase
import io.github.quickbar.data.OverlayPreferences
import io.github.quickbar.data.ScriptEntity
import io.github.quickbar.data.SnippetEntity
import io.github.quickbar.BuildConfig
import kotlinx.coroutines.launch

private enum class MainPage { SHORTCUTS, SCRIPTS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickBarApp(database: AppDatabase) {
    val scope = rememberCoroutineScope()
    val snippets by database.snippetDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scripts by database.scriptDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var page by rememberSaveable { mutableStateOf(MainPage.SHORTCUTS) }
    var editingScriptId by rememberSaveable { mutableStateOf<Long?>(null) }
    var snippetDialog by remember { mutableStateOf<SnippetEntity?>(null) }
    var showNewSnippet by rememberSaveable { mutableStateOf(false) }
    var scriptDialog by remember { mutableStateOf<ScriptEntity?>(null) }
    var showNewScript by rememberSaveable { mutableStateOf(false) }

    val editingScript = scripts.firstOrNull { it.id == editingScriptId }
    if (editingScriptId != null && editingScript != null) {
        ScriptEditorScreen(
            database = database,
            script = editingScript,
            snippets = snippets,
            onBack = { editingScriptId = null },
            onRename = { scriptDialog = editingScript },
        )
        if (scriptDialog != null) {
            ScriptDialog(
                initial = scriptDialog,
                defaultName = scriptDialog!!.name,
                onDismiss = { scriptDialog = null },
                onSave = { name ->
                    val current = scriptDialog ?: return@ScriptDialog
                    scope.launch { database.scriptDao().update(current.copy(name = name)) }
                    scriptDialog = null
                },
                onDelete = {
                    val current = scriptDialog ?: return@ScriptDialog
                    scope.launch { database.scriptDao().delete(current) }
                    scriptDialog = null
                    editingScriptId = null
                },
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("QuickBar", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = when (page) {
                                MainPage.SHORTCUTS -> "快捷输入"
                                MainPage.SCRIPTS -> "自动化"
                                MainPage.SETTINGS -> "设置与权限"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page == MainPage.SHORTCUTS,
                    onClick = { page = MainPage.SHORTCUTS },
                    icon = { Icon(Icons.Rounded.TextFields, null) },
                    label = { Text("快捷栏") },
                )
                NavigationBarItem(
                    selected = page == MainPage.SCRIPTS,
                    onClick = { page = MainPage.SCRIPTS },
                    icon = { Icon(Icons.Rounded.Bolt, null) },
                    label = { Text("自动化") },
                )
                NavigationBarItem(
                    selected = page == MainPage.SETTINGS,
                    onClick = { page = MainPage.SETTINGS },
                    icon = { Icon(Icons.Rounded.Settings, null) },
                    label = { Text("设置") },
                )
            }
        },
        floatingActionButton = {
            when (page) {
                MainPage.SHORTCUTS -> ExtendedFloatingActionButton(
                    onClick = { showNewSnippet = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("新建按钮") },
                )
                MainPage.SCRIPTS -> ExtendedFloatingActionButton(
                    onClick = { showNewScript = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("新建脚本") },
                )
                MainPage.SETTINGS -> Unit
            }
        },
    ) { padding ->
        when (page) {
            MainPage.SHORTCUTS -> ShortcutScreen(
                modifier = Modifier.padding(padding),
                snippets = snippets,
                onEdit = { snippetDialog = it },
                onToggle = { item, enabled ->
                    scope.launch { database.snippetDao().update(item.copy(enabled = enabled)) }
                },
                onMove = { from, to ->
                    scope.launch { reorderSnippets(database, snippets, from, to) }
                },
            )
            MainPage.SCRIPTS -> ScriptsScreen(
                modifier = Modifier.padding(padding),
                scripts = scripts,
                onOpen = { editingScriptId = it.id },
                onEdit = { scriptDialog = it },
                onToggle = { item, enabled ->
                    scope.launch { database.scriptDao().update(item.copy(enabled = enabled)) }
                },
            )
            MainPage.SETTINGS -> SettingsScreen(Modifier.padding(padding))
        }
    }

    if (showNewSnippet) {
        SnippetDialog(
            initial = null,
            defaultName = "快捷 ${snippets.size + 1}",
            onDismiss = { showNewSnippet = false },
            onSave = { name, content ->
                scope.launch {
                    database.snippetDao().insert(
                        SnippetEntity(
                            name = name,
                            content = content,
                            sortOrder = snippets.size,
                        ),
                    )
                }
                showNewSnippet = false
            },
        )
    }

    snippetDialog?.let { item ->
        SnippetDialog(
            initial = item,
            defaultName = item.name,
            onDismiss = { snippetDialog = null },
            onSave = { name, content ->
                scope.launch { database.snippetDao().update(item.copy(name = name, content = content)) }
                snippetDialog = null
            },
            onDelete = {
                scope.launch { database.snippetDao().delete(item) }
                snippetDialog = null
            },
        )
    }

    if (showNewScript) {
        ScriptDialog(
            initial = null,
            defaultName = "自动化 ${scripts.size + 1}",
            onDismiss = { showNewScript = false },
            onSave = { name ->
                scope.launch {
                    database.scriptDao().insert(
                        ScriptEntity(name = name, sortOrder = scripts.size),
                    )
                }
                showNewScript = false
            },
        )
    }

    if (scriptDialog != null) {
        val item = scriptDialog!!
        ScriptDialog(
            initial = item,
            defaultName = item.name,
            onDismiss = { scriptDialog = null },
            onSave = { name ->
                scope.launch { database.scriptDao().update(item.copy(name = name)) }
                scriptDialog = null
            },
            onDelete = {
                scope.launch { database.scriptDao().delete(item) }
                scriptDialog = null
                if (editingScriptId == item.id) editingScriptId = null
            },
        )
    }
}

@Composable
private fun ShortcutScreen(
    modifier: Modifier,
    snippets: List<SnippetEntity>,
    onEdit: (SnippetEntity) -> Unit,
    onToggle: (SnippetEntity, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    if (snippets.isEmpty()) {
        EmptyState(
            modifier = modifier,
            icon = { Icon(Icons.Rounded.Keyboard, null, modifier = Modifier.size(42.dp)) },
            title = "还没有快捷按钮",
            body = "创建按钮后，它会同步出现在屏幕顶部的 QuickBar 悬浮栏中。按钮名称和实际输入内容可以分别编辑。",
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            InfoCard(
                title = "顶部快捷栏",
                body = "按钮顺序与这里一致。点击悬浮栏中的按钮，会把对应文本插入当前光标位置。",
            )
        }
        itemsIndexed(snippets, key = { _, item -> item.id }) { index, item ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.content.ifBlank { "（空文本）" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = item.enabled,
                        onCheckedChange = { onToggle(item, it) },
                    )
                    FilledTonalIconButton(
                        onClick = { if (index > 0) onMove(index, index - 1) },
                        enabled = index > 0,
                    ) { Icon(Icons.Rounded.ArrowUpward, "上移") }
                    FilledTonalIconButton(
                        onClick = { if (index < snippets.lastIndex) onMove(index, index + 1) },
                        enabled = index < snippets.lastIndex,
                    ) { Icon(Icons.Rounded.ArrowDownward, "下移") }
                    IconButton(onClick = { onEdit(item) }) {
                        Icon(Icons.Rounded.Edit, "编辑")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun ScriptsScreen(
    modifier: Modifier,
    scripts: List<ScriptEntity>,
    onOpen: (ScriptEntity) -> Unit,
    onEdit: (ScriptEntity) -> Unit,
    onToggle: (ScriptEntity, Boolean) -> Unit,
) {
    if (scripts.isEmpty()) {
        EmptyState(
            modifier = modifier,
            icon = { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(42.dp)) },
            title = "还没有自动化脚本",
            body = "脚本可以依次插入快捷内容、自定义文本、切换到下一个输入框、触发 IME 回车或等待一段时间。",
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            InfoCard(
                title = "可视化自动化",
                body = "在悬浮栏点击 ⚡ 可切换到脚本列表。脚本只操作当前前台应用的编辑框，任一步失败都会立即停止。",
            )
        }
        itemsIndexed(scripts, key = { _, item -> item.id }) { _, item ->
            Card(
                onClick = { onOpen(item) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Bolt, null)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "点击进入步骤编辑",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = item.enabled,
                        onCheckedChange = { onToggle(item, it) },
                    )
                    IconButton(onClick = { onEdit(item) }) {
                        Icon(Icons.Rounded.Edit, "重命名")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(isQuickBarAccessibilityEnabled(context)) }
    var showDisclosure by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(OverlayPreferences.isVisible(context)) }
    var overlayOrientation by remember { mutableStateOf(OverlayPreferences.orientation(context)) }
    var spanCount by remember { mutableStateOf(OverlayPreferences.spanCount(context)) }
    val overlayPrefs = remember { OverlayPreferences.prefs(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = isQuickBarAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(overlayPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                OverlayPreferences.KEY_VISIBLE -> overlayVisible = OverlayPreferences.isVisible(context)
                OverlayPreferences.KEY_ORIENTATION -> overlayOrientation = OverlayPreferences.orientation(context)
                OverlayPreferences.KEY_SPAN_COUNT -> spanCount = OverlayPreferences.spanCount(context)
            }
        }
        overlayPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { overlayPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                Column(Modifier.padding(18.dp)) {
                    Text("无障碍服务", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (enabled) "已启用：顶部快捷栏应当正在运行。" else "未启用：QuickBar 暂时无法跨应用显示和输入。",
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { showDisclosure = true }) {
                        Text(if (enabled) "打开无障碍设置" else "启用无障碍服务")
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(18.dp)) {
                    Text("悬浮栏布局", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "拖动悬浮栏左侧的 QuickBar 图标可以移动窗口；位置会自动保存。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("显示悬浮栏", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (overlayVisible) "当前显示" else "已关闭，可在这里重新显示",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = overlayVisible,
                            onCheckedChange = {
                                overlayVisible = it
                                OverlayPreferences.setVisible(context, it)
                            },
                            enabled = enabled,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("排列方向", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = overlayOrientation == OverlayPreferences.ORIENTATION_HORIZONTAL,
                            onClick = {
                                overlayOrientation = OverlayPreferences.ORIENTATION_HORIZONTAL
                                OverlayPreferences.setOrientation(context, overlayOrientation)
                            },
                            label = { Text("横向") },
                        )
                        FilterChip(
                            selected = overlayOrientation == OverlayPreferences.ORIENTATION_VERTICAL,
                            onClick = {
                                overlayOrientation = OverlayPreferences.ORIENTATION_VERTICAL
                                OverlayPreferences.setOrientation(context, overlayOrientation)
                            },
                            label = { Text("纵向") },
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (overlayOrientation == OverlayPreferences.ORIENTATION_HORIZONTAL) "行数" else "列数",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { count ->
                            FilterChip(
                                selected = spanCount == count,
                                onClick = {
                                    spanCount = count
                                    OverlayPreferences.setSpanCount(context, count)
                                },
                                label = {
                                    Text(
                                        if (overlayOrientation == OverlayPreferences.ORIENTATION_HORIZONTAL) {
                                            "${count} 行"
                                        } else {
                                            "${count} 列"
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            InfoCard(
                title = "本地优先",
                body = "QuickBar 不申请联网权限。快捷文本、按钮名称和自动化步骤只保存在本机数据库中。无障碍服务仅在你主动点击按钮或脚本时向当前编辑框写入内容。",
            )
        }
        item {
            InfoCard(
                title = "按键动作说明",
                body = "“下一个输入框”是 Tab 的无障碍语义实现；“IME 回车”使用 Android 11+ 的 ACTION_IME_ENTER。应用不会伪造任意硬件按键事件。",
            )
        }
        item {
            Card {
                Column(Modifier.padding(18.dp)) {
                    Text("关于 QuickBar", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    Text("QuickBar ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "本地优先的 Android 快捷输入与轻量自动化工具",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("作者  ·  Voidream", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("License  ·  MIT", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "后续公开版本将以 GitHub 仓库作为项目主页。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("启用前说明") },
            text = {
                Text(
                    "QuickBar 需要无障碍权限来读取当前是否存在可编辑输入框、在屏幕顶部显示快捷栏，并在你点击快捷按钮或运行脚本时修改当前编辑框内容。应用不会把这些数据上传到网络。",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showDisclosure = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("同意并打开设置") }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    title: String,
    body: String,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            icon()
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun SnippetDialog(
    initial: SnippetEntity?,
    defaultName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: defaultName) }
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建快捷按钮" else "编辑快捷按钮") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("按钮名称") },
                    supportingText = { Text("默认名称可以直接保留，也可以自定义。") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("输入内容") },
                    minLines = 4,
                    maxLines = 9,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim().ifEmpty { defaultName }, content) },
                enabled = content.isNotEmpty(),
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
private fun ScriptDialog(
    initial: ScriptEntity?,
    defaultName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建自动化" else "编辑自动化") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("脚本名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim().ifEmpty { defaultName }) },
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

private suspend fun reorderSnippets(
    database: AppDatabase,
    current: List<SnippetEntity>,
    from: Int,
    to: Int,
) {
    if (from !in current.indices || to !in current.indices || from == to) return
    val reordered = current.toMutableList().apply {
        add(to, removeAt(from))
    }
    reordered.forEachIndexed { index, item ->
        if (item.sortOrder != index) {
            database.snippetDao().update(item.copy(sortOrder = index))
        }
    }
}
