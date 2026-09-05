package com.example.cardprobe.net

import com.example.cardprobe.diagnostic.ProbeDiagnostics
import com.example.cardprobe.model.Direction
import com.example.cardprobe.model.ProbeEvent
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local BadVPN udpgw endpoint used by the tun2socks library for UDP/QUIC.
 *
 * This does not decrypt traffic. It only forwards UDP datagrams and emits
 * endpoint/size metadata to ProbeBus.
 */
object UdpGatewayServer {
    const val PORT = 7300

    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private var pool: ExecutorService? = null
    @Volatile private var dnsAddress: InetAddress = InetAddress.getByName("1.1.1.1")

    fun start(preferredDns: InetAddress? = null) {
        preferredDns?.let { if (it.address.size == 4) dnsAddress = it }
        if (!running.compareAndSet(false, true)) return
        pool = Executors.newCachedThreadPool()
        server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT))
        }
        ProbeDiagnostics.setUdpGatewayListening(true, dnsAddress.hostAddress)
        pool!!.execute {
            while (running.get()) {
                try {
                    val socket = server?.accept() ?: break
                    ProbeDiagnostics.udpGatewayAccepted(socket.remoteSocketAddress?.toString())
                    pool?.execute { handleClient(socket) }
                } catch (_: SocketException) {
                    break
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        server = null
        pool?.shutdownNow()
        pool = null
        ProbeDiagnostics.setUdpGatewayListening(false)
    }

    private data class UdpConnection(
        val id: Int,
        val originalAddress: InetAddress,
        val originalPort: Int,
        val actualAddress: InetAddress,
        val actualPort: Int,
        val socket: DatagramSocket
    )

    private fun handleClient(client: Socket) {
        client.tcpNoDelay = true
        val connections = ConcurrentHashMap<Int, UdpConnection>()
        client.use { tcp ->
            val input = BufferedInputStream(tcp.getInputStream())
            val output = BufferedOutputStream(tcp.getOutputStream())

            try {
                while (running.get()) {
                    val frame = UdpgwProtocol.readFrame(input) ?: break
                    if ((frame.flags and UdpgwProtocol.FLAG_KEEPALIVE) != 0) continue

                    val originalAddress = frame.address ?: continue
                    val originalPort = frame.port
                    val dns = (frame.flags and UdpgwProtocol.FLAG_DNS) != 0
                    val actualAddress = if (dns) dnsAddress else originalAddress
                    val actualPort = if (dns) 53 else originalPort
                    val mustRebind = (frame.flags and UdpgwProtocol.FLAG_REBIND) != 0

                    var con = connections[frame.connectionId]
                    if (
                        con == null ||
                        mustRebind ||
                        con.originalAddress != originalAddress ||
                        con.originalPort != originalPort
                    ) {
                        con?.let { closeConnection(it) }
                        con = createConnection(
                            id = frame.connectionId,
                            originalAddress = originalAddress,
                            originalPort = originalPort,
                            actualAddress = actualAddress,
                            actualPort = actualPort,
                            output = output
                        )
                        connections[frame.connectionId] = con
                    }

                    if (frame.payload.isNotEmpty()) {
                        con.socket.send(DatagramPacket(frame.payload, frame.payload.size))
                        ProbeDiagnostics.udpPacket(
                            true,
                            originalAddress.hostAddress ?: originalAddress.toString(),
                            originalPort,
                            frame.payload.size
                        )
                        ProbeBus.emit(
                            ProbeEvent(
                                host = originalAddress.hostAddress ?: originalAddress.toString(),
                                port = originalPort,
                                direction = Direction.UDP_OUT,
                                sizeBytes = frame.payload.size
                            )
                        )
                    }
                }
            } catch (t: Throwable) {
                ProbeDiagnostics.error("UDPGW", t.message ?: t.javaClass.simpleName)
            } finally {
                connections.values.forEach(::closeConnection)
                connections.clear()
            }
        }
    }

    private fun createConnection(
        id: Int,
        originalAddress: InetAddress,
        originalPort: Int,
        actualAddress: InetAddress,
        actualPort: Int,
        output: OutputStream
    ): UdpConnection {
        val udp = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(0))
            connect(actualAddress, actualPort)
            soTimeout = 0
        }
        val con = UdpConnection(id, originalAddress, originalPort, actualAddress, actualPort, udp)
        pool?.execute {
            val buffer = ByteArray(65507)
            try {
                while (running.get() && !udp.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udp.receive(packet)
                    val bytes = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    val framed = UdpgwProtocol.encodeResponse(id, originalAddress, originalPort, bytes)
                    synchronized(output) {
                        output.write(framed)
                        output.flush()
                    }
                    ProbeDiagnostics.udpPacket(
                        false,
                        originalAddress.hostAddress ?: originalAddress.toString(),
                        originalPort,
                        bytes.size
                    )
                    ProbeBus.emit(
                        ProbeEvent(
                            host = originalAddress.hostAddress ?: originalAddress.toString(),
                            port = originalPort,
                            direction = Direction.UDP_IN,
                            sizeBytes = bytes.size
                        )
                    )
                }
            } catch (_: SocketException) {
            } catch (_: Throwable) {
                runCatching { udp.close() }
            }
        }
        return con
    }

    private fun closeConnection(con: UdpConnection) {
        runCatching { con.socket.close() }
    }
}
