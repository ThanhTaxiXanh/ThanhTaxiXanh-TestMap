package vn.thanhtaxixanh.wazebridge

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.InputStream
import java.util.concurrent.Executors

/** GPS and road data entirely independent from com.waze. No maps or tile downloads. */
data class OfflineLiveSnapshot(
    val gpsSpeedKmh:Int?, val limitKmh:Int?, val street:String,
    val alerts:List<OfflineAlert>, val elapsedMs:Long, val accuracyM:Float,
    val quality:String, val datasetSource:String
) {
    val ageMs:Long get() = (SystemClock.elapsedRealtime()-elapsedMs).coerceAtLeast(0)
    val fresh:Boolean get() = ageMs <= 8_000
}
class IndependentEngine(
    context:Context, private val onLocation:(OfflineLiveSnapshot)->Unit,
    private val onStatus:(String)->Unit
) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val db = OfflineRoadDatabase(app)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    @Volatile private var newestGpsElapsedMs = 0L

    private val listener = object:LocationListener {
        override fun onLocationChanged(location:Location) {
            if (!started) return
            val fix = Location(location)
            // Network-only coarse fixes must not be mistaken for road-level precision.
            if(fix.accuracy > 100f || !fix.hasAccuracy()) {
                onStatus("GPS chưa đủ chính xác; không xác nhận giới hạn tốc độ")
                return
            }
            val time = SystemClock.elapsedRealtime()
            newestGpsElapsedMs = time
            worker.execute {
                try {
                    val road = db.nearest(fix)
                    val gpsSpeed = if(fix.hasSpeed()) (fix.speed * 3.6f).toInt().coerceAtLeast(0) else null
                    val quality = when {
                        road.ambiguous -> "Nhiều đoạn đường có giới hạn khác nhau — chưa xác định"
                        road.limitKmh == null -> "Chưa tìm thấy giới hạn tốc độ đáng tin cậy"
                        else -> "Khớp đường ~${road.matchDistanceM?.toInt() ?: 0}m; cần đối chiếu biển thực tế"
                    }
                    val update = OfflineLiveSnapshot(gpsSpeed,road.limitKmh,road.roadName,
                        road.alerts,time,fix.accuracy,quality,road.source)
                    main.post { if(started && time >= newestGpsElapsedMs) onLocation(update) }
                } catch(e:Exception) {
                    main.post { if(started)onStatus("Lỗi đọc dữ liệu ngoại tuyến: ${e.javaClass.simpleName}") }
                }
            }
        }
        override fun onProviderEnabled(provider:String) {}
        override fun onProviderDisabled(provider:String) {
            onStatus("GPS đã tắt — bật Vị trí để nhận tốc độ")
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if(started) return
        if(app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
           app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onStatus("Chưa cấp quyền vị trí cho chế độ ngoại tuyến")
            return
        }
        started = true
        var count = 0
        for(provider in listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER)) {
            try {
                if(manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider,900L,0f,listener,Looper.getMainLooper())
                    count++
                }
            } catch(_:SecurityException) {
                onStatus("Quyền GPS bị từ chối")
            } catch(_:Exception) {}
        }
        if(count == 0) onStatus("Bật vị trí GPS trên điện thoại")
    }
    fun stop() {
        started = false
        try {manager.removeUpdates(listener)} catch(_:Exception) {}
    }
    fun importJson(input:InputStream,done:(Result<ImportedDataset>)->Unit) {
        worker.execute {
            val outcome = try {
                input.use { Result.success(db.import(it)) }
            } catch(e:Exception) { Result.failure(e) }
            main.post { done(outcome) }
        }
    }
    fun counts(done:(Pair<Long,Long>)->Unit) {
        worker.execute {
            val result = try {db.counts()} catch(_:Exception) {0L to 0L}
            main.post {done(result)}
        }
    }
    fun close() {
        stop()
        // Jobs already queued finish, but no more callbacks are emitted once stopped.
        worker.shutdown()
    }
}
