package vn.thanhtaxixanh.wazebridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Floating Android phone overlay; Android Auto itself requires an authorized Car App implementation. */
class HudOverlayService : Service() {
    companion object {
        const val ACTION_STOP = "vn.thanhtaxixanh.wazebridge.STOP_HUD"
        private const val CHANNEL = "ttx_waze_mod_bridge"
        private const val NOTIF_ID = 27
    }
    private lateinit var wm: WindowManager
    private var overlay: View? = null
    private lateinit var speedSign: SpeedLimitSignView
    private lateinit var text: TextView
    private lateinit var warning: TextView
    private lateinit var client: WazeModClient
    private val handler = Handler(Looper.getMainLooper())
    private var latest: WazeSnapshot? = null
    private val heartbeat = object : Runnable {
        override fun run() {
            if (latest?.stale == true) {
                speedSign.speedLimit = 0
                warning.text = "⚠ Dữ liệu cũ — chưa xác minh được tốc độ"
            }
            handler.postDelayed(this, 1000)
        }
    }
    private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        client = WazeModClient(this, { message -> handler.post { if (::warning.isInitialized) warning.text=message } },
            { snapshot -> handler.post { latest=snapshot;update(snapshot) } })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action==ACTION_STOP){stopSelf();return START_NOT_STICKY}
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY}
        makeNotification()
        if(overlay==null) addOverlay()
        client.connect()
        handler.removeCallbacks(heartbeat)
        handler.post(heartbeat)
        return START_NOT_STICKY
    }
    private fun makeNotification() {
        val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL,"TTX HUD đang chạy",NotificationManager.IMPORTANCE_LOW))
        val stop=PendingIntent.getService(this,0,Intent(this,HudOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note=Notification.Builder(this,CHANNEL)
            .setContentTitle("TTX Waze Mod 10 HUD")
            .setContentText("Đang hiển thị dữ liệu được bạn cấp quyền")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause,"Dừng HUD",stop)
            .build()
        startForeground(NOTIF_ID,note)
    }
    private fun addOverlay() {
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
            setPadding(dp(10),dp(10),dp(10),dp(10))
            background=GradientDrawable().apply {
                setColor(Color.argb(232,18,29,37)); cornerRadius=dp(14).toFloat()
            }
        }
        speedSign=SpeedLimitSignView(this)
        panel.addView(speedSign,LinearLayout.LayoutParams(dp(82),dp(82)))
        text=TextView(this).apply {setTextColor(Color.WHITE);textSize=16f;gravity=Gravity.CENTER;text="GPS: —"}
        panel.addView(text)
        warning=TextView(this).apply {setTextColor(Color.WHITE);textSize=12f;gravity=Gravity.CENTER;maxWidth=dp(190);text="Chờ Waze Mod..."}
        panel.addView(warning)
        val params=WindowManager.LayoutParams(
            dp(200), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {gravity=Gravity.TOP or Gravity.END;x=dp(10);y=dp(120)}
        wm.addView(panel,params)
        overlay=panel
    }
    private fun update(snapshot: WazeSnapshot) {
        if(overlay==null) return
        speedSign.speedLimit=if(snapshot.stale) 0 else snapshot.limitKmh
        text.text="GPS ${snapshot.speedKmh} km/h"
        warning.text = snapshot.alerts.minByOrNull { it.distanceM }?.let {
            "${it.label} · ${it.distanceM} m"
        } ?: "Chưa có biển báo phía trước"
    }
    override fun onDestroy() {
        handler.removeCallbacks(heartbeat)
        client.disconnect()
        overlay?.let { try{wm.removeView(it)}catch(_:Exception){} }
        overlay=null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder?=null
}
