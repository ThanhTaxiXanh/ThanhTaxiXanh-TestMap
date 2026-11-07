package vn.thanhtaxixanh.wazebridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** Locally drawn UI symbol; no Waze assets copied. */
class SpeedLimitSignView(context: Context) : View(context) {
    var speedLimit: Int = 0
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val default = (76f * resources.displayMetrics.density).toInt()
        setMeasuredDimension(resolveSize(default, widthMeasureSpec), resolveSize(default, heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = min(width, height) / 2f - 5f
        val cx = width / 2f; val cy = height / 2f
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.25f
        paint.color = Color.rgb(212,28,28)
        canvas.drawCircle(cx, cy, radius - radius * 0.12f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER; paint.isFakeBoldText = true
        paint.textSize = radius * if (speedLimit >= 100) 0.70f else 0.94f
        paint.color = Color.BLACK
        val label = if (speedLimit > 0) speedLimit.toString() else "—"
        canvas.drawText(label, cx, cy - (paint.ascent() + paint.descent())/2f, paint)
    }
}
