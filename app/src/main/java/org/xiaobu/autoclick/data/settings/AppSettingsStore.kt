package org.xiaobu.autoclick.data.settings

import android.content.Context
import androidx.core.content.edit

const val DEFAULT_THEME_ID = "aurora_purple"

class AppSettingsStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getThemeId(): String {
        return preferences.getString(THEME_ID_KEY, DEFAULT_THEME_ID).orEmpty()
            .ifBlank { DEFAULT_THEME_ID }
    }

    fun saveThemeId(themeId: String) {
        preferences.edit {
            putString(THEME_ID_KEY, themeId.ifBlank { DEFAULT_THEME_ID })
        }
    }

    private companion object {
        private const val PREF_NAME = "app_settings"
        private const val THEME_ID_KEY = "theme_id"
    }
}
