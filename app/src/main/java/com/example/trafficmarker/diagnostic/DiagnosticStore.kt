package com.example.trafficmarker.diagnostic

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object DiagnosticStore {
    private const val MAX_LOG = 300

    private val socksClients = AtomicLong(0)
    private val tcpConnectOk = AtomicLong(0)
    private val tcpConnectFail = AtomicLong(0)
    private val tcpOutBytes = AtomicLong(0)
    private val tcpInBytes = AtomicLong(0)
    private val udpGatewayClients = AtomicLong(0)
    private val udpOutPackets = AtomicLong(0)
    private val udpInPackets = AtomicLong(0)
    private val udpOutBytes = AtomicLong(0)
    private val udpInBytes = AtomicLong(0)
    private val busEvents = AtomicLong(0)
    private val busBytes = AtomicLong(0)
    private val logs = ArrayDeque<String>()

    @Volatile private var targetPackage = "-"
    @Volatile private var dns = "-"
    @Volatile private var vpnRequested = false
    @Volatile private var socksListening = false
    @Volatile private var udpGatewayListening = false
    @Volatile private var lastError = "-"

    @Synchronized
    fun reset(target: String, dnsAddress: String?) {
        targetPackage = target
        dns = dnsAddress ?: "-"
        vpnRequested = false
        socksListening = false
        udpGatewayListening = false
        lastError = "-"
        socksClients.set(0)
        tcpConnectOk.set(0)
        tcpConnectFail.set(0)
        tcpOutBytes.set(0)
        tcpInBytes.set(0)
        udpGatewayClients.set(0)
        udpOutPackets.set(0)
        udpInPackets.set(0)
        udpOutBytes.set(0)
        udpInBytes.set(0)
        busEvents.set(0)
        busBytes.set(0)
        logs.clear()
        log("INIT", "Target=" + targetPackage + " DNS=" + dns)
    }

    fun setVpnRequested(value: Boolean) {
        vpnRequested = value
        log("VPN", if (value) "Permintaan start dikirim" else "Stop diminta")
    }

    fun setSocksListening(value: Boolean) {
        socksListening = value
        log("SOCKS", if (value) "Listen 127.0.0.1:10808 aktif" else "Listener berhenti")
    }

    fun setUdpGatewayListening(value: Boolean, dnsAddress: String? = null) {
        if (dnsAddress != null) dns = dnsAddress
        udpGatewayListening = value
        log("UDPGW", if (value) "Listen 127.0.0.1:7300 aktif; DNS=" + dns else "Gateway berhenti")
    }

    fun socksAccepted(remote: String?) {
        val n = socksClients.incrementAndGet()
        log("SOCKS", "Client diterima #" + n + " dari " + (remote ?: "?"))
    }

    fun tcpConnect(host: String, port: Int, success: Boolean, error: String? = null) {
        if (success) {
            tcpConnectOk.incrementAndGet()
            log("TCP", "CONNECT OK " + host + ":" + port)
        } else {
            tcpConnectFail.incrementAndGet()
            val msg = "CONNECT GAGAL " + host + ":" + port + if (error.isNullOrBlank()) "" else " • " + error
            error("TCP", msg)
        }
    }

    fun tcpBytes(outbound: Boolean, host: String, port: Int, bytes: Int) {
        if (outbound) tcpOutBytes.addAndGet(bytes.toLong()) else tcpInBytes.addAndGet(bytes.toLong())
        if (bytes > 0) {
            log(if (outbound) "TCP↑" else "TCP↓", host + ":" + port + " " + bytes + " B")
        }
    }

    fun udpGatewayAccepted(remote: String?) {
        val n = udpGatewayClients.incrementAndGet()
        log("UDPGW", "Client diterima #" + n + " dari " + (remote ?: "?"))
    }

    fun udpPacket(outbound: Boolean, host: String, port: Int, bytes: Int) {
        if (outbound) {
            udpOutPackets.incrementAndGet()
            udpOutBytes.addAndGet(bytes.toLong())
        } else {
            udpInPackets.incrementAndGet()
            udpInBytes.addAndGet(bytes.toLong())
        }
        log(if (outbound) "UDP↑" else "UDP↓", host + ":" + port + " " + bytes + " B")
    }

    fun busEvent(host: String, port: Int, direction: String, bytes: Int) {
        busEvents.incrementAndGet()
        busBytes.addAndGet(bytes.toLong())
        log("BUS", direction + " " + host + ":" + port + " " + bytes + " B")
    }

    fun error(layer: String, message: String) {
        lastError = layer + ": " + message
        log("ERR/" + layer, message)
    }

    @Synchronized
    fun log(layer: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        logs.addFirst(time + " [" + layer + "] " + message)
        while (logs.size > MAX_LOG) logs.removeLast()
    }

    @Synchronized
    fun snapshot(vpnTransportActive: Boolean): String {
        val recent = logs.take(12).joinToString("\n")
        return buildString {
            append("TARGET: ").append(targetPackage).append('\n')
            append("VPN request: ").append(if (vpnRequested) "YA" else "TIDAK")
                .append(" | VPN Android: ").append(if (vpnTransportActive) "AKTIF" else "TIDAK").append('\n')
            append("SOCKS listen: ").append(if (socksListening) "ON" else "OFF")
                .append(" | client: ").append(socksClients.get()).append('\n')
            append("TCP connect: ").append(tcpConnectOk.get()).append(" OK / ")
                .append(tcpConnectFail.get()).append(" gagal").append('\n')
            append("TCP bytes: ↑").append(tcpOutBytes.get()).append(" / ↓").append(tcpInBytes.get()).append('\n')
            append("UDPGW listen: ").append(if (udpGatewayListening) "ON" else "OFF")
                .append(" | client: ").append(udpGatewayClients.get()).append('\n')
            append("UDP paket: ↑").append(udpOutPackets.get()).append(" / ↓").append(udpInPackets.get())
                .append(" | bytes ↑").append(udpOutBytes.get()).append(" / ↓").append(udpInBytes.get()).append('\n')
            append("TrafficBus: ").append(busEvents.get()).append(" event / ").append(busBytes.get()).append(" B").append('\n')
            append("DNS: ").append(dns).append('\n')
            append("Error terakhir: ").append(lastError).append("\n\n")
            append("12 LOG TERAKHIR\n")
            append(if (recent.isBlank()) "(belum ada log)" else recent)
        }
    }
}
