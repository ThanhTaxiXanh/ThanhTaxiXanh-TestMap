package vn.thanhtaxixanh.wazebridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/** Works without com.waze. Optional Waze Mod 10 API is a source, not a dependency. */
class MainActivity:Activity() {
    private lateinit var sources:DataSourceCoordinator
    private lateinit var appStatus:TextView
    private lateinit var speed:TextView
    private lateinit var road:TextView
    private lateinit var alarms:TextView
    private lateinit var sourceInfo:TextView
    private lateinit var detected:TextView
    private lateinit var sign:SpeedLimitSignView
    private val CONSENT=101
    private val OVERLAY=102
    private val IMPORT_JSON=104
    private val LOCATION_PERMISSION=105

    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun label(text:String,size:Float=17f)=TextView(this).apply {
        this.text=text
        textSize=size
        setTextColor(Color.rgb(26,42,52))
        setPadding(0,dp(7),0,dp(7))
    }
    private fun button(name:String,click:()->Unit)=Button(this).apply {
        text=name
        setOnClickListener {click()}
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        sources=DataSourceCoordinator(this){state ->runOnUiThread{show(state)}}
        val page=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(16),dp(16),dp(22))
        }
        page.addView(label("TTX · Giới hạn tốc độ & Biển báo",23f))
        page.addView(label("Chạy độc lập bằng GPS + dữ liệu địa phương. Waze Mod 10 chỉ là nguồn bổ sung.",14f))
        appStatus=label("Đang khởi tạo GPS...",14f)
        page.addView(appStatus)
        page.addView(label("Nguồn tốc độ",18f))
        val options=TtxSourceMode.values()
        val picker=Spinner(this)
        val pickerAdapter=ArrayAdapter(this,android.R.layout.simple_spinner_item,options.map{it.label})
        pickerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        picker.adapter=pickerAdapter
        picker.setSelection(sources.mode.ordinal)
        picker.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?, view:View?,position:Int,id:Long) {
                sources.changeMode(options[position])
            }
            override fun onNothingSelected(parent:AdapterView<*>?) {}
        }
        page.addView(picker)
        detected=label("",14f);page.addView(detected)
        page.addView(button("Nhập dữ liệu tốc độ / biển báo từ JSON") {
            val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="application/json"
            }
            try {startActivityForResult(intent,IMPORT_JSON)}
            catch(e:Exception) {appStatus.text="Không mở được bộ chọn dữ liệu: ${e.message}"}
        })
        page.addView(button("Xin quyền Waze Mod 10 (nếu đã cài)") {
            if(!sources.wazeInstalled) {
                appStatus.text="Không cài com.waze — vẫn dùng GPS + dữ liệu ngoại tuyến."
            } else try {
                startActivityForResult(sources.consentIntent(),CONSENT)
            } catch(e:Exception) {
                appStatus.text="com.waze không hỗ trợ màn xin quyền Mod 10: ${e.javaClass.simpleName}"
            }
        })
        page.addView(button("Kết nối lại Waze (tùy chọn)") {sources.reconnectWaze()})
        page.addView(button("Bật HUD nổi trên ứng dụng khác") {startHud()})
        page.addView(button("Dừng HUD nổi") {
            stopService(Intent(this,HudOverlayService::class.java))
        })
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        sign=SpeedLimitSignView(this)
        row.addView(sign,LinearLayout.LayoutParams(dp(95),dp(95)))
        speed=label("GPS: —",23f);row.addView(speed);page.addView(row)
        road=label("Đường: Chưa xác định",17f);page.addView(road)
        alarms=label("Biển báo: Chưa có dữ liệu điểm hợp lệ",16f);page.addView(alarms)
        page.addView(label("Trạng thái nguồn dữ liệu",18f))
        sourceInfo=label("",14f);page.addView(sourceInfo)
        page.addView(label(
            "Nguồn OSM: © OpenStreetMap contributors (ODbL), nếu dùng dữ liệu OSM. " +
            "Giới hạn tốc độ chỉ mang tính hỗ trợ; ưu tiên biển báo giao thông thực tế. " +
            "Tệp JSON riêng cần có nguồn và giấy phép hợp lệ.",12f))
        val scroll=ScrollView(this);scroll.addView(page);setContentView(scroll)
        requestGpsPermissionIfNeeded()
        if(Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),106)
        }
    }
    private fun show(state:TtxDisplay) {
        sign.speedLimit=state.limitKmh?:0
        speed.text="GPS: ${state.speedKmh?.toString() ?: "—"} km/h"
        road.text="Đường: ${state.street.ifBlank{"Chưa xác định"}}"
        alarms.text=if(state.alerts.isEmpty()) "Biển báo: chưa có cảnh báo có tọa độ phía trước" else
            state.alerts.joinToString("\n"){"• ${it.label} · ${it.distanceM} m" +
                if(it.value>0) " (${it.value})" else ""}
        detected.text=when {
            !state.wazeInstalled -> "com.waze: Chưa cài. TTX dùng GPS và cơ sở dữ liệu riêng."
            state.wazeModConfirmed -> "com.waze: Đã xác nhận Waze Mod Data API v1."
            else -> "com.waze: Đã phát hiện package; chưa xác nhận hỗ trợ Mod 10."
        }
        sourceInfo.text="Nguồn đang sử dụng: ${state.source}\n${state.status}"
    }
    private fun requestGpsPermissionIfNeeded() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION),LOCATION_PERMISSION)
        }
    }
    private fun startHud() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
           checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            requestGpsPermissionIfNeeded()
            appStatus.text="Cấp GPS để HUD hoạt động độc lập với Waze."
            return
        }
        if(!Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")),OVERLAY)
            return
        }
        try {
            startForegroundService(Intent(this,HudOverlayService::class.java))
        } catch(e:Exception) {appStatus.text="Không bật được HUD: ${e.javaClass.simpleName}"}
    }
    override fun onStart() {super.onStart();sources.start()}
    override fun onStop() {sources.stop();super.onStop()}
    override fun onDestroy() {sources.dispose();super.onDestroy()}

    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>,
        grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==LOCATION_PERMISSION) {
            sources.stop();sources.start()
            appStatus.text=if(grantResults.any{it==PackageManager.PERMISSION_GRANTED})
                "Đã cấp GPS. Đang tìm vị trí..." else
                "Chưa có quyền GPS; không thể chạy nguồn độc lập."
        }
    }
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        when(requestCode) {
            CONSENT -> if(resultCode==RESULT_OK) {
                appStatus.text="Đã cấp quyền WazeMod; đang đồng bộ."
                sources.reconnectWaze()
            } else appStatus.text="Waze chưa cấp quyền. Chế độ ngoại tuyến vẫn hoạt động."
            OVERLAY -> if(Settings.canDrawOverlays(this)) startHud() else
                appStatus.text="Chưa cấp quyền HUD nổi."
            IMPORT_JSON -> if(resultCode==RESULT_OK && data?.data!=null) {
                try {
                    val input=contentResolver.openInputStream(data.data!!)
                        ?:throw IllegalStateException("Không mở được tệp")
                    appStatus.text="Đang nhập bộ dữ liệu ngoại tuyến..."
                    sources.importJson(input){message ->
                        if(!isDestroyed)appStatus.text=message
                    }
                } catch(e:Exception) {
                    appStatus.text="Lỗi mở tệp: ${e.message}"
                }
            }
        }
    }
}
