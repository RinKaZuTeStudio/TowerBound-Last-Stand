package com.towerbound.laststand.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.towerbound.laststand.R

/**
 * Reusable programmatic widgets shared by all meta screens (shop, heroes,
 * quests, settings...). Everything is drawn procedurally — no image assets.
 */
object Widgets {

    /** Rounded dark panel used as a card/list container. */
    fun panel(context: Context, bgColor: Int = UiKit.cBgPanel, cornerDp: Float = 14f): LinearLayout {
        val ll = LinearLayout(context)
        ll.orientation = LinearLayout.VERTICAL
        ll.setPadding(UiKit.dp(context, 14f), UiKit.dp(context, 12f), UiKit.dp(context, 14f), UiKit.dp(context, 12f))
        ll.background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = UiKit.dp(context, cornerDp).toFloat()
            setStroke(UiKit.dp(context, 1f), Color.argb(70, 255, 255, 255))
        }
        return ll
    }

    /** Section header with accent underline. */
    fun sectionHeader(context: Context, label: String, color: Int = UiKit.cAccent): TextView {
        val tv = UiKit.text(context, label, 17f, color, bold = true)
        tv.letterSpacing = 0.06f
        tv.gravity = Gravity.START
        return tv
    }

    /** Gold/gems balance strip (icon dot + amount). */
    fun currencyBar(context: Context): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val gold = currencyChip(context, UiKit.cGold)
        val gems = currencyChip(context, UiKit.cGem)
        row.addView(gold, LinearLayout.LayoutParams(UiKit.dp(context, 96f), UiKit.dp(context, 30f)).apply {
            marginEnd = UiKit.dp(context, 10f)
        })
        row.addView(gems, LinearLayout.LayoutParams(UiKit.dp(context, 96f), UiKit.dp(context, 30f)))
        row.setTag(R.id.tag_gold_chip, gold)
        row.setTag(R.id.tag_gem_chip, gems)
        return row
    }

    private fun currencyChip(context: Context, color: Int): TextView {
        val tv = UiKit.text(context, "0", 14f, UiKit.cText)
        tv.gravity = Gravity.CENTER
        tv.background = GradientDrawable().apply {
            setColor(Color.argb(60, 255, 255, 255))
            cornerRadius = UiKit.dp(context, 15f).toFloat()
            setStroke(UiKit.dp(context, 1f), color)
        }
        tv.setPadding(UiKit.dp(context, 10f), 0, UiKit.dp(context, 10f), 0)
        return tv
    }

    /** Refresh currency chip amounts. */
    fun updateCurrency(bar: LinearLayout, gold: Long, gems: Long) {
        (bar.getTag(R.id.tag_gold_chip) as? TextView)?.text = formatGold(gold)
        (bar.getTag(R.id.tag_gem_chip) as? TextView)?.text = gems.toString()
    }

    fun formatGold(v: Long): String = when {
        v >= 1_000_000 -> String.format("%.1fM", v / 1_000_000.0)
        v >= 10_000 -> "${v / 1000}k"
        else -> v.toString()
    }

    /** Circular portrait view for hero cards. */
    class PortraitView(context: Context) : View(context) {
        var bitmap: Bitmap? = null
        var ringColor: Int = UiKit.cPrimary
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            val bmp = bitmap ?: return
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val r = minOf(w, h) / 2f - UiKit.dp(context, 3f)

            // backdrop
            paint.shader = LinearGradient(0f, 0f, 0f, h, Color.argb(50, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, paint)
            paint.shader = null

            // clipped circular portrait
            canvas.save()
            path.reset()
            path.addCircle(cx, cy, r, Path.Direction.CW)
            canvas.clipPath(path)
            val scale = maxOf(w / bmp.width.toFloat(), h / bmp.height.toFloat())
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            canvas.drawBitmap(bmp, cx - dw / 2f, cy - dh / 2f, paint)
            canvas.restore()

            // ring
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = UiKit.dp(context, 2.5f).toFloat()
            paint.color = ringColor
            canvas.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.FILL
        }
    }

    /** Horizontal progress bar with label overlay slot. */
    class BarView(context: Context) : View(context) {
        var fraction: Float = 0f
        var fillColor: Int = UiKit.cAccent
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val h = height.toFloat()
            val w = width.toFloat()
            paint.color = Color.argb(80, 0, 0, 0)
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
            paint.color = fillColor
            rect.set(0f, 0f, w * fraction.coerceIn(0f, 1f), h)
            canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
        }
    }

    /** Label + switch row for settings. */
    fun toggleRow(context: Context, label: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, UiKit.dp(context, 6f), 0, UiKit.dp(context, 6f))

        val tv = UiKit.text(context, label, 15f, UiKit.cText, bold = false)
        tv.gravity = Gravity.START
        row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val sw = Switch(context)
        sw.isChecked = initial
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        row.addView(sw, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return row
    }

    /** Label + seekbar row for settings. */
    fun sliderRow(context: Context, label: String, initial: Float, onChange: (Float) -> Unit): LinearLayout {
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, UiKit.dp(context, 6f), 0, UiKit.dp(context, 6f))

        val tv = UiKit.text(context, "$label  ${(initial * 100).toInt()}%", 14f, UiKit.cText, bold = false)
        tv.gravity = Gravity.START
        col.addView(tv)

        val seek = SeekBar(context)
        seek.max = 100
        seek.progress = (initial * 100).toInt().coerceIn(0, 100)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                tv.text = "$label  $value%"
                if (fromUser) onChange(value / 100f)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        col.addView(seek, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return col
    }

    /** A vertical scroll container for screen bodies. */
    fun scroll(context: Context, child: View): ScrollView {
        val sv = ScrollView(context)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(child, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        return sv
    }

    /** Horizontal pill selector (e.g. quality/language options). */
    fun choiceRow(context: Context, options: List<String>, initialIndex: Int, onPick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        options.forEachIndexed { i, opt ->
            val tv = UiKit.text(context, opt, 13f, if (i == initialIndex) UiKit.cText else UiKit.cTextDim)
            tv.background = GradientDrawable().apply {
                setColor(if (i == initialIndex) Color.argb(60, 124, 77, 255) else Color.argb(35, 255, 255, 255))
                cornerRadius = UiKit.dp(context, 14f).toFloat()
                setStroke(UiKit.dp(context, 1f), if (i == initialIndex) UiKit.cPrimary else Color.argb(50, 255, 255, 255))
            }
            tv.setPadding(UiKit.dp(context, 14f), UiKit.dp(context, 7f), UiKit.dp(context, 14f), UiKit.dp(context, 7f))
            tv.isClickable = true
            tv.setOnClickListener {
                for (j in 0 until row.childCount) {
                    val other = row.getChildAt(j) as TextView
                    other.setTextColor(UiKit.cTextDim)
                    (other.background as GradientDrawable).apply {
                        setColor(Color.argb(35, 255, 255, 255))
                        setStroke(UiKit.dp(context, 1f), Color.argb(50, 255, 255, 255))
                    }
                }
                tv.setTextColor(UiKit.cText)
                (tv.background as GradientDrawable).apply {
                    setColor(Color.argb(60, 124, 77, 255))
                    setStroke(UiKit.dp(context, 1f), UiKit.cPrimary)
                }
                onPick(i)
            }
            row.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = UiKit.dp(context, 8f)
            })
        }
        return row
    }
}
