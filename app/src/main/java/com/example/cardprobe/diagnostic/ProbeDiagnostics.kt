package com.example.cardprobe.diagnostic

import java.util.concurrent.atomic.AtomicLong

object ProbeDiagnostics {
    private val tcpOk = AtomicLong(0)
    private val tcpFail = AtomicLong(0)
    private val tcpIn = AtomicLong(0)
    private val tcpOut = AtomicLong(0)
    private val udpIn = AtomicLong(0)
    private val udpOut = AtomicLong(0)
    private val events = AtomicLong(0)

    @Volatile private var target = "-"
    @Volatile private var socks = false
    @Volatile private var udpgw = false
    @Volatile private var vpnRequested = false
    @Volatile private var lastError = "-"

    fun reset(targetPackage: String) {
        target = targetPackage
        tcpOk.set(0)
        tcpFail.set(0)
        tcpIn.set(0)
        tcpOut.set(0)
        udpIn.set(0)
        udpOut.set(0)
        events.set(0)
        lastError = "-"
    }

    fun setVpnRequested(value: Boolean) { vpnRequested = value }
    fun setSocksListening(value: Boolean) { socks = value }
    fun setUdpGatewayListening(value: Boolean, dnsAddress: String? = null) { udpgw = value }
    fun socksAccepted(remote: String?) {}
    fun udpGatewayAccepted(remote: String?) {}

    fun tcpConnect(host: String, port: Int, success: Boolean, error: String? = null) {
        if (success) tcpOk.incrementAndGet() else {
            tcpFail.incrementAndGet()
            if (!error.isNullOrBlank()) lastError = error
        }
    }

    fun tcpBytes(outbound: Boolean, host: String, port: Int, bytes: Int) {
        if (outbound) tcpOut.addAndGet(bytes.toLong()) else tcpIn.addAndGet(bytes.toLong())
    }

    fun udpPacket(outbound: Boolean, host: String, port: Int, bytes: Int) {
        if (outbound) udpOut.addAndGet(bytes.toLong()) else udpIn.addAndGet(bytes.toLong())
    }

    fun busEvent(bytes: Int) {
        events.incrementAndGet()
    }

    fun error(layer: String, message: String) {
        lastError = layer + ": " + message
    }

    fun snapshot(vpnActive: Boolean): String =
        "TARGET: " + target + "\n" +
            "VPN request: " + if (vpnRequested) "YA" else "TIDAK" +
            " • Android VPN: " + if (vpnActive) "AKTIF" else "TIDAK" + "\n" +
            "SOCKS: " + if (socks) "ON" else "OFF" +
            " • TCP " + tcpOk.get() + " OK / " + tcpFail.get() + " gagal\n" +
            "TCP bytes ↑" + tcpOut.get() + " / ↓" + tcpIn.get() + "\n" +
            "UDPGW: " + if (udpgw) "ON" else "OFF" +
            " • UDP bytes ↑" + udpOut.get() + " / ↓" + udpIn.get() + "\n" +
            "Event metadata: " + events.get() + "\n" +
            "Error: " + lastError
}
