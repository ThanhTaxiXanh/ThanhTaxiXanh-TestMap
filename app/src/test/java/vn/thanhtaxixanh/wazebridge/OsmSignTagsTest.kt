package vn.thanhtaxixanh.wazebridge

import org.junit.Assert.*
import org.junit.Test

class OsmSignTagsTest {
    @Test fun unknownTagsAreNotGuessed() {
        assertNull(OsmSignTags.classify(mapOf("traffic_sign" to "VN:P.127")))
        assertNull(OsmSignTags.classify(mapOf("traffic_sign" to "maxspeed", "maxspeed" to "signals")))
        assertNull(OsmSignTags.classify(mapOf("highway" to "residential")))
    }

    @Test fun numericSpeedSignsAreParsedOnlyWithUnitsOrKmh() {
        assertEquals(76 to 50, OsmSignTags.classify(mapOf("traffic_sign" to "maxspeed", "maxspeed" to "50")))
        assertEquals(76 to 80, OsmSignTags.classify(mapOf("traffic_sign" to "maxspeed", "maxspeed" to "80 km/h")))
        assertNull(OsmSignTags.speedKmh("50 mph"))
        assertNull(OsmSignTags.speedKmh("none"))
        assertNull(OsmSignTags.speedKmh("250"))
    }

    @Test fun landmarksAndCamerasAreClassifiedConservatively() {
        assertEquals(2 to 0, OsmSignTags.classify(mapOf("highway" to "speed_camera")))
        assertEquals(23 to 0, OsmSignTags.classify(mapOf("traffic_sign" to "city_limit")))
        assertEquals(24 to 0, OsmSignTags.classify(mapOf("traffic_sign" to "city_limit", "city_limit" to "end")))
        assertEquals(28 to 0, OsmSignTags.classify(mapOf("traffic_sign" to "no_left_turn")))
        assertEquals(77 to 0, OsmSignTags.classify(mapOf("traffic_sign" to "stop")))
    }

    @Test fun headingsAcceptCompassAndDegreesButNotRoadRelativeWords() {
        assertEquals(270.0, OsmSignTags.heading(mapOf("direction" to "W"))!!, 0.001)
        assertEquals(90.0, OsmSignTags.heading(mapOf("direction" to "90"))!!, 0.001)
        assertNull(OsmSignTags.heading(mapOf("direction" to "forward")))
        assertNull(OsmSignTags.heading(mapOf("direction" to "540")))
    }
}
