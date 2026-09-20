package io.github.quickbar.ui

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import io.github.quickbar.service.QuickBarAccessibilityService

fun isQuickBarAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, QuickBarAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()

    return enabled
        .split(':')
        .mapNotNull(ComponentName::unflattenFromString)
        .any { it == expected }
}
