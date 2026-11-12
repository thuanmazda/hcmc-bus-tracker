package com.hcmcbustracker.core.model

/**
 * Visual theme mode selection for the application.
 */
enum class AppThemeMode(val displayName: String) {
    SYSTEM("Theo hệ thống"),
    LIGHT("Giao diện sáng"),
    DARK("Giao diện tối")
}

/**
 * User personalization and runtime configuration preferences.
 */
data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val pollingIntervalSeconds: Int = 15,
    val notificationsEnabled: Boolean = true
)
