package com.partybuilding.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Data classes representing a slide's structure extracted from PPTX.
 *
 * Coordinates are stored in EMU (English Metric Units): 914400 EMU = 1 inch = 96 px.
 * For rendering, [SlideView] converts to view-space coordinates using the slide's
 * intrinsic canvas size (1280 x 720 px) and the actual view size.
 */
data class TextRun(
    val text: String,
    /** font size in hundredths of a point (e.g. 900 = 9 pt). */
    val size: Float,
    val bold: Boolean,
    val font: String?,
    /** color hex string (#RRGGBB) or scheme:N name. */
    val color: String?,
)

data class TextField(
    val id: String,
    val name: String,
    val xEmu: Int,
    val yEmu: Int,
    val cxEmu: Int,
    val cyEmu: Int,
    val defaultText: String,
    val runs: List<TextRun>,
    /** Vertical anchor in the text box: 't' (top), 'ctr' (middle), 'b' (bottom). */
    val anchor: String = "t",
    /** Horizontal alignment for paragraphs: 'l', 'ctr', 'r'. */
    val align: String = "l",
    /** Inset padding from each side of the text box, in view-space pixels. */
    val insets: Map<String, Float> = emptyMap(),
    /** Multi-paragraph rendering: list of runs per paragraph. */
    val paragraphs: List<Paragraph> = emptyList(),
)

data class Paragraph(
    val align: String = "l",
    val text: String = "",
    val runs: List<TextRun> = emptyList(),
)

data class MediaItem(
    val id: String,
    val name: String,
    val xEmu: Int,
    val yEmu: Int,
    val cxEmu: Int,
    val cyEmu: Int,
    /** Path relative to assets/, e.g. "media/image3.png" or "media/media1.mp4" */
    val src: String,
    val loop: Boolean = false,
    val mute: Boolean = false,
    /** True if this picture should be drawn on top of the video (z-order from PPT). */
    val onTopOfVideo: Boolean = false,
)

data class SlideData(
    val slideNum: Int,
    val background: String?,
    val pictures: List<MediaItem>,
    val videos: List<MediaItem>,
    val textFields: List<TextField>,
) {
    companion object {
        fun fromJson(json: JSONObject): SlideData {
            val pics = json.optJSONArray("pictures").toMediaItems()
            val vids = json.optJSONArray("videos").toMediaItems()
            val texts = json.optJSONArray("text_fields").toTextFields()
            return SlideData(
                slideNum = json.optInt("slide_num"),
                background = json.optString("background").takeIf { it.isNotEmpty() && it != "null" },
                pictures = pics,
                videos = vids,
                textFields = texts,
            )
        }

        fun loadAll(jsonText: String): List<SlideData> {
            val arr = JSONArray(jsonText)
            val out = ArrayList<SlideData>(arr.length())
            for (i in 0 until arr.length()) {
                out.add(fromJson(arr.getJSONObject(i)))
            }
            return out
        }

        private fun JSONArray?.toMediaItems(): List<MediaItem> {
            if (this == null) return emptyList()
            val list = ArrayList<MediaItem>(length())
            for (i in 0 until length()) {
                val o = getJSONObject(i)
                list.add(
                    MediaItem(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        xEmu = o.optInt("x_emu"),
                        yEmu = o.optInt("y_emu"),
                        cxEmu = o.optInt("cx_emu"),
                        cyEmu = o.optInt("cy_emu"),
                        src = o.optString("src"),
                        loop = o.optBoolean("loop", false),
                        mute = o.optBoolean("mute", false),
                        onTopOfVideo = o.optBoolean("on_top_of_video", false),
                    )
                )
            }
            return list
        }

        private fun JSONArray?.toTextFields(): List<TextField> {
            if (this == null) return emptyList()
            val list = ArrayList<TextField>(length())
            for (i in 0 until length()) {
                val o = getJSONObject(i)
                val runsArr = o.optJSONArray("runs")
                val runs = if (runsArr != null) {
                    ArrayList<TextRun>(runsArr.length()).apply {
                        for (j in 0 until runsArr.length()) {
                            val r = runsArr.getJSONObject(j)
                            add(
                                TextRun(
                                    text = r.optString("text"),
                                    size = (r.optInt("sz", 900).toFloat()),
                                    bold = r.optBoolean("bold", false),
                                    font = r.optString("font").takeIf { it.isNotEmpty() && it != "null" },
                                    color = r.optString("color").takeIf { it.isNotEmpty() && it != "null" },
                                )
                            )
                        }
                    }
                } else emptyList()
                // Parse insets map
                val insetsObj = o.optJSONObject("insets")
                val insets = mutableMapOf<String, Float>()
                if (insetsObj != null) {
                    for (k in listOf("l", "t", "r", "b")) {
                        if (insetsObj.has(k)) insets[k] = insetsObj.optDouble(k).toFloat()
                    }
                }
                // Parse paragraphs
                val paragraphsArr = o.optJSONArray("paragraphs")
                val paragraphs = if (paragraphsArr != null) {
                    ArrayList<Paragraph>(paragraphsArr.length()).apply {
                        for (j in 0 until paragraphsArr.length()) {
                            val pObj = paragraphsArr.getJSONObject(j)
                            val pRuns = pObj.optJSONArray("runs")?.let { pArr ->
                                ArrayList<TextRun>(pArr.length()).apply {
                                    for (k in 0 until pArr.length()) {
                                        val r = pArr.getJSONObject(k)
                                        add(TextRun(
                                            text = r.optString("text"),
                                            size = r.optInt("sz", 900).toFloat(),
                                            bold = r.optBoolean("bold", false),
                                            font = r.optString("font").takeIf { it.isNotEmpty() && it != "null" },
                                            color = r.optString("color").takeIf { it.isNotEmpty() && it != "null" },
                                        ))
                                    }
                                }
                            } ?: emptyList()
                            add(Paragraph(
                                align = pObj.optString("align", "l"),
                                text = pObj.optString("text"),
                                runs = pRuns,
                            ))
                        }
                    }
                } else emptyList()
                list.add(
                    TextField(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        xEmu = o.optInt("x_emu"),
                        yEmu = o.optInt("y_emu"),
                        cxEmu = o.optInt("cx_emu"),
                        cyEmu = o.optInt("cy_emu"),
                        defaultText = o.optString("text"),
                        runs = runs,
                        anchor = o.optString("anchor", "t"),
                        align = o.optString("align", "l"),
                        insets = insets,
                        paragraphs = paragraphs,
                    )
                )
            }
            return list
        }
    }
}
