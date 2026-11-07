package vn.thanhtaxixanh.wazebridge

import kotlin.math.*

/** Only measurements: no inferred legal speed limits or synthetic map data. */
object GeoMath {
    private const val EARTH_M = 6371008.8
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latDelta = Math.toRadians(lat2 - lat1)
        val lonDelta = Math.toRadians(lon2 - lon1)
        val a = sin(latDelta / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(lonDelta / 2).pow(2.0)
        return 2.0 * EARTH_M * atan2(sqrt(a.coerceIn(0.0, 1.0)), sqrt((1.0-a).coerceAtLeast(0.0)))
    }
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a = Math.toRadians(lat1)
        val b = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val x = sin(dl) * cos(b)
        val y = cos(a) * sin(b) - sin(a) * cos(b) * cos(dl)
        return (Math.toDegrees(atan2(x,y)) + 360.0) % 360.0
    }
    fun angleDelta(a: Double, b: Double): Double =
        abs((a - b + 540.0) % 360.0 - 180.0)

    /** Return perpendicular distance from GPS point to a road geometry edge (meters). */
    fun toSegmentMeters(lat:Double, lon:Double, aLat:Double, aLon:Double,
                        bLat:Double, bLon:Double):Double {
        val scaleX = 111320.0 * cos(Math.toRadians(lat))
        val ax = (aLon - lon) * scaleX
        val ay = (aLat - lat) * 111320.0
        val bx = (bLon - lon) * scaleX
        val by = (bLat - lat) * 111320.0
        val dx = bx - ax
        val dy = by - ay
        val length2 = dx*dx + dy*dy
        val t = if (length2 <= 0.000001) 0.0 else ((-ax*dx-ay*dy)/length2).coerceIn(0.0,1.0)
        return hypot(ax+dx*t, ay+dy*t)
    }
}
