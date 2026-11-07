package vn.thanhtaxixanh.wazebridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** Simple drawn speed-limit sign; no copied Waze assets. */
class SpeedLimitSignView(context: Context) : View(context) {
    var speedLimit: Int = 0
        set(value) { field = value; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = (76f * resources.displayMetrics.density).toInt()
        setMeasuredDimension(resolveSize(d,widthMeasureSpec),resolveSize(d,heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = min(width,height)/2f - 5f
        val cx = width/2f; val cy = height/2f
        p.style = Paint.Style.FILL; p.color = Color.WHITE
        canvas.drawCircle(cx,cy,radius,p)
        p.style = Paint.Style.STROKE; p.strokeWidth = radius * 0.25f; p.color = Color.rgb(212,28,28)
        canvas.drawCircle(cx,cy,radius-radius*0.12f,p)
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
        p.textSize = radius * (if (speedLimit >= 100) 0.70f else 0.94f)
        p.color = Color.BLACK
        val txt = if (speedLimit > 0) speedLimit.toString() else "—"
        canvas.drawText(txt,cx,cy - (p.ascent()+p.descent())/2f,p)
    }
}
