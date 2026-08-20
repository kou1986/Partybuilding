package com.partybuilding.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Wraps SharedPreferences for editable text values and playback settings.
 *
 * Text edits are stored as `<slideNum>:<fieldId>` -> `<new text>` so we can
 * restore defaults simply by clearing all matching keys.
 */
class DataStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getText(slideNum: Int, fieldId: String): String? = prefs.getString(key(slideNum, fieldId), null)

    fun setText(slideNum: Int, fieldId: String, value: String) {
        prefs.edit().putString(key(slideNum, fieldId), value).apply()
    }

    /** Drop every edited text, reverting slides to their PPTX defaults. */
    fun resetAllText() {
        val toRemove = prefs.all.keys.filter { it.startsWith(TEXT_PREFIX) }
        if (toRemove.isEmpty()) return
        prefs.edit().also { e -> toRemove.forEach { e.remove(it) } }.apply()
    }

    var pageIntervalSeconds: Int
        get() = prefs.getInt(KEY_PAGE_INTERVAL, 8).coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        set(value) { prefs.edit().putInt(KEY_PAGE_INTERVAL, value.coerceIn(MIN_INTERVAL, MAX_INTERVAL)).apply() }

    var defaultMode: Mode
        get() = Mode.fromKey(prefs.getString(KEY_DEFAULT_MODE, null))
        set(value) { prefs.edit().putString(KEY_DEFAULT_MODE, value.key).apply() }

    private fun key(slideNum: Int, fieldId: String) = "$TEXT_PREFIX$slideNum:$fieldId"

    companion object {
        private const val PREFS_NAME = "party_building_prefs"
        private const val TEXT_PREFIX = "text_"
        private const val KEY_PAGE_INTERVAL = "page_interval"
        private const val KEY_DEFAULT_MODE = "default_mode"
        const val MIN_INTERVAL = 3
        const val MAX_INTERVAL = 60
    }
}

enum class Mode(val key: String) {
    EDIT("edit"), PLAY("play");
    companion object {
        fun fromKey(k: String?): Mode = entries.firstOrNull { it.key == k } ?: EDIT
    }
}
