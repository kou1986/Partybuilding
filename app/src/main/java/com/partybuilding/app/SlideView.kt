package com.partybuilding.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.VideoView
import kotlin.math.max
import kotlin.math.min

/**
 * Renders a single slide: background image, decorative pictures, looping muted video,
 * and editable text fields positioned at exact PPT coordinates (EMU -> view space).
 *
 * Two modes:
 *   EDIT  - focus moves between text fields; pressing OK opens a soft keyboard to edit.
 *   PLAY  - the host activity flips slides on a timer; this view just renders.
 */
class SlideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun onModeHintChanged(mode: Mode, focusedFieldIndex: Int, totalFields: Int)
        fun onSlideDirty()
        fun onEditRequested(fieldIndex: Int, currentText: String, callback: (String) -> Unit)
    }

    var listener: Listener? = null

    // ---- runtime state --------------------------------------------------

    private var slide: SlideData? = null
    private var mode: Mode = Mode.EDIT
    private var focusedFieldIndex: Int = -1

    // Background image (sized to canvas).
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var bgDrawable: android.graphics.drawable.Drawable? = null

    // Pictures list (rendered after background).
    private val picRects = mutableListOf<RectF>()

    // Video view for the (at most one) video on this slide.
    private var videoView: VideoView? = null
    private var videoPlayer: MediaPlayer? = null

    // Edit overlay: a positioned EditText shown while editing.
    private var editOverlay: EditText? = null

    // ---- paints ----------------------------------------------------------

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
    }
    private val focusBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#FFFFC000")
    }
    private val focusFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33000000")
    }

    // ---- constants -------------------------------------------------------

    /** Intrinsic canvas size of slides in pixels (1280 x 720, matching 16:9 PPTs). */
    private val SLIDE_W = 1280f
    private val SLIDE_H = 720f

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
    }

    // ---- public api ------------------------------------------------------

    fun setMode(mode: Mode) {
        this.mode = mode
        if (mode == Mode.PLAY) {
            focusedFieldIndex = -1
            hideEditOverlay()
            clearFocus()
        } else {
            requestFocus()
            if (slide?.textFields?.isNotEmpty() == true && focusedFieldIndex < 0) {
                focusedFieldIndex = 0
            }
        }
        listener?.onModeHintChanged(mode, focusedFieldIndex, slide?.textFields?.size ?: 0)
        invalidate()
    }

    fun showSlide(data: SlideData?) {
        slide = data
        focusedFieldIndex = if (mode == Mode.EDIT && data?.textFields?.isNotEmpty() == true) 0 else -1
        rebuildChildren()
        startVideoIfAny()
        listener?.onModeHintChanged(mode, focusedFieldIndex, data?.textFields?.size ?: 0)
        invalidate()
    }

    fun clearVideoPlayback() {
        videoView?.stopPlayback()
        videoPlayer?.release()
        videoPlayer = null
    }

    // ---- rendering -------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutBackground()
        layoutPictures()
        layoutVideo()
        layoutFocusedOverlay()
        startVideoIfAny()
    }

    private fun layoutBackground() {
        val s = slide ?: return
        val bgSrc = s.background ?: return
        val d = SlideLoader.getImage(context, bgSrc.removePrefix("../")) ?: return
        bgDrawable = d
        // Background covers entire slide canvas; we scale preserving aspect.
        val (dx, dy, dw, dh) = fitInto(canvasW(), canvasH(), d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        d.setBounds(dx.toInt(), dy.toInt(), (dx + dw).toInt(), (dy + dh).toInt())
    }

    private fun layoutPictures() {
        picRects.clear()
        val s = slide ?: return
        val cw = canvasW(); val ch = canvasH()
        for (pic in s.pictures) {
            val r = emuRectToView(pic.xEmu, pic.yEmu, pic.cxEmu, pic.cyEmu, cw, ch)
            picRects.add(r)
        }
    }

    private fun layoutVideo() {
        val s = slide ?: return
        val v = s.videos.firstOrNull() ?: run {
            removeVideoView(); return
        }
        val r = emuRectToView(v.xEmu, v.yEmu, v.cxEmu, v.cyEmu, canvasW(), canvasH())
        if (videoView == null) {
            val vv = VideoView(context)
            // No black background - the slide's own background will show through.
            addView(vv, FrameLayout.LayoutParams(1, 1))
            videoView = vv
        }
        videoView?.layoutParams = FrameLayout.LayoutParams(r.width().toInt(), r.height().toInt()).apply {
            leftMargin = r.left.toInt(); topMargin = r.top.toInt()
        }
    }

    private fun startVideoIfAny() {
        val s = slide ?: return
        val v = s.videos.firstOrNull() ?: return
        val file = SlideLoader.videoFile(context, v.src) ?: run {
            android.util.Log.w("SlideView", "Video file missing: ${v.src}")
            return
        }
        val vv = videoView ?: return
        android.util.Log.i("SlideView", "Starting video: $file")
        vv.setVideoURI(Uri.fromFile(file))
        vv.setOnPreparedListener { mp ->
            android.util.Log.i("SlideView", "Video prepared")
            videoPlayer = mp
            mp.isLooping = true
            mp.setVolume(0f, 0f) // muted per requirement
            mp.setScreenOnWhilePlaying(true)
            mp.start()
        }
        vv.setOnErrorListener { _, what, extra ->
            android.util.Log.w("SlideView", "Video error what=$what extra=$extra for ${file.absolutePath}")
            true
        }
        vv.setOnInfoListener { _, what, extra ->
            android.util.Log.i("SlideView", "Video info what=$what extra=$extra")
            false
        }
    }

    private fun removeVideoView() {
        videoView?.let { removeView(it) }
        videoView = null
        videoPlayer?.release(); videoPlayer = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)
        // Pictures that belong UNDER the video are drawn here.
        drawPicturesUnderVideo(canvas)
        drawText(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Children (VideoView / EditText) draw first.
        super.dispatchDraw(canvas)
        // Pictures that belong ON TOP of the video (z-order) draw here.
        drawPicturesOverVideo(canvas)
        // Focus highlight on top of everything.
        drawFocusHighlight(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        bgDrawable?.draw(canvas)
    }

    private fun drawPictures(canvas: Canvas) {
        drawPicturesUnderVideo(canvas)
        drawPicturesOverVideo(canvas)
    }

    private fun drawPicturesUnderVideo(canvas: Canvas) {
        val s = slide ?: return
        val cw = canvasW(); val ch = canvasH()
        for (pic in s.pictures) {
            if (pic.onTopOfVideo) continue
            val d = SlideLoader.getImage(context, pic.src.removePrefix("../")) ?: continue
            val r = emuRectToView(pic.xEmu, pic.yEmu, pic.cxEmu, pic.cyEmu, cw, ch)
            // Use a callback draw so we don't mutate the cached drawable's bounds.
            d.setBounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            d.draw(canvas)
        }
    }

    private fun drawPicturesOverVideo(canvas: Canvas) {
        val s = slide ?: return
        val cw = canvasW(); val ch = canvasH()
        for (pic in s.pictures) {
            if (!pic.onTopOfVideo) continue
            val d = SlideLoader.getImage(context, pic.src.removePrefix("../")) ?: continue
            val r = emuRectToView(pic.xEmu, pic.yEmu, pic.cxEmu, pic.cyEmu, cw, ch)
            d.setBounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            d.draw(canvas)
        }
    }

    private fun drawFocusHighlight(canvas: Canvas) {
        if (mode != Mode.EDIT) return
        val s = slide ?: return
        if (focusedFieldIndex < 0 || focusedFieldIndex >= s.textFields.size) return
        val f = s.textFields[focusedFieldIndex]
        val r = emuRectToView(f.xEmu, f.yEmu, f.cxEmu, f.cyEmu, canvasW(), canvasH())
        canvas.drawRect(r, focusFillPaint)
        canvas.drawRect(r, focusBorderPaint)
    }

    // ---- text drawing ----------------------------------------------------

    private fun drawText(canvas: Canvas) {
        val s = slide ?: return
        for ((i, field) in s.textFields.withIndex()) {
            val r = emuRectToView(field.xEmu, field.yEmu, field.cxEmu, field.cyEmu, canvasW(), canvasH())
            // Slide 2 (组织架构): use yellow text on top of the ribbon GIF
            val forceColor = if (s.slideNum == 2) Color.parseColor("#FFE600") else null
            val forceBold = (s.slideNum == 2)
            drawField(canvas, field, r, forceColor = forceColor, forceBold = forceBold)
        }
    }

    private fun drawField(canvas: Canvas, field: TextField, r: RectF, forceColor: Int? = null, forceBold: Boolean = false) {
        val text = DataStore(context).getText(slide?.slideNum ?: 0, field.id) ?: field.defaultText
        if (text.isEmpty()) return
        // Scale text size with the canvas so it looks proportional to the PPT.
        // PPT was 1280x720. On larger views, text scales up too.
        val scaleFactor = canvasW() / SLIDE_W

        // Apply insets (text padding from box edges)
        val insetL = field.insets["l"] ?: 0f
        val insetT = field.insets["t"] ?: 0f
        val insetR = field.insets["r"] ?: 0f
        val insetB = field.insets["b"] ?: 0f
        val innerLeft = r.left + insetL
        val innerTop = r.top + insetT
        val innerWidth = r.width() - insetL - insetR
        val innerHeight = r.height() - insetT - insetB

        // Get paragraphs to render
        val paragraphs = if (field.paragraphs.isNotEmpty()) field.paragraphs
                         else listOf(Paragraph(align = field.align, text = field.defaultText, runs = field.runs))

        // Pre-compute each paragraph's height (max run size)
        val paraMetrics = paragraphs.map { para ->
            var maxSize = 0f
            for (run in para.runs) {
                if (run.size > 0) {
                    val sizePx = (run.size / 100f) * (96f / 72f) * scaleFactor
                    if (sizePx > maxSize) maxSize = sizePx
                }
            }
            if (maxSize == 0f) maxSize = 9f * (96f / 72f) * scaleFactor  // default
            maxSize
        }
        val lineSpacing = 1.2f
        val totalHeight = paraMetrics.sum().let { it + (paragraphs.size - 1) * it * (lineSpacing - 1f) / paragraphs.size }

        // Vertical anchor (t = top, ctr = middle, b = bottom)
        val firstBaselineY = when (field.anchor) {
            "ctr" -> innerTop + (innerHeight - totalHeight) / 2f
            "b" -> innerTop + (innerHeight - totalHeight)
            else -> innerTop  // top
        }

        var y = firstBaselineY
        for ((idx, para) in paragraphs.withIndex()) {
            val lineHeight = paraMetrics[idx]
            val baselineY = y + lineHeight
            // Measure line width for horizontal alignment
            var lineWidth = 0f
            for (run in para.runs) {
                applyRunStyle(run, forceColor, forceBold)
                val sizePx = (run.size / 100f) * (96f / 72f) * scaleFactor
                if (sizePx > 0f) textPaint.textSize = sizePx
                lineWidth += textPaint.measureText(run.text)
                textPaint.color = Color.WHITE
                textPaint.typeface = Typeface.DEFAULT
            }
            val startX = when (para.align) {
                "ctr" -> innerLeft + (innerWidth - lineWidth) / 2f
                "r" -> innerLeft + (innerWidth - lineWidth)
                else -> innerLeft  // left
            }
            var cursorX = startX
            for (run in para.runs) {
                applyRunStyle(run, forceColor, forceBold)
                val sizePx = (run.size / 100f) * (96f / 72f) * scaleFactor
                if (sizePx > 0f) textPaint.textSize = sizePx
                canvas.drawText(run.text, cursorX, baselineY, textPaint)
                cursorX += textPaint.measureText(run.text)
                textPaint.color = Color.WHITE
                textPaint.typeface = Typeface.DEFAULT
            }
            y += lineHeight * lineSpacing
        }
    }

    private fun applyRunStyle(run: TextRun, forceColor: Int? = null, forceBold: Boolean = false) {
        if (forceColor != null) {
            textPaint.color = forceColor
        } else {
            textPaint.color = when (run.color) {
                "scheme:bg1" -> Color.parseColor("#B30000")
                "scheme:bg2" -> Color.parseColor("#E7E6E6")
                "scheme:tx1" -> Color.BLACK
                "scheme:tx2" -> Color.parseColor("#44546A")
                null -> Color.parseColor("#B30000")
                else -> run.color?.let { runCatching { Color.parseColor(it) }.getOrNull() }
                    ?: Color.parseColor("#B30000")
            }
        }
        val isBold = run.bold || forceBold
        val family = run.font?.takeIf { it.isNotEmpty() }
        if (family != null && (family.contains("雅黑") || family.contains("黑体"))) {
            textPaint.typeface = if (isBold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                                 else Typeface.SANS_SERIF
        } else {
            textPaint.typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    // ---- input handling --------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = slide
        return when (keyCode) {
            // LEFT/RIGHT/PAGE_UP/PAGE_DOWN are handled by MainActivity for slide navigation.
            // Don't consume them here, let the activity handle.
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> false
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (mode == Mode.EDIT) moveFocus(-numColumns()) else false
                listener?.onModeHintChanged(mode, focusedFieldIndex, s?.textFields?.size ?: 0)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (mode == Mode.EDIT) moveFocus(numColumns()) else false
                listener?.onModeHintChanged(mode, focusedFieldIndex, s?.textFields?.size ?: 0)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (mode == Mode.EDIT) {
                    openEditOverlay()
                }
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (editOverlay != null) {
                    hideEditOverlay(); true
                } else false
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mode != Mode.EDIT) return false
        if (event.action != MotionEvent.ACTION_UP) return super.onTouchEvent(event)
        val s = slide ?: return false
        val tapped = findFieldAt(event.x, event.y)
        if (tapped >= 0) {
            focusedFieldIndex = tapped
            listener?.onModeHintChanged(mode, focusedFieldIndex, s.textFields.size)
            openEditOverlay()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun findFieldAt(x: Float, y: Float): Int {
        val s = slide ?: return -1
        val cw = canvasW(); val ch = canvasH()
        for ((i, f) in s.textFields.withIndex()) {
            val r = emuRectToView(f.xEmu, f.yEmu, f.cxEmu, f.cyEmu, cw, ch)
            if (r.contains(x, y)) return i
        }
        return -1
    }

    private fun moveFocus(delta: Int) {
        val s = slide ?: return
        val n = s.textFields.size
        if (n == 0) return
        focusedFieldIndex = ((focusedFieldIndex + delta) % n + n) % n
        invalidate()
    }

    private fun numColumns(): Int {
        // Estimate horizontal grouping: 33 text fields on a table -> assume ~6 cols.
        return 6
    }

    // ---- edit overlay ----------------------------------------------------

    private fun openEditOverlay() {
        val s = slide ?: return
        if (focusedFieldIndex < 0 || focusedFieldIndex >= s.textFields.size) return
        val field = s.textFields[focusedFieldIndex]
        val current = DataStore(context).getText(s.slideNum, field.id) ?: field.defaultText
        listener?.onEditRequested(focusedFieldIndex, current) { newText ->
            DataStore(context).setText(s.slideNum, field.id, newText)
            invalidate()
            listener?.onSlideDirty()
        }
    }

    fun showSoftKeyboardEdit(initial: String, commit: (String) -> Unit) {
        hideEditOverlay()
        val s = slide ?: return
        if (focusedFieldIndex < 0 || focusedFieldIndex >= s.textFields.size) return
        val field = s.textFields[focusedFieldIndex]
        val r = emuRectToView(field.xEmu, field.yEmu, field.cxEmu, field.cyEmu, canvasW(), canvasH())
        val edit = EditText(context).apply {
            setText(initial)
            setSelection(initial.length)
            setTextColor(Color.parseColor("#FFFFC000"))
            setBackgroundColor(Color.parseColor("#CC000000"))
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commit(text.toString()); hideEditOverlay(); true
                } else false
            }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    hideEditOverlay(); true
                } else false
            }
        }
        val lp = FrameLayout.LayoutParams(r.width().toInt(), max(80, r.height().toInt())).apply {
            leftMargin = r.left.toInt(); topMargin = r.top.toInt()
            gravity = Gravity.TOP or Gravity.START
        }
        addView(edit, lp)
        editOverlay = edit
        edit.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideEditOverlay() {
        editOverlay?.let {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
            removeView(it)
        }
        editOverlay = null
        invalidate()
    }

    private fun layoutFocusedOverlay() {
        editOverlay?.let {
            val s = slide ?: return
            if (focusedFieldIndex < 0) return
            val f = s.textFields[focusedFieldIndex]
            val r = emuRectToView(f.xEmu, f.yEmu, f.cxEmu, f.cyEmu, canvasW(), canvasH())
            it.layout(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt() + 80)
        }
    }

    // ---- coordinate helpers ---------------------------------------------

    private fun canvasW(): Float = width.toFloat().takeIf { it > 0 } ?: SLIDE_W
    private fun canvasH(): Float = height.toFloat().takeIf { it > 0 } ?: SLIDE_H

    private fun emuRectToView(xEmu: Int, yEmu: Int, cxEmu: Int, cyEmu: Int, viewW: Float, viewH: Float): RectF {
        val scale = min(viewW / SLIDE_W, viewH / SLIDE_H)
        val offsetX = (viewW - SLIDE_W * scale) / 2f
        val offsetY = (viewH - SLIDE_H * scale) / 2f
        val left = offsetX + xEmu / 914400f * 96f * scale
        val top = offsetY + yEmu / 914400f * 96f * scale
        val right = left + cxEmu / 914400f * 96f * scale
        val bottom = top + cyEmu / 914400f * 96f * scale
        return RectF(left, top, right, bottom)
    }

    private fun fitInto(viewW: Float, viewH: Float, srcW: Float, srcH: Float): FloatArray {
        val scale = max(viewW / srcW, viewH / srcH)
        val dw = srcW * scale
        val dh = srcH * scale
        val dx = (viewW - dw) / 2f
        val dy = (viewH - dh) / 2f
        return floatArrayOf(dx, dy, dw, dh)
    }

    private fun rebuildChildren() {
        removeAllViews()
        videoView = null
        layoutBackground()
        layoutPictures()
        layoutVideo()
        startVideoIfAny()
    }
}
