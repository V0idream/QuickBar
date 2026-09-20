package io.github.quickbar.data

import android.content.Context
import android.content.SharedPreferences

object OverlayPreferences {
    const val PREFS_NAME = "overlay_preferences"
    const val KEY_VISIBLE = "visible"
    const val KEY_ORIENTATION = "orientation"
    const val KEY_SPAN_COUNT = "span_count"
    const val KEY_X = "x"
    const val KEY_Y = "y"

    const val ORIENTATION_HORIZONTAL = "horizontal"
    const val ORIENTATION_VERTICAL = "vertical"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VISIBLE, true)

    fun setVisible(context: Context, visible: Boolean) {
        prefs(context).edit().putBoolean(KEY_VISIBLE, visible).apply()
    }

    fun orientation(context: Context): String =
        prefs(context).getString(KEY_ORIENTATION, ORIENTATION_HORIZONTAL)
            ?: ORIENTATION_HORIZONTAL

    fun setOrientation(context: Context, orientation: String) {
        prefs(context).edit().putString(KEY_ORIENTATION, orientation).apply()
    }

    fun spanCount(context: Context): Int =
        prefs(context).getInt(KEY_SPAN_COUNT, 1).coerceIn(1, 3)

    fun setSpanCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_SPAN_COUNT, count.coerceIn(1, 3)).apply()
    }

    fun x(context: Context): Int = prefs(context).getInt(KEY_X, 12)
    fun y(context: Context): Int = prefs(context).getInt(KEY_Y, 80)

    fun setPosition(context: Context, x: Int, y: Int) {
        prefs(context).edit()
            .putInt(KEY_X, x)
            .putInt(KEY_Y, y)
            .apply()
    }
}
