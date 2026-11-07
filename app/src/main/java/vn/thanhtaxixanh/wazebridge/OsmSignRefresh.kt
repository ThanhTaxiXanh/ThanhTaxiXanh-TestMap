package vn.thanhtaxixanh.wazebridge

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import kotlin.math.floor

/**
 * Separate, licensed OSM source; never opens com.waze, reads Waze private storage,
 * calls Mod-only endpoints, or presents missing OSM data as a real road sign.
 */
data class OsmSignRecord(
    val id: String, val lat: Double, val lon: Double, val type: Int,
    val value: Int, val heading: Double?, val source: String
)

/** Conservative tag translation: unknown codes are not guessed. */
internal object OsmSignTags {
    fun classify(tags: Map<String, String>): Pair<Int, Int>? {
        val sign = tags["traffic_sign"]?.lowercase(Locale.ROOT).orEmpty()
        val highway = tags["highway"]?.lowercase(Locale.ROOT).orEmpty()
        if (highway == "speed_camera") return 2 to 0
        if (sign.contains("maxspeed") && tags["maxspeed"] != null) {
            val value = speedKmh(tags["maxspeed"].orEmpty())
            if (value != null) return 76 to value
        }
        if (sign.contains("city_limit")) {
            return (if (tags["city_limit"].equals("end", true)) 24 else 23) to 0
        }
        if (sign.contains("overtaking") && tags["overtaking"].equals("no",true)) return 9 to 0
        if (sign.contains("no_overtaking")) return 9 to 0
        if (sign.contains("end_of_no_overtaking")) return 10 to 0
        if (sign.contains("no_left_turn")) return 28 to 0
        if (sign.contains("no_right_turn")) return 29 to 0
        if (sign.contains("no_u_turn")) return 30 to 0
        if (sign == "stop" || highway == "stop") return 77 to 0
        if (sign == "give_way" || highway == "give_way") return 78 to 0
        return null
    }

    fun speedKmh(raw: String): Int? {
        val v = raw.trim().lowercase(Locale.ROOT)
        val m = Regex("^(\\d{1,3})(?:\\s*km/h)?$").matchEntire(v) ?: return null
        return m.groupValues[1].toIntOrNull()?.takeIf { it in 5..160 }
    }

    fun heading(tags: Map<String, String>): Double? {
        val raw = tags["direction"]?.trim()?.uppercase(Locale.ROOT) ?: return null
        val degrees = when(raw) {
            "N" -> 0.0
            "NE" -> 45.0
            "E" -> 90.0
            "SE" -> 135.0
            "S" -> 180.0
            "SW" -> 225.0
            "W" -> 270.0
            "NW" -> 315.0
            else -> raw.removeSuffix("°").toDoubleOrNull()
        }
        return degrees?.takeIf { it >= 0.0 && it <= 360.0 }
    }
}

/**
 * A deliberately rate-limited, local-area OSM sign cache for the no-Waze-open requirement.
 * Requires Internet once per area; previously downloaded signs remain in SQLite offline.
 * Public Overpass is best effort, never a nationwide availability guarantee.
 */
internal class OsmSignRefresh(
    context: Context, private val db: OfflineRoadDatabase,
    private val onStatus: (String) -> Unit,
    private val onRefresh: () -> Unit
) {
    private val prefs = context.getSharedPreferences("ttx_osm_sign_cache_v1", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var working = false
    @Volatile private var nextRequestAtMs = 0L
    private val minBetweenRequestsMs = 10L * 60_000
    private val ttlMs = 24L * 60 * 60_000
    private val maxDailyRequests = 24
    private val endpoint = "https://overpass-api.de/api/interpreter"
    private val radiusM = 2_500

    fun consider(location: Location) {
        if (working || !location.hasAccuracy() || location.accuracy > 70f) return
        val lat = location.latitude
        val lon = location.longitude
        if (lat !in 7.5..23.8 || lon !in 101.5..110.0) return
        val now = System.currentTimeMillis()
        val tile = "${floor(lat * 40).toInt()}_${floor(lon * 40).toInt()}"
        if (prefs.getLong("tile_$tile", 0) > now - ttlMs) return
        if (now < nextRequestAtMs) return
        val day = now / 86_400_000L
        val dayKey = "requests_$day"
        if (prefs.getInt(dayKey, 0) >= maxDailyRequests) return
        // Save the quota before a network request to avoid tight retry loops on failures.
        prefs.edit().putInt(dayKey, prefs.getInt(dayKey, 0) + 1).apply()
        nextRequestAtMs = now + minBetweenRequestsMs
        working = true
        executor.execute {
            try {
                val records = retrieve(lat, lon)
                db.mergeOsmSigns(records)
                prefs.edit().putLong("tile_$tile", System.currentTimeMillis()).apply()
                handler.post {
                    onStatus("OSM: cập nhật ${records.size} biển trong bán kính ${radiusM}m; thiếu dữ liệu thì không suy đoán.")
                    onRefresh()
                }
            } catch (e: Exception) {
                nextRequestAtMs = System.currentTimeMillis() + 30L * 60_000
                handler.post { onStatus("Nguồn OSM chưa truy cập được (${e.javaClass.simpleName}); tiếp tục dùng bộ biển đã lưu.") }
            } finally {
                working = false
            }
        }
    }

    private fun retrieve(lat: Double, lon: Double): List<OsmSignRecord> {
        val latStr = String.format(Locale.US, "%.6f", lat)
        val lonStr = String.format(Locale.US, "%.6f", lon)
        val query = """[out:json][timeout:18];
            (node(around:$radiusM,$latStr,$lonStr)["traffic_sign"];
             node(around:$radiusM,$latStr,$lonStr)["highway"="speed_camera"];);
            out body;""".trimIndent()
        val conn = URL(endpoint).openConnection() as HttpsURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 12_000
        conn.readTimeout = 24_000
        conn.doOutput = true
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "TTX-SpeedSigns/0.4 (OpenStreetMap personal-use cache)")
        try {
            val body = "data=" + URLEncoder.encode(query, "UTF-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) throw IllegalStateException("OSM HTTP ${conn.responseCode}")
            val bytes = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val chunk = ByteArray(8192)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    if (bytes.size() + n > 2_000_000) throw IllegalStateException("OSM response too large")
                    bytes.write(chunk, 0, n)
                }
            }
            val elements = JSONObject(bytes.toString("UTF-8")).getJSONArray("elements")
            if (elements.length() > 6_000) throw IllegalStateException("OSM too many elements")
            val found = ArrayList<OsmSignRecord>()
            for (i in 0 until elements.length()) {
                val item = elements.getJSONObject(i)
                if (item.optString("type") != "node") continue
                val id = item.optLong("id", 0L)
                val eLat = item.optDouble("lat", Double.NaN)
                val eLon = item.optDouble("lon", Double.NaN)
                if (id <= 0 || !eLat.isFinite() || !eLon.isFinite() ||
                    eLat !in -90.0..90.0 || eLon !in -180.0..180.0) continue
                val t = item.optJSONObject("tags") ?: continue
                val tags = HashMap<String, String>()
                for (key in t.keys()) tags[key] = t.optString(key, "")
                val kind = OsmSignTags.classify(tags) ?: continue
                found.add(OsmSignRecord(
                    "osm:node:$id", eLat, eLon, kind.first, kind.second,
                    OsmSignTags.heading(tags), "OpenStreetMap contributors · ODbL"
                ))
            }
            return found
        } finally {
            conn.disconnect()
        }
    }

    fun close() { executor.shutdownNow() }
}
