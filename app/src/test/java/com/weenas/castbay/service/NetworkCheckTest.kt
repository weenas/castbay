package com.weenas.castbay.service

import android.net.nsd.NsdManager
import com.weenas.castbay.service.NetworkCheck.Item
import com.weenas.castbay.service.NetworkCheck.Outcome
import com.weenas.castbay.service.NetworkCheck.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkCheckTest {
    private val wifi = NetworkStatus(NetworkStatus.Type.WIFI, "Home", listOf("192.168.1.20"))
    private val registered = DiscoveryStatus(Registration.Registered(7000), Registration.Registered(7000))

    private fun check(
        state: AirPlayConnectionState = AirPlayConnectionState.Discovering,
        discovery: DiscoveryStatus = registered,
        network: NetworkStatus? = wifi,
        port: Int? = 7000,
        error: String? = null
    ): Map<Item, NetworkCheck.Result> =
        NetworkCheck.classify(NetworkCheck.Input(AirPlayManager.ReceiverSnapshot(state, error, port, discovery), network))
            .associateBy { it.item }

    @Test
    fun aHealthyReceiverPassesEverythingItCanSee() {
        val results = check()
        assertEquals(Item.entries.toSet(), results.keys)
        results.values.filter { it.item != Item.SAME_NETWORK }.forEach { assertEquals(it.item.name, Outcome.PASS, it.outcome) }
        assertEquals("192.168.1.20", results.getValue(Item.NETWORK).text)
        assertEquals("Home", results.getValue(Item.NETWORK).ssid)
        assertEquals(7000, results.getValue(Item.LISTENER).port)
    }

    @Test
    fun theSendersSideIsNeverReportedAsPassing() {
        val external = check().getValue(Item.SAME_NETWORK)
        assertEquals(Outcome.CANT_VERIFY, external.outcome)
        assertEquals(Reason.EXTERNAL, external.reason)
    }

    @Test
    fun eachServiceIsReportedOnItsOwn() {
        val discovery = DiscoveryStatus(
            airplay = Registration.Off,
            raop = Registration.Failed(0),
            lastFailure = DiscoveryFailure(AirPlayDiscoveryAdvertiser.RAOP, 0, 1_000L)
        )
        val results = check(AirPlayConnectionState.Error, discovery, error = "Local network discovery failed")
        assertEquals(Reason.SERVICE_OFF, results.getValue(Item.AIRPLAY_SERVICE).reason)
        val raop = results.getValue(Item.RAOP_SERVICE)
        assertEquals(Outcome.PROBLEM, raop.outcome)
        assertEquals(Reason.SERVICE_FAILED, raop.reason)
        assertEquals(AirPlayDiscoveryAdvertiser.RAOP, raop.serviceType)
        assertEquals(0, raop.errorCode)
        val failure = results.getValue(Item.LAST_FAILURE)
        assertEquals(Reason.FAILURE_ACTIVE, failure.reason)
        assertEquals(1_000L, failure.atMs)
        assertEquals(Reason.RECEIVER_ERROR, results.getValue(Item.RECEIVER).reason)
        assertEquals("Local network discovery failed", results.getValue(Item.RECEIVER).text)
    }

    @Test
    fun aFailureBothServicesRecoveredFromStillShowsButPasses() {
        val discovery = registered.copy(lastFailure = DiscoveryFailure(AirPlayDiscoveryAdvertiser.AIRPLAY, 3, 5L))
        val failure = check(discovery = discovery).getValue(Item.LAST_FAILURE)
        assertEquals(Outcome.PASS, failure.outcome)
        assertEquals(Reason.FAILURE_RECOVERED, failure.reason)
        assertEquals(3, failure.errorCode)
    }

    @Test
    fun registrationsAndroidHasntAnsweredCantBeVerified() {
        val results = check(AirPlayConnectionState.Registering, DiscoveryStatus(Registration.Pending, Registration.Registered(7000)))
        assertEquals(Outcome.CANT_VERIFY, results.getValue(Item.AIRPLAY_SERVICE).outcome)
        assertEquals(Outcome.PASS, results.getValue(Item.RAOP_SERVICE).outcome)
    }

    @Test
    fun missingNetworkInformationIsNotAVerdict() {
        assertEquals(Outcome.CANT_VERIFY, check(network = null).getValue(Item.NETWORK).outcome)
        val noAddress = check(network = NetworkStatus(NetworkStatus.Type.WIFI)).getValue(Item.NETWORK)
        assertEquals(Outcome.CANT_VERIFY, noAddress.outcome)
        assertEquals(Reason.NETWORK_NO_ADDRESS, noAddress.reason)
        val other = check(network = NetworkStatus(NetworkStatus.Type.OTHER, ipv4 = listOf("10.0.0.2"))).getValue(Item.NETWORK)
        assertEquals(Outcome.CANT_VERIFY, other.outcome)
        assertEquals(Outcome.PROBLEM, check(network = NetworkStatus()).getValue(Item.NETWORK).outcome)
    }

    @Test
    fun ethernetPassesWithoutAWifiName() {
        val result = check(network = NetworkStatus(NetworkStatus.Type.ETHERNET, ipv4 = listOf("10.0.0.5", "10.0.0.6"))).getValue(Item.NETWORK)
        assertEquals(Reason.NETWORK_ETHERNET, result.reason)
        assertEquals("10.0.0.5, 10.0.0.6", result.text)
        assertNull(result.ssid)
    }

    @Test
    fun theListenerIsOnlyJudgedWhileTheReceiverRuns() {
        val off = check(AirPlayConnectionState.Idle, DiscoveryStatus(), port = null)
        assertEquals(Outcome.CANT_VERIFY, off.getValue(Item.LISTENER).outcome)
        assertEquals(Reason.RECEIVER_OFF, off.getValue(Item.RECEIVER).reason)
        assertEquals(Reason.SERVICE_OFF, off.getValue(Item.RAOP_SERVICE).reason)
        assertEquals(Reason.NO_FAILURE, off.getValue(Item.LAST_FAILURE).reason)
        val discoveryOnly = check(AirPlayConnectionState.AdvertisingOnly, port = null).getValue(Item.LISTENER)
        assertEquals(Outcome.PROBLEM, discoveryOnly.outcome)
        assertEquals(Reason.LISTENER_MISSING, discoveryOnly.reason)
        assertEquals(Outcome.PASS, check(AirPlayConnectionState.Streaming).getValue(Item.LISTENER).outcome)
    }

    @Test
    fun nsdErrorCodesAreNamed() {
        assertEquals(NetworkCheck.NsdError.INTERNAL, NetworkCheck.nsdError(NsdManager.FAILURE_INTERNAL_ERROR))
        assertEquals(NetworkCheck.NsdError.ALREADY_ACTIVE, NetworkCheck.nsdError(NsdManager.FAILURE_ALREADY_ACTIVE))
        assertEquals(NetworkCheck.NsdError.MAX_LIMIT, NetworkCheck.nsdError(NsdManager.FAILURE_MAX_LIMIT))
        assertEquals(NetworkCheck.NsdError.NOT_RUNNING, NetworkCheck.nsdError(NsdManager.FAILURE_OPERATION_NOT_RUNNING))
        assertEquals(NetworkCheck.NsdError.BAD_PARAMETERS, NetworkCheck.nsdError(NsdManager.FAILURE_BAD_PARAMETERS))
        assertEquals(NetworkCheck.NsdError.OTHER, NetworkCheck.nsdError(42))
        assertEquals(NetworkCheck.NsdError.NO_CODE, NetworkCheck.nsdError(null))
    }
}
