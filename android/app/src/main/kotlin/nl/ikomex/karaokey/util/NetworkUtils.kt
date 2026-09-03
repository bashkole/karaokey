package nl.ikomex.karaokey.util

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun localIpAddress(context: Context? = null): String? {
        context?.let { ctx ->
            val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = connectivityManager?.activeNetwork
            val linkProperties = connectivityManager?.getLinkProperties(network)
            linkProperties?.linkAddresses?.forEach { linkAddress ->
                val address = linkAddress.address
                if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                    return address.hostAddress
                }
            }
        }

        return NetworkInterface.getNetworkInterfaces().toList()
            .sortedByDescending { it.displayName.startsWith("wlan", ignoreCase = true) }
            .flatMap { networkInterface -> networkInterface.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }

    fun guestUrl(port: Int, context: Context? = null): String {
        val ip = localIpAddress(context) ?: "localhost"
        return "http://$ip:$port/"
    }

    fun stickHostAddress(port: Int, context: Context? = null): String {
        val ip = localIpAddress(context)
            ?: error("Could not detect Fire Stick IP address. Check Wi-Fi connection.")
        return "$ip:$port"
    }
}
