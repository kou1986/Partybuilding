package com.partybuilding.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var slideView: SlideView
    private lateinit var modeBadge: TextView
    private lateinit var slideInfo: TextView
    private lateinit var hintText: TextView
    private lateinit var dataStore: DataStore
    private lateinit var slides: List<SlideData>

    private var currentSlideIndex = 0
    private var mode: Mode = Mode.EDIT
    private val handler = Handler(Looper.getMainLooper())
    private var autoAdvanceRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fullscreen / immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        slideView = findViewById(R.id.slideView)
        modeBadge = findViewById(R.id.modeBadge)
        slideInfo = findViewById(R.id.slideInfo)
        hintText = findViewById(R.id.hintText)

        dataStore = DataStore(this)

        // Load slides
        val jsonText = assets.open("slides.json").bufferedReader().use { it.readText() }
        slides = SlideData.loadAll(jsonText)

        slideView.listener = object : SlideView.Listener {
            override fun onModeHintChanged(mode: Mode, focusedFieldIndex: Int, totalFields: Int) {
                updateHint(mode, focusedFieldIndex, totalFields)
            }
            override fun onSlideDirty() {
                // No-op; DataStore writes are synchronous.
            }
            override fun onEditRequested(fieldIndex: Int, currentText: String, callback: (String) -> Unit) {
                showEditDialog(currentText, callback)
            }
        }

        // Initial mode from settings
        mode = dataStore.defaultMode
        slideView.setMode(mode)
        updateHint(mode, -1, slides.getOrNull(currentSlideIndex)?.textFields?.size ?: 0)
        showSlide(currentSlideIndex)

        slideView.requestFocus()
    }

    // ---- slide navigation ------------------------------------------------

    private fun showSlide(index: Int) {
        if (slides.isEmpty()) return
        currentSlideIndex = ((index % slides.size) + slides.size) % slides.size
        slideView.clearVideoPlayback()
        slideView.showSlide(slides[currentSlideIndex])
        slideInfo.text = "${currentSlideIndex + 1} / ${slides.size}"
        if (mode == Mode.PLAY) scheduleAutoAdvance()
        updateHint(mode, 0, slides[currentSlideIndex].textFields.size)
    }

    private fun scheduleAutoAdvance() {
        autoAdvanceRunnable?.let { handler.removeCallbacks(it) }
        val seconds = dataStore.pageIntervalSeconds
        val r = Runnable {
            showSlide(currentSlideIndex + 1)
        }
        autoAdvanceRunnable = r
        handler.postDelayed(r, seconds * 1000L)
    }

    private fun cancelAutoAdvance() {
        autoAdvanceRunnable?.let { handler.removeCallbacks(it) }
        autoAdvanceRunnable = null
    }

    private fun updateHint(mode: Mode, focusedIndex: Int, total: Int) {
        when (mode) {
            Mode.EDIT -> {
                modeBadge.text = getString(R.string.mode_edit)
                hintText.text = if (total > 0)
                    "编辑模式 · 第 ${focusedIndex + 1}/$total 项 · OK编辑 · ←→换项 · ←/→ 翻页 · 菜单键切模式"
                else
                    "编辑模式 · 本页无文字 · ←/→ 翻页 · 菜单键切模式"
            }
            Mode.PLAY -> {
                modeBadge.text = getString(R.string.mode_play)
                hintText.text = "播放模式 · ${dataStore.pageIntervalSeconds}秒/页 · 菜单键切模式 · 任意键暂停"
            }
        }
    }

    // ---- key handling ----------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_MODE -> {
                toggleMode(); true
            }
            // LEFT/RIGHT: change slides in both modes
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> {
                cancelAutoAdvance(); showSlide(currentSlideIndex - 1); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN -> {
                cancelAutoAdvance(); showSlide(currentSlideIndex + 1); true
            }
            // UP/DOWN in edit mode: move text focus; in play mode: switch to edit mode
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (mode == Mode.PLAY) {
                    cancelAutoAdvance(); toggleMode(); true
                } else { slideView.onKeyDown(keyCode, event); true }
            }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> false
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun toggleMode() {
        cancelAutoAdvance()
        mode = if (mode == Mode.EDIT) Mode.PLAY else Mode.EDIT
        slideView.setMode(mode)
        updateHint(mode, 0, slides[currentSlideIndex].textFields.size)
        if (mode == Mode.PLAY) {
            Toast.makeText(this, R.string.toast_playback_started, Toast.LENGTH_SHORT).show()
            scheduleAutoAdvance()
        } else {
            Toast.makeText(this, R.string.toast_edit_started, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- soft-keyboard edit dialog --------------------------------------

    private fun showEditDialog(current: String, callback: (String) -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        val edit = EditText(this).apply {
            setText(current)
            setSelection(current.length)
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#33000000"))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        container.addView(edit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val dlg = AlertDialog.Builder(this)
            .setTitle("编辑文字")
            .setView(container)
            .setPositiveButton(R.string.hint_save) { _, _ ->
                callback(edit.text.toString())
            }
            .setNegativeButton(R.string.hint_cancel, null)
            .setNeutralButton(R.string.hint_delete) { _, _ ->
                callback("")
            }
            .create()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
        edit.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        cancelAutoAdvance()
    }

    override fun onResume() {
        super.onResume()
        slideView.requestFocus()
        if (mode == Mode.PLAY) scheduleAutoAdvance()
    }

    override fun onDestroy() {
        cancelAutoAdvance()
        slideView.clearVideoPlayback()
        super.onDestroy()
    }
}
