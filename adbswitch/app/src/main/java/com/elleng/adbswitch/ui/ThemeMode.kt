package com.elleng.adbswitch.ui

import androidx.annotation.StringRes
import com.elleng.adbswitch.R

enum class ThemeMode(val preferenceValue: String, @StringRes val labelResource: Int) {
    SYSTEM("system", R.string.theme_follow_system),
    LIGHT("light", R.string.theme_light),
    DARK("dark", R.string.theme_dark);

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        @JvmStatic
        fun fromPreference(value: String?): ThemeMode = entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}
