package com.example.trafficmarker.net

import com.example.trafficmarker.model.Direction
import com.example.trafficmarker.model.TrafficEvent
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object LocalSocksServer {
    const val PORT = 10808
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private var pool: ExecutorService? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        pool = Executors.newCachedThreadPool()
        server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getLoopbackAddress(), PORT))
        }
        pool!!.execute {
            while (running.get()) {
                try {
                    val socket = server?.accept() ?: break
                    pool?.execute { handleClient(socket) }
                } catch (_: SocketException) {
                    break
                } catch (_: Throwable) { }
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        server = null
        pool?.shutdownNow()
        pool = null
    }

    private fun handleClient(client: Socket) {
        client.tcpNoDelay = true
        client.use {
            val input = BufferedInputStream(it.getInputStream())
            val output = BufferedOutputStream(it.getOutputStream())
            if (readU8(input) != 0x05) return
            val nMethods = readU8(input)
            repeat(nMethods) { readU8(input) }
            output.write(byteArrayOf(0x05, 0x00)); output.flush()

            if (readU8(input) != 0x05) return
            val cmd = readU8(input)
            readU8(input) // RSV
            val atyp = readU8(input)
            val host = readAddress(input, atyp)
            val port = readU16(input)

            when (cmd) {
                0x01 -> handleConnect(input, output, host, port)
                0x03 -> handleUdpAssociate(client, input, output)
                else -> reply(output, 0x07, null, 0)
            }
        }
    }

    private fun handleConnect(clientIn: InputStream, clientOut: OutputStream, host: String, port: Int) {
        val remote = try {
            Socket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(host, port), 12_000)
            }
        } catch (_: Throwable) {
            reply(clientOut, 0x05, null, 0)
            return
        }

        remote.use { r ->
            reply(clientOut, 0x00, r.localAddress, r.localPort)
            TrafficBus.emit(TrafficEvent(host = host, port = port, direction = Direction.CONNECT, sizeBytes = 0))

            val remoteIn = BufferedInputStream(r.getInputStream())
            val remoteOut = BufferedOutputStream(r.getOutputStream())
            val executor = pool ?: return
            val t = executor.submit {
                relay(clientIn, remoteOut, host, port, Direction.OUT)
                runCatching { r.shutdownOutput() }
            }
            relay(remoteIn, clientOut, host, port, Direction.IN)
            runCatching { t.get() }
        }
    }

    private fun relay(input: InputStream, output: OutputStream, host: String, port: Int, direction: Direction) {
        val buffer = ByteArray(16 * 1024)
        try {
            while (running.get()) {
                val n = input.read(buffer)
                if (n <= 0) break
                output.write(buffer, 0, n)
                output.flush()
                TrafficBus.emit(TrafficEvent(host = host, port = port, direction = direction, sizeBytes = n))
            }
        } catch (_: Throwable) { }
    }

    private fun handleUdpAssociate(control: Socket, controlIn: InputStream, controlOut: OutputStream) {
        val udp = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(0))
        }
        reply(controlOut, 0x00, InetAddress.getLoopbackAddress(), udp.localPort)

        val remoteToHost = ConcurrentHashMap<String, Pair<String, Int>>()
        val worker = (pool ?: return).submit {
            var clientEndpoint: SocketAddress? = null
            val buffer = ByteArray(65535)
            try {
                while (running.get() && !udp.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udp.receive(packet)
                    val sender = packet.socketAddress
                    if (clientEndpoint == null || sender == clientEndpoint) {
                        clientEndpoint = sender
                        val parsed = parseUdpRequest(packet.data, packet.offset, packet.length) ?: continue
                        val (host, port, payloadOffset, payloadLen) = parsed
                        val addr = InetAddress.getByName(host)
                        udp.send(DatagramPacket(packet.data, payloadOffset, payloadLen, addr, port))
                        remoteToHost["${addr.hostAddress}:$port"] = host to port
                        TrafficBus.emit(TrafficEvent(host = host, port = port, direction = Direction.UDP_OUT, sizeBytes = payloadLen))
                    } else {
                        val client = clientEndpoint ?: continue
                        val key = "${packet.address.hostAddress}:${packet.port}"
                        val hp = remoteToHost[key] ?: (packet.address.hostAddress to packet.port)
                        val wrapped = buildUdpResponse(packet.address, packet.port, packet.data, packet.offset, packet.length)
                        udp.send(DatagramPacket(wrapped, wrapped.size, client))
                        TrafficBus.emit(TrafficEvent(host = hp.first, port = hp.second, direction = Direction.UDP_IN, sizeBytes = packet.length))
                    }
                }
            } catch (_: Throwable) { }
        }

        try {
            while (running.get() && controlIn.read() >= 0) { /* SOCKS UDP control channel stays open */ }
        } catch (_: Throwable) {
        } finally {
            udp.close()
            worker.cancel(true)
        }
    }

    private data class UdpParsed(val host: String, val port: Int, val payloadOffset: Int, val payloadLen: Int)

    private fun parseUdpRequest(data: ByteArray, offset: Int, length: Int): UdpParsed? {
        if (length < 7) return null
        var p = offset
        if (data[p++].toInt() != 0 || data[p++].toInt() != 0) return null
        if (data[p++].toInt() != 0) return null // FRAG unsupported
        val atyp = data[p++].toInt() and 0xff
        val host: String
        when (atyp) {
            1 -> {
                if (p + 4 > offset + length) return null
                host = InetAddress.getByAddress(data.copyOfRange(p, p + 4)).hostAddress ?: return null
                p += 4
            }
            3 -> {
                val n = data[p++].toInt() and 0xff
                if (p + n > offset + length) return null
                host = String(data, p, n, Charsets.UTF_8); p += n
            }
            4 -> {
                if (p + 16 > offset + length) return null
                host = InetAddress.getByAddress(data.copyOfRange(p, p + 16)).hostAddress ?: return null
                p += 16
            }
            else -> return null
        }
        if (p + 2 > offset + length) return null
        val port = ((data[p].toInt() and 0xff) shl 8) or (data[p + 1].toInt() and 0xff); p += 2
        return UdpParsed(host, port, p, offset + length - p)
    }

    private fun buildUdpResponse(address: InetAddress, port: Int, data: ByteArray, offset: Int, length: Int): ByteArray {
        val addr = address.address
        val atyp = if (addr.size == 4) 1 else 4
        return ByteArray(3 + 1 + addr.size + 2 + length).also { out ->
            var p = 0
            out[p++] = 0; out[p++] = 0; out[p++] = 0
            out[p++] = atyp.toByte()
            System.arraycopy(addr, 0, out, p, addr.size); p += addr.size
            out[p++] = (port ushr 8).toByte(); out[p++] = port.toByte()
            System.arraycopy(data, offset, out, p, length)
        }
    }

    private fun reply(out: OutputStream, code: Int, address: InetAddress?, port: Int) {
        val addr = (address ?: InetAddress.getByName("0.0.0.0")).address
        val atyp = if (addr.size == 4) 1 else 4
        out.write(byteArrayOf(0x05, code.toByte(), 0x00, atyp.toByte()))
        out.write(addr)
        out.write(byteArrayOf((port ushr 8).toByte(), port.toByte()))
        out.flush()
    }

    private fun readAddress(input: InputStream, atyp: Int): String = when (atyp) {
        1 -> InetAddress.getByAddress(readExact(input, 4)).hostAddress ?: "0.0.0.0"
        3 -> String(readExact(input, readU8(input)), Charsets.UTF_8)
        4 -> InetAddress.getByAddress(readExact(input, 16)).hostAddress ?: "::"
        else -> throw EOFException("Unsupported ATYP")
    }

    private fun readU8(input: InputStream): Int = input.read().also { if (it < 0) throw EOFException() }
    private fun readU16(input: InputStream): Int = (readU8(input) shl 8) or readU8(input)
    private fun readExact(input: InputStream, n: Int): ByteArray {
        val b = ByteArray(n); var p = 0
        while (p < n) { val r = input.read(b, p, n - p); if (r < 0) throw EOFException(); p += r }
        return b
    }
}
