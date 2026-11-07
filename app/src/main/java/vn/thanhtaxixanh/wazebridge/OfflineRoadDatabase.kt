package vn.thanhtaxixanh.wazebridge

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.os.SystemClock
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

data class OfflineAlert(
    val type:Int, val distanceM:Int, val value:Int, val label:String, val origin:String
)
data class OfflineRoadResult(
    val limitKmh:Int?, val roadName:String, val matchDistanceM:Double?,
    val ambiguous:Boolean, val alerts:List<OfflineAlert>, val source:String
)
data class ImportedDataset(val roads:Int, val signs:Int, val source:String, val license:String)

/**
 * App-private SQLite database. Imports the user's OSM JSON using streaming JsonReader.
 * Does not download, decrypt, or copy Waze/WazeMod proprietary geographic data.
 */
class OfflineRoadDatabase(context:Context) : SQLiteOpenHelper(context, "ttx_roads_v1.db", null, 2) {
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE roads (
            way_id INTEGER PRIMARY KEY, road_name TEXT NOT NULL,
            maxspeed INTEGER, forward_speed INTEGER, backward_speed INTEGER,
            minlat REAL NOT NULL, maxlat REAL NOT NULL, minlon REAL NOT NULL, maxlon REAL NOT NULL,
            geometry TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX roads_bbox ON roads(minlat, maxlat, minlon, maxlon)")
        db.execSQL("""CREATE TABLE signs (
            id TEXT PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL,
            type INTEGER NOT NULL, value INTEGER NOT NULL, heading REAL, source TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX signs_lat_lon ON signs(lat,lon)")
        createOsmTable(db)
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
        if (oldVersion < 2) createOsmTable(db) // preserve imported user road/sign data
    }

    private fun createOsmTable(db:SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS osm_signs (
            id TEXT PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL,
            type INTEGER NOT NULL, value INTEGER NOT NULL, heading REAL,
            source TEXT NOT NULL, last_seen_ms INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS osm_signs_lat_lon ON osm_signs(lat,lon)")
    }

    /** Cache public OSM points without deleting the user's own imported signs. */
    fun mergeOsmSigns(records:List<OsmSignRecord>) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for (s in records) {
                val values = ContentValues().apply {
                    put("id", s.id);put("lat",s.lat);put("lon",s.lon)
                    put("type",s.type);put("value",s.value)
                    if(s.heading != null) put("heading",s.heading) else putNull("heading")
                    put("source",s.source);put("last_seen_ms",now)
                }
                db.insertWithOnConflict("osm_signs",null,values,SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.delete("osm_signs","last_seen_ms < ?",arrayOf((now - 30L*86_400_000L).toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun JsonReader.textOrNull():String? = when (peek()) {
        JsonToken.NULL -> { nextNull(); null }
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        else -> { skipValue(); null }
    }
    private fun JsonReader.numberOrNull():Double? = textOrNull()?.toDoubleOrNull()
    private fun JsonReader.intOrNull():Int? = numberOrNull()?.toInt()

    /**
     * Supports provided vietnam_speed_limits_1.json (features with geometry as JSON-encoded
     * text) and user-supplied point-sign JSON (signs array).
     * Both datasets require source + license for provenance.
     * A failed import rolls back, retaining previous data.
     */
    fun import(input:InputStream):ImportedDataset {
        val db = writableDatabase
        var speedCount = 0
        var signCount = 0
        var seenRoads = false
        var seenSigns = false
        var source = ""
        var license = ""
        db.beginTransaction()
        try {
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.isLenient = false
                reader.beginObject()
                while(reader.hasNext()) {
                    when(reader.nextName()) {
                        "source" -> source = reader.textOrNull().orEmpty().take(400)
                        "license" -> license = reader.textOrNull().orEmpty().take(200)
                        "features" -> {
                            seenRoads = true
                            db.delete("roads", null, null)
                            reader.beginArray()
                            while(reader.hasNext()) {
                                val v = readRoad(reader)
                                if(v != null) { db.insertWithOnConflict("roads",null,v,SQLiteDatabase.CONFLICT_REPLACE); speedCount++ }
                            }
                            reader.endArray()
                        }
                        "signs" -> {
                            seenSigns = true
                            db.delete("signs",null,null)
                            reader.beginArray()
                            while(reader.hasNext()) {
                                val v = readSign(reader)
                                if(v != null) { db.insertWithOnConflict("signs",null,v,SQLiteDatabase.CONFLICT_REPLACE); signCount++ }
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
            require(source.isNotBlank() && license.isNotBlank()) {
                "Tệp phải khai báo nguồn (source) và giấy phép/quyền dùng (license)."
            }
            require((seenRoads && speedCount > 0) || (seenSigns && signCount > 0)) {
                "Tệp không chứa đoạn đường hoặc điểm biển báo hợp lệ."
            }
            val metadata = mapOf("source" to source,"license" to license,
                "import_timestamp" to System.currentTimeMillis().toString())
            for ((key,value) in metadata) {
                val c = ContentValues().apply { put("key", key); put("value",value) }
                db.insertWithOnConflict("metadata",null,c,SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            return ImportedDataset(speedCount, signCount, source, license)
        } finally {
            db.endTransaction()
        }
    }

    private fun readRoad(r:JsonReader):ContentValues? {
        var wayId:Long? = null
        var name = ""
        var speed:Int? = null
        var forward:Int? = null
        var backward:Int? = null
        var minLat:Double? = null
        var maxLat:Double? = null
        var minLon:Double? = null
        var maxLon:Double? = null
        var geometry:String? = null
        r.beginObject()
        while(r.hasNext()) {
            when(r.nextName()) {
                "way_id" -> wayId = r.textOrNull()?.toLongOrNull()
                "name" -> name = r.textOrNull().orEmpty().take(180)
                "maxspeed_kmh" -> speed = r.intOrNull()
                "forward_kmh" -> forward = r.intOrNull()
                "backward_kmh" -> backward = r.intOrNull()
                "minlat" -> minLat = r.numberOrNull()
                "maxlat" -> maxLat = r.numberOrNull()
                "minlon" -> minLon = r.numberOrNull()
                "maxlon" -> maxLon = r.numberOrNull()
                "geometry" -> geometry = r.textOrNull()
                else -> r.skipValue()
            }
        }
        r.endObject()
        val lat1 = minLat ?: return null
        val lat2 = maxLat ?: return null
        val lon1 = minLon ?: return null
        val lon2 = maxLon ?: return null
        if (wayId == null || lat1 !in -90.0..90.0 || lat2 !in -90.0..90.0 ||
            lon1 !in -180.0..180.0 || lon2 !in -180.0..180.0 ||
            lat1 > lat2 || lon1 > lon2 || geometry.isNullOrBlank()) return null
        val cleanSpeed = speed?.takeIf { it in 5..160 }
        val f = forward?.takeIf { it in 5..160 }
        val b = backward?.takeIf { it in 5..160 }
        if (cleanSpeed == null && f == null && b == null) return null
        return ContentValues().apply {
            put("way_id",wayId)
            put("road_name",name)
            if(cleanSpeed != null) put("maxspeed",cleanSpeed) else putNull("maxspeed")
            if(f != null) put("forward_speed",f) else putNull("forward_speed")
            if(b != null) put("backward_speed",b) else putNull("backward_speed")
            put("minlat",lat1);put("maxlat",lat2);put("minlon",lon1);put("maxlon",lon2)
            put("geometry",geometry)
        }
    }
    private fun readSign(r:JsonReader):ContentValues? {
        var id = "";var lat:Double? = null;var lon:Double? = null
        var type:Int? = null; var value = 0; var heading:Double? = null; var origin = ""
        r.beginObject()
        while(r.hasNext()) {
            when(r.nextName()) {
                "id" -> id = r.textOrNull().orEmpty().take(100)
                "lat" -> lat = r.numberOrNull()
                "lon" -> lon = r.numberOrNull()
                "type" -> type = r.intOrNull()
                "value" -> value = r.intOrNull() ?: 0
                "heading" -> heading = r.numberOrNull()
                "source" -> origin = r.textOrNull().orEmpty().take(160)
                else -> r.skipValue()
            }
        }
        r.endObject()
        if(id.isBlank() || lat == null || lon == null || type == null ||
            lat !in -90.0..90.0 || lon !in -180.0..180.0 || type !in 1..200 ||
            (heading != null && heading !in 0.0..360.0)) return null
        return ContentValues().apply {
            put("id",id);put("lat",lat);put("lon",lon);put("type",type)
            put("value",value);if(heading != null)put("heading",heading) else putNull("heading")
            put("source",origin)
        }
    }

    fun counts():Pair<Long,Long> {
        val db = readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM roads",null).use { c ->
            c.moveToFirst();val roads = c.getLong(0)
            db.rawQuery("SELECT COUNT(*) FROM signs",null).use { s ->
                s.moveToFirst()
                db.rawQuery("SELECT COUNT(*) FROM osm_signs",null).use { osm ->
                    osm.moveToFirst()
                    return roads to (s.getLong(0) + osm.getLong(0))
                }
            }
        }
    }

    fun nearest(location:Location):OfflineRoadResult {
        val lat = location.latitude; val lon = location.longitude
        val db = readableDatabase
        val radiusM = max(25.0,min(60.0,location.accuracy.toDouble() * 1.5))
        val deltaLat = radiusM / 111320.0
        val deltaLon = radiusM / (111320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.05))
        val candidates = ArrayList<MatchedRoad>()
        db.rawQuery(
            "SELECT road_name,maxspeed,forward_speed,backward_speed,geometry FROM roads "+
                "WHERE minlat<=? AND maxlat>=? AND minlon<=? AND maxlon>=? LIMIT 500",
            arrayOf((lat+deltaLat).toString(),(lat-deltaLat).toString(),
                (lon+deltaLon).toString(),(lon-deltaLon).toString())
        ).use { cursor ->
            while(cursor.moveToNext()) {
                val geometry = cursor.getString(4) ?: continue
                val match = matchGeometry(lat,lon,geometry) ?: continue
                if(match.first > radiusM) continue
                val speed = if(cursor.isNull(1)) null else cursor.getInt(1)
                val forward = if(cursor.isNull(2)) null else cursor.getInt(2)
                val backward = if(cursor.isNull(3)) null else cursor.getInt(3)
                val travelAngle = if (location.hasBearing() && location.speed >= 1.5f)
                    GeoMath.angleDelta(location.bearing.toDouble(),match.second) else null
                val chosen = when {
                    travelAngle == null -> speed ?: if(forward == backward) forward else null
                    travelAngle <= 55.0 -> forward ?: speed
                    travelAngle >= 125.0 -> backward ?: speed
                    else -> speed
                }
                candidates.add(MatchedRoad(match.first,cursor.getString(0) ?: "",chosen))
            }
        }
        candidates.sortBy { it.distance }
        val best = candidates.firstOrNull()
        val ambiguous = if(best == null) false else candidates.drop(1).any {
            it.distance <= best.distance + 8.0 && it.speed != best.speed
        }
        val signs = nearbySigns(db,location,500.0)
        return OfflineRoadResult(
            if(!ambiguous) best?.speed else null,
            if(!ambiguous) best?.name.orEmpty() else "",
            best?.distance,ambiguous,signs,metadata("source")
        )
    }
    private data class MatchedRoad(val distance:Double,val name:String,val speed:Int?)
    private fun matchGeometry(lat:Double, lon:Double,geometry:String):Pair<Double,Double>? {
        return try {
            val array = JSONArray(geometry)
            if(array.length() < 2) return null
            var closest = Double.MAX_VALUE
            var heading = 0.0
            for(i in 0 until array.length()-1) {
                val a = array.getJSONArray(i)
                val b = array.getJSONArray(i+1)
                val aLat = a.getDouble(0);val aLon = a.getDouble(1)
                val bLat = b.getDouble(0);val bLon = b.getDouble(1)
                val distance = GeoMath.toSegmentMeters(lat,lon,aLat,aLon,bLat,bLon)
                if(distance < closest) {
                    closest = distance
                    heading = GeoMath.bearing(aLat,aLon,bLat,bLon)
                }
            }
            closest to heading
        } catch (_: Exception) { null }
    }
    private fun nearbySigns(db:SQLiteDatabase,location:Location,range:Double):List<OfflineAlert> {
        val lat = location.latitude;val lon = location.longitude
        val dLat = range / 111320.0
        val dLon = range / (111320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.05))
        val result = ArrayList<OfflineAlert>()
        // User-imported signs and independently fetched OSM signs coexist.
        for(table in listOf("signs", "osm_signs")) {
            db.rawQuery("SELECT lat,lon,type,value,heading,source FROM $table WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ? LIMIT 250",
                arrayOf((lat-dLat).toString(),(lat+dLat).toString(),
                    (lon-dLon).toString(),(lon+dLon).toString())
            ).use { cursor ->
                while(cursor.moveToNext()) {
                    val signLat=cursor.getDouble(0);val signLon=cursor.getDouble(1)
                    val distance=GeoMath.distanceMeters(lat,lon,signLat,signLon)
                    if(distance > range || distance < 10.0) continue
                    val heading = if(cursor.isNull(4)) null else cursor.getDouble(4)
                    if (!location.hasBearing() || location.speed < 2.0f) continue
                    val bearingToSign = GeoMath.bearing(lat,lon,signLat,signLon)
                    if(GeoMath.angleDelta(location.bearing.toDouble(),bearingToSign)>55.0) continue
                    if(heading != null && GeoMath.angleDelta(location.bearing.toDouble(),heading)>65.0) continue
                    // Prefer signs within the probable travel corridor; not adjacent parallel roads.
                    val rad = Math.toRadians(GeoMath.angleDelta(location.bearing.toDouble(),bearingToSign))
                    val lateralM = distance * kotlin.math.sin(rad)
                    if(lateralM > max(35.0, min(60.0,location.accuracy.toDouble()*1.5))) continue
                    val code = cursor.getInt(2)
                    result.add(OfflineAlert(code,distance.toInt(),cursor.getInt(3),
                        AlertTypes.label(code),cursor.getString(5).orEmpty()))
                }
            }
        }
        return result.sortedBy { it.distanceM }.take(3)
    }
    fun metadata(key:String):String {
        return readableDatabase.rawQuery("SELECT value FROM metadata WHERE key=?",arrayOf(key)).use {
            if(it.moveToFirst())it.getString(0).orEmpty() else ""
        }
    }
}
