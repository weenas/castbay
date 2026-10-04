package com.weenas.castbay.service

import android.content.Context
import android.net.ConnectivityManager
import com.weenas.castbay.util.Diagnostics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * About → Diagnostics → Run network check: why a phone may not find the receiver.
 * It only reads what the receiver already knows (its state, the network, what NsdManager said
 * about each DNS-SD registration), so it never restarts anything or interrupts a cast. What
 * the TV can't see from its side, such as whether the phone got the multicast announcement or
 * the router isolates Wi-Fi clients, is always reported as unverified.
 */
object NetworkCheck {
    enum class Outcome { PASS, PROBLEM, CANT_VERIFY }

    enum class Item { RECEIVER, NETWORK, AIRPLAY_SERVICE, RAOP_SERVICE, LISTENER, LAST_FAILURE, SAME_NETWORK }

    /** Each reason has its own explanation (and, for most, a next step) on screen. */
    enum class Reason {
        RECEIVER_RUNNING, RECEIVER_OFF, RECEIVER_ERROR,
        NETWORK_WIFI, NETWORK_ETHERNET, NETWORK_OTHER, NETWORK_NO_ADDRESS, NETWORK_NONE, NETWORK_UNKNOWN,
        SERVICE_REGISTERED, SERVICE_PENDING, SERVICE_FAILED, SERVICE_OFF,
        LISTENER_OK, LISTENER_MISSING, LISTENER_UNKNOWN,
        NO_FAILURE, FAILURE_RECOVERED, FAILURE_ACTIVE,
        EXTERNAL
    }

    /** What NsdManager's error code means; its constants, as numbers since some are API 34+. */
    enum class NsdError { INTERNAL, ALREADY_ACTIVE, MAX_LIMIT, NOT_RUNNING, BAD_PARAMETERS, OTHER, NO_CODE }

    data class Result(
        val item: Item,
        val outcome: Outcome,
        val reason: Reason,
        /** The receiver's error, or the network's addresses (comma-separated). */
        val text: String? = null,
        /** The Wi-Fi name, when Android reveals it. */
        val ssid: String? = null,
        val port: Int? = null,
        val serviceType: String? = null,
        val errorCode: Int? = null,
        val atMs: Long? = null
    )

    data class Input(
        val receiver: AirPlayManager.ReceiverSnapshot,
        /** Null when the network couldn't be read. */
        val network: NetworkStatus?
    )

    fun classify(input: Input): List<Result> {
        val receiver = input.receiver
        val running = receiver.state != AirPlayConnectionState.Idle && receiver.state != AirPlayConnectionState.Error
        val discovery = receiver.discovery
        return listOf(
            receiverResult(receiver),
            networkResult(input.network),
            serviceResult(Item.AIRPLAY_SERVICE, AirPlayDiscoveryAdvertiser.AIRPLAY, discovery.airplay),
            serviceResult(Item.RAOP_SERVICE, AirPlayDiscoveryAdvertiser.RAOP, discovery.raop),
            listenerResult(running, receiver.listenerPort),
            failureResult(discovery),
            Result(Item.SAME_NETWORK, Outcome.CANT_VERIFY, Reason.EXTERNAL)
        )
    }

    fun nsdError(code: Int?): NsdError = when (code) {
        null -> NsdError.NO_CODE
        0 -> NsdError.INTERNAL
        3 -> NsdError.ALREADY_ACTIVE
        4 -> NsdError.MAX_LIMIT
        5 -> NsdError.NOT_RUNNING
        6 -> NsdError.BAD_PARAMETERS
        else -> NsdError.OTHER
    }

    private fun receiverResult(receiver: AirPlayManager.ReceiverSnapshot) = when (receiver.state) {
        AirPlayConnectionState.Idle -> Result(Item.RECEIVER, Outcome.PROBLEM, Reason.RECEIVER_OFF)
        AirPlayConnectionState.Error -> Result(Item.RECEIVER, Outcome.PROBLEM, Reason.RECEIVER_ERROR, text = receiver.error)
        else -> Result(Item.RECEIVER, Outcome.PASS, Reason.RECEIVER_RUNNING)
    }

    private fun networkResult(network: NetworkStatus?): Result {
        if (network == null) return Result(Item.NETWORK, Outcome.CANT_VERIFY, Reason.NETWORK_UNKNOWN)
        if (network.type == NetworkStatus.Type.NONE) return Result(Item.NETWORK, Outcome.PROBLEM, Reason.NETWORK_NONE)
        // Senders find and reach the TV over IPv4; without an address the TV may still be joining.
        if (network.ipv4.isEmpty()) return Result(Item.NETWORK, Outcome.CANT_VERIFY, Reason.NETWORK_NO_ADDRESS)
        val addresses = network.ipv4.joinToString(", ")
        return when (network.type) {
            NetworkStatus.Type.WIFI -> Result(Item.NETWORK, Outcome.PASS, Reason.NETWORK_WIFI, text = addresses, ssid = network.ssid)
            NetworkStatus.Type.ETHERNET -> Result(Item.NETWORK, Outcome.PASS, Reason.NETWORK_ETHERNET, text = addresses)
            // Mobile data, a tether…: senders on the home Wi-Fi may not be on it.
            else -> Result(Item.NETWORK, Outcome.CANT_VERIFY, Reason.NETWORK_OTHER, text = addresses)
        }
    }

    private fun serviceResult(item: Item, type: String, registration: Registration) = when (registration) {
        is Registration.Registered -> Result(item, Outcome.PASS, Reason.SERVICE_REGISTERED, serviceType = type, port = registration.port)
        Registration.Pending -> Result(item, Outcome.CANT_VERIFY, Reason.SERVICE_PENDING, serviceType = type)
        is Registration.Failed -> Result(item, Outcome.PROBLEM, Reason.SERVICE_FAILED, serviceType = type, errorCode = registration.errorCode)
        Registration.Off -> Result(item, Outcome.PROBLEM, Reason.SERVICE_OFF, serviceType = type)
    }

    private fun listenerResult(running: Boolean, port: Int?) = when {
        !running -> Result(Item.LISTENER, Outcome.CANT_VERIFY, Reason.LISTENER_UNKNOWN)
        port != null -> Result(Item.LISTENER, Outcome.PASS, Reason.LISTENER_OK, port = port)
        // Only the discovery probe is up: phones list the TV but can't cast to it.
        else -> Result(Item.LISTENER, Outcome.PROBLEM, Reason.LISTENER_MISSING)
    }

    private fun failureResult(discovery: DiscoveryStatus): Result {
        val failure = discovery.lastFailure ?: return Result(Item.LAST_FAILURE, Outcome.PASS, Reason.NO_FAILURE)
        // Both services registered since: the failure is history, kept for the report.
        val recovered = discovery.airplay is Registration.Registered && discovery.raop is Registration.Registered
        return Result(
            Item.LAST_FAILURE,
            if (recovered) Outcome.PASS else Outcome.PROBLEM,
            if (recovered) Reason.FAILURE_RECOVERED else Reason.FAILURE_ACTIVE,
            serviceType = failure.serviceType,
            errorCode = failure.errorCode,
            atMs = failure.atMs
        )
    }

    /** Reads the receiver and the network (waiting briefly for the latter) and records the outcome. Main thread. */
    suspend fun run(context: Context): List<Result> {
        val receiver = AirPlayManager.getInstance(context).snapshot()
        val monitor = NetworkMonitor(context)
        val network = try {
            monitor.start()
            // NetworkMonitor learns of networks through callbacks that come right after it starts;
            // a network's addresses can follow its type.
            withTimeoutOrNull(NETWORK_WAIT_MS) { monitor.status.first { it.ipv4.isNotEmpty() } }
                ?: monitor.status.value.takeIf { it.type != NetworkStatus.Type.NONE }
                ?: noNetworkOrUnknown(context)
        } catch (error: Exception) {
            null
        } finally {
            runCatching { monitor.stop() }
        }
        val results = classify(Input(receiver, network))
        val counts = results.groupingBy { it.outcome }.eachCount()
        val problems = results.filter { it.outcome == Outcome.PROBLEM }.joinToString { it.reason.name.lowercase() }
        Diagnostics.record(
            "check",
            "Network check: ${counts[Outcome.PASS] ?: 0} pass, ${counts[Outcome.PROBLEM] ?: 0} problem, " +
                "${counts[Outcome.CANT_VERIFY] ?: 0} can't verify" + if (problems.isNotEmpty()) " ($problems)" else ""
        )
        return results
    }

    /** No non-VPN network reported: none at all if Android has no default network either. */
    private fun noNetworkOrUnknown(context: Context): NetworkStatus? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return if (connectivity.activeNetwork == null) NetworkStatus(NetworkStatus.Type.NONE) else null
    }

    private const val NETWORK_WAIT_MS = 2000L
}
