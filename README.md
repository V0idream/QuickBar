# QuickBar

QuickBar 是一个本地优先的 Android 快捷输入与轻量自动化工具。它通过 Android 无障碍服务在屏幕顶部显示快捷栏，允许用户预先保存常用文本，并在任意支持标准无障碍编辑操作的输入框中快速插入。

项目当前处于 `0.3.0` 原型阶段。

## 功能

- 自定义任意数量的快捷输入按钮。
- 每个按钮都有默认名称，并可单独修改名称和实际输入内容。
- 顶部悬浮栏使用 `TYPE_ACCESSIBILITY_OVERLAY`，无需申请普通悬浮窗权限。
- 悬浮栏可以通过左侧 QuickBar 图标自由拖动，并记忆上次位置。
- 悬浮栏支持横向与纵向布局；横向可选 1–3 行，纵向可选 1–3 列。
- 悬浮栏提供快捷关闭按钮；关闭后可在应用设置中重新显示，无需禁用或重启无障碍服务。
- 悬浮栏提供剪贴板快捷项开关。开启后，最近复制的文本会以“剪贴板 1、2……”按钮形式加入快捷栏，最多保留 20 条，服务重启后清空。
- 悬浮栏提供“引用”按钮：点击后读取当前剪贴板文本，按 Markdown 引用格式（每行以 `>` 开头）粘贴到当前输入框，不会自动发送。ChatGPT 当前对话中只有一个可编辑输入框时，可在输入框失焦后尝试重新聚焦。
- 点击按钮后，优先通过 `ACTION_SET_TEXT` 在当前光标位置插入文本。
- 空输入框如果正在显示 hint/placeholder，会把提示词视为空内容，避免把“点击输入文本”等提示文案一起写入。
- 不支持直接文本设置的控件会回退到系统剪贴板粘贴。
- 快捷按钮支持启用、停用和排序。
- 自动化脚本支持可视化编辑与长按拖拽排序。
- 脚本当前支持：
  - 插入某个快捷内容；
  - 插入自定义文本；
  - 聚焦下一个输入框，对应 Tab 语义；
  - 触发 IME 回车动作；
  - 等待指定毫秒数。
- 任一自动化步骤失败时立即停止，避免后续内容输入到错误位置。
- Material 3 界面，支持系统动态配色和深色模式。
- Room 本地数据库，无账号、无广告、无遥测、无联网权限。

## 使用方式

1. 安装并打开 QuickBar。
2. 在“快捷栏”页面创建一个或多个快捷按钮。
3. 在“设置”中阅读权限说明并进入系统无障碍设置。
4. 启用“QuickBar 快捷输入服务”。
5. 返回任意应用并聚焦一个文本框。
6. 点击屏幕顶部 QuickBar 中对应的按钮即可插入内容。
7. 如果创建了自动化脚本，点击悬浮栏中的 `⚡` 切换到脚本列表并执行。
8. 点击悬浮栏中的剪贴板图标可开启或关闭临时剪贴板快捷项。
9. 在 ChatGPT 中复制要引用的文本后，点击悬浮栏“引用”按钮即可插入到当前对话；如果系统限制了直接读取剪贴板，请先开启剪贴板快捷项并重新复制。其他应用需要先聚焦目标输入框。

拖动悬浮栏左上角的 QuickBar 图标可以移动整个窗口；短按可以折叠或展开，长按可以重新打开 QuickBar 主界面。右侧关闭图标可以直接隐藏悬浮栏。

## 自动化设计

QuickBar 的公开版本不会尝试伪造任意硬件按键事件。普通第三方 Android 应用即使获得无障碍权限，也没有稳定、通用且适合公开发行的任意按键注入接口。

因此当前版本使用等价的公开无障碍语义：

- `Tab` → 在当前窗口的可编辑节点中聚焦下一个输入框；
- `Enter` → Android 11 及以上优先调用 `ACTION_IME_ENTER`；
- 多行输入框不支持 IME action 时插入换行；
- 单行输入框不支持 IME action 时尝试切换到下一个输入框。

未来可以把 Shizuku 或 root 作为可选扩展，但不会作为基础功能的强制依赖。

## 隐私

QuickBar 的 Android Manifest 不包含 `INTERNET` 权限，并关闭 Android 应用云备份。应用本身不会把快捷文本、脚本或输入框内容上传到任何服务器。

无障碍服务具有较高权限。QuickBar 仅使用它来：

- 创建顶部快捷栏；
- 查找当前获得输入焦点的可编辑控件；
- 在用户主动点击快捷按钮或脚本后执行对应输入动作；
- 在自动化脚本需要时聚焦下一个可编辑控件。
- 在用户主动开启剪贴板快捷项后，监听可获得的复制事件并把普通文本暂存在当前服务进程内；系统标记为敏感的剪贴板内容不会保存。

完整说明见 [PRIVACY.md](PRIVACY.md)。

## 技术栈

- Kotlin 2.0.21
- Jetpack Compose + Material 3
- Room
- Kotlin Coroutines / Flow
- Android AccessibilityService
- `TYPE_ACCESSIBILITY_OVERLAY`
- minSdk 26
- targetSdk / compileSdk 35
- JDK 17

## 项目结构

```text
app/src/main/java/io/github/quickbar/
├── MainActivity.kt
├── data/
│   ├── AppDatabase.kt
│   ├── Models.kt
│   └── QuickBarDao.kt
├── service/
│   ├── InputController.kt
│   ├── OverlayController.kt
│   └── QuickBarAccessibilityService.kt
└── ui/
    ├── AccessibilityUtils.kt
    ├── QuickBarApp.kt
    ├── ScriptEditorScreen.kt
    └── Theme.kt
```

## 构建

当前开发环境：Android SDK 35、Build Tools 35.0.0、JDK 17。

可以直接用 Android Studio 打开仓库并执行 `assembleDebug`。如果本机已有 Gradle 8.10.2，也可以运行：

```bash
gradle :app:assembleDebug
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 已知限制

- 某些自绘控件、游戏、远程桌面、部分 WebView 或未正确实现 Accessibility API 的应用可能无法直接写入。
- `ACTION_SET_TEXT` 不可用时会使用剪贴板作为兼容回退，因此这种情况下系统剪贴板会被 QuickBar 的待输入文本覆盖。
- Android 10 及以上限制后台第三方应用直接读取其他应用的剪贴板，因此剪贴板快捷项会结合系统剪贴板回调和无障碍文本选择事件尽力捕获；某些自绘控件或“复制全文”按钮可能无法被记录。
- “引用”按钮会在用户点击时尝试单次读取剪贴板，失败时仅回退到近两分钟内捕获的剪贴板历史（要求剪贴板快捷项已开启）；无法获取文本时不会插入旧内容。
- “下一个输入框”依赖目标应用暴露的无障碍节点顺序，个别应用的顺序可能与视觉顺序不同。
- 当前没有任意硬件按键注入、坐标点击、条件分支、循环和变量系统。
- 当前没有导入导出功能。

## 参考项目

设计阶段参考了两个开源/公开项目的思路：

- [rrajath/expander](https://github.com/rrajath/expander)：参考其 Android 无障碍文本扩展、Room 与 Material 3 的整体方向。该项目采用 MIT License。
- [chuk-development/plauder-android](https://github.com/chuk-development/plauder-android)：参考其“悬浮 UI + AccessibilityService”的架构思路。QuickBar 未复制该仓库源代码。

QuickBar 的实现代码为独立实现。

## GitHub

项目主页：<https://github.com/V0idream/QuickBar>

## License

MIT License。见 [LICENSE](LICENSE)。

---

**English:** QuickBar is a local-first Android text shortcut and lightweight automation tool built around AccessibilityService. It provides a top overlay for inserting saved snippets and running ordered input workflows without requiring network access.

