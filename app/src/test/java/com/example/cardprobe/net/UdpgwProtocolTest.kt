package com.example.cardprobe.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.InetAddress

class UdpgwProtocolTest {
    @Test
    fun parsesIpv4Request() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val bodyLen = 3 + 4 + 2 + payload.size
        val raw = byteArrayOf(
            (bodyLen and 0xff).toByte(), ((bodyLen ushr 8) and 0xff).toByte(),
            UdpgwProtocol.FLAG_REBIND.toByte(),
            0x34, 0x12,
            1, 2, 3, 4,
            0x01, 0xBB.toByte(),
            *payload
        )
        val f = UdpgwProtocol.readFrame(ByteArrayInputStream(raw))
        assertNotNull(f)
        assertEquals(0x1234, f!!.connectionId)
        assertEquals("1.2.3.4", f.address!!.hostAddress)
        assertEquals(443, f.port)
        assertArrayEquals(payload, f.payload)
    }

    @Test
    fun encodesResponse() {
        val raw = UdpgwProtocol.encodeResponse(
            7,
            InetAddress.getByName("8.8.8.8"),
            53,
            byteArrayOf(9, 8, 7)
        )
        val f = UdpgwProtocol.readFrame(ByteArrayInputStream(raw))
        assertEquals(7, f!!.connectionId)
        assertArrayEquals(byteArrayOf(9, 8, 7), f.payload)
    }
}
