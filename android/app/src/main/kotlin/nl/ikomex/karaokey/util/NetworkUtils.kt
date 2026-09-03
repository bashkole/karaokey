package nl.ikomex.karaokey.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun localIpAddress(): String? {
        return NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    fun guestUrl(port: Int): String {
        val ip = localIpAddress() ?: "localhost"
        return "http://$ip:$port/"
    }

    fun stickHostAddress(port: Int): String {
        val ip = localIpAddress() ?: error("Could not detect Fire Stick IP address")
        return "$ip:$port"
    }
}
