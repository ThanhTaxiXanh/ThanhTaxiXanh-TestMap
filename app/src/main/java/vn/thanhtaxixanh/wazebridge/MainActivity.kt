package vn.thanhtaxixanh.wazebridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var client: WazeModClient
    private lateinit var status: TextView
    private lateinit var currentSpeed: TextView
    private lateinit var street: TextView
    private lateinit var alerts: TextView
    private lateinit var sign: SpeedLimitSignView
    private var last: WazeSnapshot? = null
    private val CONSENT = 101
    private val OVERLAY = 102

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun txt(s: String, size: Float = 18f): TextView = TextView(this).apply {
        text = s; textSize = size; setTextColor(Color.rgb(30,38,48));setPadding(0,dp(8),0,dp(8))
    }
    private fun btn(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = WazeModClient(this, { s -> runOnUiThread { status.text = s } },
            { snapshot -> runOnUiThread { last = snapshot; render(snapshot) } })
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(18),dp(20),dp(25))
        }
        val title = txt("TTX · Waze Mod 10 Bridge",23f)
        layout.addView(title)
        layout.addView(txt("Ứng dụng mẫu: đọc cảnh báo qua Binder, chỉ sau khi bạn cấp quyền.",14f))
        status = txt("Chưa kết nối",14f)
        layout.addView(status)
        layout.addView(btn("1 · Xin quyền Waze Mod 10") {
            try { startActivityForResult(client.consentIntent(), CONSENT) }
            catch (e: Exception) { status.text="Không mở được màn hình cấp quyền: ${e.message}" }
        })
        layout.addView(btn("2 · Kết nối lại / đọc dữ liệu") { client.reconnect() })
        layout.addView(btn("3 · Bật HUD nổi trên ứng dụng khác") { startHud() })
        layout.addView(btn("Dừng HUD nổi") { stopService(Intent(this, HudOverlayService::class.java)) })
        val speedRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        sign = SpeedLimitSignView(this)
        speedRow.addView(sign, LinearLayout.LayoutParams(dp(90),dp(90)))
        currentSpeed = txt("GPS: — km/h",22f)
        speedRow.addView(currentSpeed)
        layout.addView(speedRow)
        street = txt("Đường: —",16f); layout.addView(street)
        alerts = txt("Chưa có cảnh báo",17f); layout.addView(alerts)
        layout.addView(txt("Lưu ý: giới hạn 0 = không biết; đây là dữ liệu HUD hiện tại, không phải kho biển báo GPS cả nước.",13f))
        val scroll=ScrollView(this);scroll.addView(layout)
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 103)
        }
    }

    private fun render(s: WazeSnapshot) {
        val fresh = !s.stale
        sign.speedLimit = if (fresh) s.limitKmh else 0
        currentSpeed.text = "GPS: ${s.speedKmh} km/h" + (if (s.overspeed) " · VƯỢT TỐC" else "")
        street.text = "Đường: ${s.street.ifBlank { "Chưa nhận" }}" + (if (s.navigating) " · Đang dẫn đường" else "")
        alerts.text = if (s.alerts.isEmpty()) "Chưa có cảnh báo phía trước" else
            s.alerts.joinToString("\n") { "• ${it.label} · ${it.distanceM} m" + if (it.value>0) " · ${it.value}" else "" }
    }

    private fun startHud() {
        if (!Settings.canDrawOverlays(this)) {
            val intent=Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${packageName}"))
            startActivityForResult(intent, OVERLAY)
            return
        }
        try { startForegroundService(Intent(this,HudOverlayService::class.java)) }
        catch(e: Exception){status.text="Không chạy được HUD: ${e.message}"}
    }

    override fun onStart() {super.onStart();client.connect()}
    override fun onStop() {client.disconnect();super.onStop()}

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        when(requestCode){
            CONSENT -> if(resultCode==RESULT_OK){status.text="Đã đồng ý. Đang kết nối lại...";client.reconnect()}
                       else status.text="Chưa được Waze Mod cấp quyền"
            OVERLAY -> if(Settings.canDrawOverlays(this)) startHud()
                       else status.text="Chưa có quyền hiển thị đè lên ứng dụng khác"
        }
    }
}
