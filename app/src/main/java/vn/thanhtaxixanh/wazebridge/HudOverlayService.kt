package vn.thanhtaxixanh.wazebridge

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Foreground phone overlay: works without Waze by using its own GPS+SQLite. */
class HudOverlayService:Service() {
    companion object {
        const val ACTION_STOP="vn.thanhtaxixanh.wazebridge.STOP_HUD"
        private const val CHANNEL="ttx_independent_road_hud"
        private const val NOTIFICATION=28
    }
    private lateinit var wm:WindowManager
    private lateinit var coordinator:DataSourceCoordinator
    private var window:View?=null
    private lateinit var speedSign:SpeedLimitSignView
    private lateinit var gpsText:TextView
    private lateinit var sourceText:TextView
    private lateinit var alertText:TextView
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        coordinator=DataSourceCoordinator(this){state ->render(state)}
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==ACTION_STOP) {stopSelf();return START_NOT_STICKY}
        if(!Settings.canDrawOverlays(this)) {stopSelf();return START_NOT_STICKY}
        try {
            showNotification()
            if(window==null)addHud()
            coordinator.start()
        } catch(e:Exception) {stopSelf()}
        return START_NOT_STICKY
    }
    private fun showNotification() {
        val manager=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL,"TTX HUD ngoại tuyến",NotificationManager.IMPORTANCE_LOW))
        val stop=PendingIntent.getService(this,0,Intent(this,HudOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,CHANNEL)
            .setContentTitle("TTX Speed & Signs")
            .setContentText("GPS ngoại tuyến · Waze Mod 10 tùy chọn")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause,"Dừng HUD",stop)
            .build()
        if(Build.VERSION.SDK_INT>=34) {
            val hasGps=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
            val base=ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            val types=if(hasGps) base or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else base
            try {startForeground(NOTIFICATION,notification,types)}
            catch(_:SecurityException) {startForeground(NOTIFICATION,notification,base)}
        } else {
            startForeground(NOTIFICATION,notification)
        }
    }
    private fun addHud() {
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            setPadding(dp(8),dp(10),dp(8),dp(10))
            background=GradientDrawable().apply {
                setColor(Color.argb(236,21,34,45));cornerRadius=dp(16).toFloat()
            }
        }
        speedSign=SpeedLimitSignView(this)
        panel.addView(speedSign,LinearLayout.LayoutParams(dp(80),dp(80)))
        fun line(size:Float)=TextView(this).apply {
            setTextColor(Color.WHITE);textSize=size;gravity=Gravity.CENTER
            setPadding(dp(2),dp(4),dp(2),dp(4));maxWidth=dp(195)
        }
        gpsText=line(17f).apply {text="GPS: —"};panel.addView(gpsText)
        sourceText=line(12f).apply {text="Đang xác định nguồn..."};panel.addView(sourceText)
        alertText=line(12f).apply {text="Chờ dữ liệu biển báo..."};panel.addView(alertText)
        val params=WindowManager.LayoutParams(
            dp(210),WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {gravity=Gravity.TOP or Gravity.END;x=dp(10);y=dp(120)}
        wm.addView(panel,params)
        window=panel
    }
    private fun render(s:TtxDisplay) {
        if(window==null)return
        speedSign.speedLimit=s.limitKmh?:0
        gpsText.text="GPS: ${s.speedKmh?.toString() ?: "—"} km/h"
        sourceText.text=when {
            s.limitKmh==null -> "Chưa xác định giới hạn tốc độ"
            s.source.contains("Waze") -> "Giới hạn từ Waze Mod"
            else -> "Giới hạn ngoại tuyến"
        }
        alertText.text=s.alerts.firstOrNull()?.let{
            "${it.label} · ${it.distanceM} m"
        } ?: "Chưa có biển báo hợp lệ phía trước"
    }
    override fun onDestroy() {
        coordinator.dispose()
        window?.let{try{wm.removeView(it)}catch(_:Exception){}}
        window=null
        super.onDestroy()
    }
    override fun onBind(intent:Intent?):IBinder?=null
}
