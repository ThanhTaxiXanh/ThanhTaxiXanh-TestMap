package vn.thanhtaxixanh.wazebridge

import android.os.Bundle
import android.os.SystemClock

/** Snapshot only; not a country-wide GIS database. */
data class WazeAlert(
    val type: Int,
    val distanceM: Int,
    val value: Int,
    val jamSeverity: Int,
    val jamDelayMin: Int
) {
    val label: String get() = AlertTypes.label(type)
}
data class WazeSnapshot(
    val version: Int,
    val measuredAtElapsedMs: Long,
    val speedKmh: Int,
    val limitKmh: Int,
    val minSpeedKmh: Int?,
    val overspeed: Boolean,
    val street: String,
    val navigating: Boolean,
    val alerts: List<WazeAlert>,
    val nextStreet: String,
    val turnDistanceM: Int?
) {
    val ageMs get() = (SystemClock.elapsedRealtime() - measuredAtElapsedMs).coerceAtLeast(0)
    val stale get() = ageMs > 5_000

    companion object {
        @Suppress("DEPRECATION")
        fun from(src: Bundle): WazeSnapshot {
            src.classLoader = Bundle::class.java.classLoader
            val alerts = src.getParcelableArray("alerts")?.filterIsInstance<Bundle>()?.map {
                WazeAlert(
                    it.getInt("type"), it.getInt("distance_m"), it.getInt("value"),
                    it.getInt("jam_severity"), it.getInt("jam_delay_min")
                )
            }.orEmpty()
            return WazeSnapshot(
                version = src.getInt("api", 0),
                measuredAtElapsedMs = src.getLong("timestamp", 0),
                speedKmh = src.getInt("speed", 0),
                limitKmh = src.getInt("speed_limit", 0),
                minSpeedKmh = if (src.containsKey("speed_min")) src.getInt("speed_min") else null,
                overspeed = src.getBoolean("overspeed", false),
                street = src.getString("current_street").orEmpty(),
                navigating = src.getBoolean("navigating", false),
                alerts = alerts,
                nextStreet = src.getString("next_street").orEmpty(),
                turnDistanceM = if (src.containsKey("turn_distance_m")) src.getInt("turn_distance_m") else null
            )
        }
    }
}
