package com.nexamart.customer.presentation.orders

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.nexamart.customer.R
import com.nexamart.customer.presentation.common.dpF

/** Port of _MapPainter: a stylised pickup → delivery route (not a real map). */
class DeliveryMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var active: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val primary = ContextCompat.getColor(context, R.color.vk_primary)
    private val background = Paint()
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xA6FFFFFF.toInt(); strokeWidth = 1f.dpF }
    private val road = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAEBDAD.toInt(); strokeWidth = 18f.dpF; strokeCap = Paint.Cap.ROUND
    }
    private val route = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primary; strokeWidth = 7f.dpF; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        background.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFFEDF2EE.toInt(), 0xFFDCE7DC.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = 1f.dpF
        canvas.drawRect(0f, 0f, w, h, background)
        var x = 0f
        while (x < w) {
            canvas.drawLine(x, 0f, x + 40 * d, h, grid); x += 46 * d
        }
        var y = 0f
        while (y < h) {
            canvas.drawLine(0f, y, w, y + 8 * d, grid); y += 42 * d
        }
        val points = listOf(
            PointF(45 * d, h - 35 * d),
            PointF(w * .34f, h * .72f),
            PointF(w * .55f, h * .48f),
            PointF(w * .72f, h * .60f),
            PointF(w - 55 * d, 45 * d),
        )
        for (i in 0 until points.size - 1) {
            canvas.drawLine(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y, road)
        }
        if (active) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1..3) lineTo(points[i].x, points[i].y)
            }
            canvas.drawPath(path, route)
        }
        fill.color = 0xFFFF5722.toInt()
        canvas.drawCircle(points.first().x, points.first().y, 10 * d, fill)
        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(points.first().x, points.first().y, 4 * d, fill)
        fill.color = primary
        canvas.drawCircle(points.last().x, points.last().y, 9 * d, fill)
        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(points.last().x, points.last().y, 4 * d, fill)
    }
}
