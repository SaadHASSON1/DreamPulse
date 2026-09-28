package com.x13labs.dreampulse.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * Per-app language (Android 13+). The system recreates the activity after a change.
 * null = follow the watch language.
 */
object AppLanguage {
    val supported = listOf("ar", "tr", "en")

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun current(context: Context): String? {
        if (!isSupported) return null
        val tags = context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        return tags.substringBefore(',').substringBefore('-').ifEmpty { null }
    }

    fun set(context: Context, tag: String?) {
        if (!isSupported) return
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    /** Each language named in itself, so it can be found whatever the current language. */
    fun nativeName(tag: String): String = java.util.Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it) }
}
