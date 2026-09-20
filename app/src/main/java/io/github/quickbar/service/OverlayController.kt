package io.github.quickbar.service

import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.quickbar.MainActivity
import io.github.quickbar.R
import io.github.quickbar.data.OverlayPreferences
import io.github.quickbar.data.ScriptEntity
import io.github.quickbar.data.SnippetEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OverlayController(
    private val service: AccessibilityService,
    private val onSnippetClick: (SnippetEntity) -> Unit,
    private val onScriptClick: (ScriptEntity) -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val prefs = OverlayPreferences.prefs(service)
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var snippets: List<SnippetEntity> = emptyList()
    private var scripts: List<ScriptEntity> = emptyList()
    private var collapsed = false
    private var scriptMode = false

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                OverlayPreferences.KEY_VISIBLE -> {
                    if (OverlayPreferences.isVisible(service)) show() else hide()
                }
                OverlayPreferences.KEY_ORIENTATION,
                OverlayPreferences.KEY_SPAN_COUNT -> if (OverlayPreferences.isVisible(service)) rebuild()
            }
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun show() {
        if (!OverlayPreferences.isVisible(service)) return
        if (root == null) rebuild()
    }

    fun update(
        snippets: List<SnippetEntity>,
        scripts: List<ScriptEntity>,
    ) {
        this.snippets = snippets
        this.scripts = scripts
        if (scriptMode && scripts.isEmpty()) scriptMode = false
        if (OverlayPreferences.isVisible(service)) rebuild()
    }

    fun destroy() {
        hide()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun hide() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        params = null
    }

    private fun rebuild() {
        if (!OverlayPreferences.isVisible(service)) {
            hide()
            return
        }

        val previous = root
        val view = buildRoot()
        val layoutParams = buildLayoutParams()
        if (previous == null) {
            windowManager.addView(view, layoutParams)
        } else {
            windowManager.removeView(previous)
            windowManager.addView(view, layoutParams)
        }
        root = view
        params = layoutParams
    }

    private fun buildRoot(): LinearLayout {
        val dark = service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val panelColor = if (dark) Color.argb(242, 34, 33, 38) else Color.argb(244, 250, 248, 255)
        val textColor = if (dark) Color.WHITE else Color.rgb(35, 32, 40)
        val buttonColor = if (dark) Color.rgb(74, 68, 87) else Color.rgb(235, 225, 255)
        val accentColor = if (dark) Color.rgb(208, 188, 255) else Color.rgb(103, 80, 164)
        val dangerColor = if (dark) Color.rgb(116, 54, 61) else Color.rgb(255, 218, 214)

        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(8), dp(8), dp(8), dp(8))
            elevation = dp(10).toFloat()
            background = roundedBackground(panelColor, 22f)

            val controls = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
            }

            controls.addView(iconButton(
                iconRes = R.drawable.ic_quickbar_mark,
                backgroundColor = accentColor,
                iconColor = Color.WHITE,
                description = "移动或折叠 QuickBar",
            ).apply {
                attachDragGesture(this)
            })

            if (!collapsed && scripts.isNotEmpty()) {
                controls.addView(pill(if (scriptMode) "文本" else "⚡", buttonColor, textColor).apply {
                    setOnClickListener {
                        scriptMode = !scriptMode
                        rebuild()
                    }
                })
            }

            controls.addView(
                View(service),
                LinearLayout.LayoutParams(0, 1, 1f),
            )

            controls.addView(iconButton(
                iconRes = R.drawable.ic_close_overlay,
                backgroundColor = dangerColor,
                iconColor = textColor,
                description = "关闭悬浮栏",
            ).apply {
                setOnClickListener {
                    OverlayPreferences.setVisible(service, false)
                }
            })

            addView(
                controls,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )

            if (!collapsed) {
                addView(buildItemsScroller(buttonColor, textColor))
            }
        }
    }

    private fun buildItemsScroller(buttonColor: Int, textColor: Int): View {
        val vertical = OverlayPreferences.orientation(service) == OverlayPreferences.ORIENTATION_VERTICAL
        val spanCount = OverlayPreferences.spanCount(service)
        val grid = GridLayout(service).apply {
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            if (vertical) {
                columnCount = spanCount
                orientation = GridLayout.VERTICAL
            } else {
                rowCount = spanCount
                orientation = GridLayout.HORIZONTAL
            }
        }

        if (scriptMode) {
            if (scripts.isEmpty()) {
                grid.addView(label("暂无自动化脚本", textColor))
            } else {
                scripts.forEach { script ->
                    grid.addView(pill("▶ ${script.name}", buttonColor, textColor).apply {
                        setOnClickListener { onScriptClick(script) }
                    })
                }
            }
        } else {
            if (snippets.isEmpty()) {
                grid.addView(label("长按 QB 打开设置", textColor))
            } else {
                snippets.forEach { snippet ->
                    grid.addView(pill(snippet.name, buttonColor, textColor).apply {
                        setOnClickListener { onSnippetClick(snippet) }
                    })
                }
            }
        }

        return if (vertical) {
            ScrollView(service).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(grid)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    verticalContentHeight(spanCount),
                ).apply { topMargin = dp(4) }
            }
        } else {
            HorizontalScrollView(service).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(grid)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4) }
            }
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val metrics = service.resources.displayMetrics
        val vertical = OverlayPreferences.orientation(service) == OverlayPreferences.ORIENTATION_VERTICAL
        val spanCount = OverlayPreferences.spanCount(service)

        val width = when {
            collapsed -> dp(128)
            vertical -> min(
                (metrics.widthPixels * (0.34f + 0.15f * (spanCount - 1))).toInt(),
                (metrics.widthPixels * 0.82f).toInt(),
            )
            else -> (metrics.widthPixels * 0.74f).toInt()
        }

        val safeEdge = dp(8)
        val maxX = max(safeEdge, metrics.widthPixels - width - safeEdge)
        val maxY = max(safeEdge, metrics.heightPixels - dp(96))
        val x = prefs.getInt(OverlayPreferences.KEY_X, dp(12)).coerceIn(safeEdge, maxX)
        val y = prefs.getInt(OverlayPreferences.KEY_Y, dp(72)).coerceIn(safeEdge, maxY)
        OverlayPreferences.setPosition(service, x, y)

        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun attachDragGesture(handle: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var downAt = 0L
        var dragged = false

        handle.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    downAt = SystemClock.uptimeMillis()
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (!dragged && (abs(dx) > dp(5) || abs(dy) > dp(5))) dragged = true
                    if (dragged) {
                        val display = service.resources.displayMetrics
                        val maxX = max(0, display.widthPixels - (root?.width ?: dp(80)))
                        val maxY = max(0, display.heightPixels - (root?.height ?: dp(50)))
                        lp.x = (startX + dx).coerceIn(0, maxX)
                        lp.y = (startY + dy).coerceIn(0, maxY)
                        root?.let { windowManager.updateViewLayout(it, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragged) {
                        OverlayPreferences.setPosition(service, lp.x, lp.y)
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        val heldMs = SystemClock.uptimeMillis() - downAt
                        if (heldMs >= 550L) {
                            val intent = Intent(service, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            service.startActivity(intent)
                        } else {
                            collapsed = !collapsed
                            rebuild()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun verticalContentHeight(spanCount: Int): Int {
        val displayHeight = service.resources.displayMetrics.heightPixels
        val desired = when (spanCount) {
            1 -> (displayHeight * 0.48f).toInt()
            2 -> (displayHeight * 0.42f).toInt()
            else -> (displayHeight * 0.36f).toInt()
        }
        return desired.coerceAtLeast(dp(160))
    }

    private fun pill(text: String, backgroundColor: Int, textColor: Int): TextView {
        return TextView(service).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 14f
            gravity = Gravity.CENTER
            isSingleLine = true
            minHeight = dp(38)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = roundedBackground(backgroundColor, 18f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dp(6)
                bottomMargin = dp(6)
            }
        }
    }

    private fun iconButton(
        iconRes: Int,
        backgroundColor: Int,
        iconColor: Int,
        description: String,
    ): ImageButton {
        return ImageButton(service).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(iconColor)
            contentDescription = description
            background = roundedBackground(backgroundColor, 18f)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            elevation = 0f
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginEnd = dp(6)
            }
        }
    }

    private fun label(text: String, textColor: Int): TextView {
        return TextView(service).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 13f
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()
}
