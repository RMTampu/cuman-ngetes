package com.example.trafficmarker.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerTest {
    private val marker = Marker(
        id = "m1",
        host = "203.0.113.10",
        port = 443,
        direction = Direction.OUT,
        centerSize = 1000,
        tolerancePercent = 25
    )

    @Test
    fun exactFingerprintMatches() {
        assertTrue(marker.matches(TrafficEvent(host = "203.0.113.10", port = 443, direction = Direction.OUT, sizeBytes = 1000)))
    }

    @Test
    fun hostComparisonIsCaseInsensitive() {
        val domain = marker.copy(host = "API.EXAMPLE.COM")
        assertTrue(domain.matches(TrafficEvent(host = "api.example.com", port = 443, direction = Direction.OUT, sizeBytes = 1000)))
    }

    @Test
    fun wrongEndpointOrDirectionDoesNotMatch() {
        assertFalse(marker.matches(TrafficEvent(host = "203.0.113.11", port = 443, direction = Direction.OUT, sizeBytes = 1000)))
        assertFalse(marker.matches(TrafficEvent(host = "203.0.113.10", port = 8443, direction = Direction.OUT, sizeBytes = 1000)))
        assertFalse(marker.matches(TrafficEvent(host = "203.0.113.10", port = 443, direction = Direction.IN, sizeBytes = 1000)))
    }

    @Test
    fun sizeToleranceIsApplied() {
        assertTrue(marker.matches(TrafficEvent(host = "203.0.113.10", port = 443, direction = Direction.OUT, sizeBytes = 1250)))
        assertFalse(marker.matches(TrafficEvent(host = "203.0.113.10", port = 443, direction = Direction.OUT, sizeBytes = 1251)))
    }

    @Test
    fun zeroSizedConnectMarkerActsAsConnectionSignature() {
        val connection = marker.copy(direction = Direction.CONNECT, centerSize = 0)
        assertTrue(connection.matches(TrafficEvent(host = "203.0.113.10", port = 443, direction = Direction.CONNECT, sizeBytes = 0)))
    }

    @Test
    fun minimumToleranceIs64Bytes() {
        val tiny = marker.copy(centerSize = 100, tolerancePercent = 1)
        assertTrue(tiny.matches(TrafficEvent(host = "203.0.113.10", port = 443, direction = Direction.OUT, sizeBytes = 164)))
        assertFalse(tiny.matches(TrafficEvent(host = "203.0.113.10", port = 443, direction = Direction.OUT, sizeBytes = 165)))
    }
}
