package com.example.trafficmarker.net

import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress

object UdpgwProtocol {
    const val FLAG_KEEPALIVE = 1
    const val FLAG_REBIND = 1 shl 1
    const val FLAG_DNS = 1 shl 2
    const val FLAG_IPV6 = 1 shl 3

    data class Frame(
        val flags: Int,
        val connectionId: Int,
        val address: InetAddress?,
        val port: Int,
        val payload: ByteArray
    )

    fun readFrame(input: InputStream): Frame? {
        val l0 = input.read()
        if (l0 < 0) return null
        val l1 = input.read()
        if (l1 < 0) throw EOFException("truncated packetproto length")
        val length = (l0 and 0xff) or ((l1 and 0xff) shl 8)
        if (length < 3 || length > 65535) throw IllegalArgumentException("invalid udpgw frame length: $length")

        val body = readExact(input, length)
        val flags = body[0].toInt() and 0xff
        val conId = (body[1].toInt() and 0xff) or ((body[2].toInt() and 0xff) shl 8)

        if ((flags and FLAG_KEEPALIVE) != 0) {
            return Frame(flags, conId, null, 0, ByteArray(0))
        }

        val addrLen = if ((flags and FLAG_IPV6) != 0) 16 else 4
        val headerLen = 3 + addrLen + 2
        if (body.size < headerLen) throw IllegalArgumentException("truncated udpgw address")

        val address = InetAddress.getByAddress(body.copyOfRange(3, 3 + addrLen))
        val portPos = 3 + addrLen
        val port = ((body[portPos].toInt() and 0xff) shl 8) or (body[portPos + 1].toInt() and 0xff)
        val payload = body.copyOfRange(headerLen, body.size)
        return Frame(flags, conId, address, port, payload)
    }

    fun encodeResponse(
        connectionId: Int,
        originalAddress: InetAddress,
        originalPort: Int,
        payload: ByteArray
    ): ByteArray {
        val addr = originalAddress.address
        val flags = if (addr.size == 16) FLAG_IPV6 else 0
        val bodyLen = 3 + addr.size + 2 + payload.size
        require(bodyLen <= 65535)

        val out = ByteArray(2 + bodyLen)
        out[0] = (bodyLen and 0xff).toByte()
        out[1] = ((bodyLen ushr 8) and 0xff).toByte()
        out[2] = flags.toByte()
        out[3] = (connectionId and 0xff).toByte()
        out[4] = ((connectionId ushr 8) and 0xff).toByte()

        var p = 5
        System.arraycopy(addr, 0, out, p, addr.size)
        p += addr.size
        out[p++] = ((originalPort ushr 8) and 0xff).toByte()
        out[p++] = (originalPort and 0xff).toByte()
        System.arraycopy(payload, 0, out, p, payload.size)
        return out
    }

    private fun readExact(input: InputStream, size: Int): ByteArray {
        val out = ByteArray(size)
        var p = 0
        while (p < size) {
            val n = input.read(out, p, size - p)
            if (n < 0) throw EOFException("truncated udpgw frame")
            p += n
        }
        return out
    }
}
