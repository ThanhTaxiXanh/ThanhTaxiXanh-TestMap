package vn.thanhtaxixanh.wazebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test fun distanceOnLineIsNearZero() {
        val d=GeoMath.toSegmentMeters(21.0,106.0,21.0,105.999,21.0,106.001)
        assertTrue(d < 0.5)
    }
    @Test fun distanceFromLineIsNonZero() {
        val d=GeoMath.toSegmentMeters(21.001,106.0,21.0,105.999,21.0,106.001)
        assertTrue(d > 100 && d < 115)
    }
    @Test fun bearingAndAngleDifference() {
        val east=GeoMath.bearing(21.0,106.0,21.0,106.001)
        assertEquals(90.0,east,0.2)
        assertEquals(20.0,GeoMath.angleDelta(350.0,10.0),0.001)
        assertEquals(180.0,GeoMath.angleDelta(0.0,180.0),0.001)
    }
}
