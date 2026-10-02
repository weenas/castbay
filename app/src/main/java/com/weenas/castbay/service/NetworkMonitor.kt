package com.weenas.castbay.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress

/** The TV's current network, as shown on the home screen. */
data class NetworkStatus(
    val type: Type = Type.NONE,
    /** Wi-Fi network name; null when not on Wi-Fi or when Android withholds it (see [NetworkMonitor]). */
    val ssid: String? = null,
    val ipv4: List<String> = emptyList()
) {
    enum class Type { WIFI, ETHERNET, OTHER, NONE }
}

/**
 * Follows the TV's local network: Ethernet, else Wi-Fi, never a VPN. The default network would
 * be a VPN when one is on (as on a Google TV with a VPN app), and its tunnel address isn't one a
 * phone on the same Wi-Fi can cast to. Android only reveals the Wi-Fi name (SSID) to apps
 * holding a location permission, with location services on; without it [NetworkStatus.ssid] is
 * null.
 */
class NetworkMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifi = appContext.getSystemService(WifiManager::class.java)

    private val _status = MutableStateFlow(NetworkStatus())
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    /** Every non-VPN network the device is on, with what is known of each. */
    private val networks = mutableMapOf<Network, Pair<NetworkCapabilities?, LinkProperties?>>()

    private val callback: Callback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ redacts the SSID in callbacks unless location info is requested.
            Callback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
        } else {
            Callback()
        }

    /**
     * NetworkCallback(int flags) only exists from API 31; calling it on older Android throws
     * NoSuchMethodError even with flags = 0, so the older constructor is picked separately.
     */
    private inner class Callback : ConnectivityManager.NetworkCallback {
        constructor() : super()

        @RequiresApi(Build.VERSION_CODES.S)
        constructor(flags: Int) : super(flags)

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            synchronized(networks) { networks[network] = caps to networks[network]?.second }
            publish()
        }

        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            synchronized(networks) { networks[network] = networks[network]?.first to properties }
            publish()
        }

        override fun onLost(network: Network) {
            synchronized(networks) { networks.remove(network) }
            publish()
        }
    }

    private var registered = false

    fun start() {
        if (registered) return
        registered = true
        publish()
        // Reports each network already up, then changes. NOT_VPN leaves VPNs out.
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
        connectivity.registerNetworkCallback(request, callback)
    }

    fun stop() {
        if (!registered) return
        registered = false
        connectivity.unregisterNetworkCallback(callback)
    }

    /** Re-reads the network, e.g. after the location permission was granted. */
    fun refresh() {
        synchronized(networks) {
            networks.keys.toList().forEach { network ->
                val (caps, links) = networks[network] ?: return@forEach
                networks[network] = (connectivity.getNetworkCapabilities(network) ?: caps) to
                    (connectivity.getLinkProperties(network) ?: links)
            }
        }
        publish()
    }

    fun canReadSsid(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun publish() {
        // The local network a phone casts over: Ethernet first, then Wi-Fi, then anything else.
        val (caps, links) = synchronized(networks) {
            networks.values
                .filter { (c, _) -> c != null && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
                .minByOrNull { (c, _) ->
                    when {
                        c!!.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 0
                        c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1
                        else -> 2
                    }
                }
        } ?: (null to null)
        val type = when {
            caps == null -> NetworkStatus.Type.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkStatus.Type.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.Type.WIFI
            else -> NetworkStatus.Type.OTHER
        }
        _status.value = NetworkStatus(
            type = type,
            ssid = if (type == NetworkStatus.Type.WIFI) readSsid(caps) else null,
            ipv4 = ipv4Addresses(links?.linkAddresses.orEmpty().map { it.address })
        )
    }

    @Suppress("DEPRECATION")
    private fun readSsid(caps: NetworkCapabilities?): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cleanSsid((caps?.transportInfo as? WifiInfo)?.ssid)?.let { return it }
        }
        // Deprecated in API 31 but still the only way before it.
        return cleanSsid(wifi?.connectionInfo?.ssid)
    }

    companion object {
        /** Android quotes SSIDs and reports "<unknown ssid>" when it withholds them. */
        fun cleanSsid(raw: String?): String? {
            val value = raw?.trim() ?: return null
            if (value.isEmpty() || value == WifiManager.UNKNOWN_SSID || value == "0x") return null
            return value.removeSurrounding("\"").takeIf { it.isNotBlank() }
        }

        /** Usable IPv4 addresses: senders on the LAN reach the TV through these. */
        fun ipv4Addresses(addresses: List<InetAddress>): List<String> =
            addresses.filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
    }
}
