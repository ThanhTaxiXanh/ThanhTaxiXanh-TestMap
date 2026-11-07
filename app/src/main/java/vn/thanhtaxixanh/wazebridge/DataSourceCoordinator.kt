package vn.thanhtaxixanh.wazebridge

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.InputStream

enum class TtxSourceMode(val label:String) {
    AUTO("Tự động: Waze + ngoại tuyến"),
    OFFLINE("Chỉ GPS + dữ liệu ngoại tuyến"),
    WAZE("Ưu tiên Waze Mod 10, dự phòng ngoại tuyến")
}
data class TtxDisplay(
    val speedKmh:Int?, val limitKmh:Int?, val street:String,
    val alerts:List<OfflineAlert>, val source:String, val status:String,
    val wazeInstalled:Boolean, val wazeModConfirmed:Boolean,
    val roads:Long, val signs:Long
)
class DataSourceCoordinator(
    context:Context, private val onDisplay:(TtxDisplay)->Unit
) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("ttx_sources_v1",Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    val wazeInstalled:Boolean get() = try {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo("com.waze",0)
        true
    } catch(_:PackageManager.NameNotFoundException) { false }
    val wazePackage = "com.waze"
    var mode:TtxSourceMode = run {
        val saved = preferences.getString("source","AUTO")
        TtxSourceMode.values().firstOrNull {it.name==saved} ?: TtxSourceMode.AUTO
    }
        private set
    private var started = false
    private var wazeSnapshot:WazeSnapshot? = null
    private var offlineSnapshot:OfflineLiveSnapshot? = null
    private var wazeInfo = "Waze chưa kết nối"
    private var offlineInfo = "GPS chưa có dữ liệu"
    private var modApiConfirmed = false
    private var roads = 0L
    private var signs = 0L
    private var lastReconnect = 0L
    private val offline = IndependentEngine(app,
        { current -> offlineSnapshot=current;emit() },
        { message -> offlineInfo=message;emit() })
    private val waze = WazeModClient(app,
        { status ->
            wazeInfo = status
            if(status.contains("Mất kết nối") || status.contains("Không tìm thấy") ||
               status.contains("Chưa cấp quyền") || status.contains("thu hồi") ||
               status.contains("từ chối")) {
                wazeSnapshot = null
            }
            if(status.contains("API v1")) modApiConfirmed=true
            emit()
        },
        { snapshot -> wazeSnapshot=snapshot;emit() })

    private val pulse = object:Runnable {
        override fun run() {
            if(!started) return
            val saved = preferences.getString("source","AUTO")
            if(saved != mode.name) changeMode(TtxSourceMode.values().firstOrNull{it.name==saved}?:TtxSourceMode.AUTO)
            val now = SystemClock.elapsedRealtime()
            // Rebind only if the remote stopped delivering data; never launch Waze UI.
            if(mode != TtxSourceMode.OFFLINE && wazeInstalled &&
               (wazeSnapshot == null || wazeSnapshot?.stale == true) &&
               now-lastReconnect > 12000L) {
                lastReconnect=now
                waze.reconnect()
            }
            emit()
            handler.postDelayed(this,1000)
        }
    }

    fun start() {
        if(started) return
        started=true
        offline.start()
        offline.counts { counts -> roads=counts.first;signs=counts.second;emit() }
        if(mode != TtxSourceMode.OFFLINE && wazeInstalled) waze.connect()
        emit()
        handler.post(pulse)
    }
    fun stop() {
        started=false
        handler.removeCallbacks(pulse)
        waze.disconnect()
        offline.stop()
    }
    fun dispose() { stop();offline.close() }
    fun changeMode(newMode:TtxSourceMode) {
        val old=mode
        mode=newMode
        preferences.edit().putString("source",newMode.name).apply()
        if(started) {
            if(newMode==TtxSourceMode.OFFLINE) {
                waze.disconnect();wazeSnapshot=null
            } else if(old==TtxSourceMode.OFFLINE && wazeInstalled) waze.connect()
        }
        emit()
    }
    fun consentIntent() = waze.consentIntent()
    fun reconnectWaze() {
        if(mode==TtxSourceMode.OFFLINE) changeMode(TtxSourceMode.AUTO)
        if(wazeInstalled)waze.reconnect()
        else {wazeInfo="Chưa cài com.waze — đang dùng ngoại tuyến";emit()}
    }
    fun importJson(input:InputStream,done:(String)->Unit) {
        offline.importJson(input){ result ->
            if(result.isSuccess) {
                val imported=result.getOrThrow()
                offline.counts { c ->
                    roads=c.first;signs=c.second
                    val report="Đã nhập ${imported.roads} đoạn tốc độ, ${imported.signs} biển báo. Nguồn: ${imported.source} · ${imported.license}"
                    done(report);emit()
                }
            } else done("Nhập thất bại; giữ nguyên dữ liệu cũ: ${result.exceptionOrNull()?.message}")
        }
    }
    private fun emit() {
        if(!started) return
        val offlineCurrent=offlineSnapshot?.takeIf{it.fresh}
        val wazeCurrent=if(mode==TtxSourceMode.OFFLINE) null else
            wazeSnapshot?.takeIf{!it.stale}
        val wazeLimit=wazeCurrent?.limitKmh?.takeIf{it in 5..160}
        val offlineLimit=offlineCurrent?.limitKmh?.takeIf{it in 5..160}
        val limit = wazeLimit ?: offlineLimit
        val speed = offlineCurrent?.gpsSpeedKmh ?: wazeCurrent?.speedKmh
        val road = wazeCurrent?.street?.takeIf{it.isNotBlank()}
            ?:offlineCurrent?.street.orEmpty()
        val wazeAlerts=wazeCurrent?.alerts.orEmpty().map {
            OfflineAlert(it.type,it.distanceM,it.value,it.label,"WazeMod API v1")
        }
        val alerts = if(wazeAlerts.isNotEmpty()) wazeAlerts else offlineCurrent?.alerts.orEmpty()
        val activeSource=when {
            wazeLimit!=null -> "Waze Mod 10 (com.waze)"
            offlineLimit!=null -> "GPS + dữ liệu ngoại tuyến"
            wazeCurrent!=null -> "Waze Mod (chưa có giới hạn tốc độ)"
            offlineCurrent!=null -> "GPS riêng (chưa có giới hạn tốc độ)"
            else -> "Chưa có vị trí GPS / dữ liệu live"
        }
        val background = if(mode==TtxSourceMode.OFFLINE) "Waze tắt theo lựa chọn" else
            if(wazeInstalled) "${if(modApiConfirmed)"Mod API v1 xác nhận" else "Phát hiện com.waze, chưa xác nhận Mod API"} · $wazeInfo"
            else "Không cài Waze; TTX hoạt động độc lập"
        val dataStatus = "Offline: $roads đoạn tốc độ, $signs biển báo. " +
            (offlineCurrent?.let{"GPS ±${it.accuracyM.toInt()}m · ${it.quality}"} ?: offlineInfo)
        onDisplay(TtxDisplay(speed,limit,road,alerts,activeSource,
            "$background\n$dataStatus",wazeInstalled,modApiConfirmed,roads,signs))
    }
}
