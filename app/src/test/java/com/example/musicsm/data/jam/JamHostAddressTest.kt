package com.example.musicsm.data.jam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The manual-address path is the fallback used precisely when nothing else works, so it has to
 * accept what a flustered user actually types and reject what cannot possibly connect.
 */
class JamHostAddressTest {

    private val defaultPort = 47_655

    @Test
    fun `bare address takes the default port`() {
        assertEquals("192.168.1.5" to defaultPort, parseHostAddress("192.168.1.5", defaultPort))
    }

    @Test
    fun `explicit port wins over the default`() {
        assertEquals("10.0.0.2" to 41234, parseHostAddress("10.0.0.2:41234", defaultPort))
    }

    @Test
    fun `surrounding whitespace is forgiven`() {
        assertEquals("192.168.43.1" to defaultPort, parseHostAddress("  192.168.43.1  ", defaultPort))
    }

    @Test
    fun `a pasted url still resolves to its address`() {
        assertEquals("192.168.1.5" to 8080, parseHostAddress("http://192.168.1.5:8080/", defaultPort))
    }

    @Test
    fun `hostnames are rejected rather than left to stall on a dns lookup`() {
        assertNull(parseHostAddress("my-phone.local", defaultPort))
    }

    @Test
    fun `an octet above 255 is not an address`() {
        assertNull(parseHostAddress("192.168.1.300", defaultPort))
    }

    @Test
    fun `too few octets is not an address`() {
        assertNull(parseHostAddress("192.168.1", defaultPort))
    }

    @Test
    fun `a port outside the valid range is rejected`() {
        assertNull(parseHostAddress("192.168.1.5:70000", defaultPort))
        assertNull(parseHostAddress("192.168.1.5:0", defaultPort))
    }

    @Test
    fun `a non-numeric port is rejected rather than silently defaulted`() {
        assertNull(parseHostAddress("192.168.1.5:abc", defaultPort))
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(parseHostAddress("   ", defaultPort))
    }
}
