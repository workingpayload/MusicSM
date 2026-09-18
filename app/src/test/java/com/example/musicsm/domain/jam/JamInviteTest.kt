package com.example.musicsm.domain.jam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JamInviteTest {

    @Test
    fun `round trips through a link`() {
        val invite = JamInvite("192.168.1.42", 41234, "abc123", "Ravi's kitchen")
        assertEquals(invite, JamInvite.parse(invite.toUri()))
    }

    @Test
    fun `names with spaces and symbols survive`() {
        val invite = JamInvite("10.0.0.7", 8080, "tok", "Sam & Alex's party — 2026")
        assertEquals(invite, JamInvite.parse(invite.toUri()))
    }

    @Test
    fun `a link uses the app scheme so the system camera can open it`() {
        val uri = JamInvite("10.0.0.7", 8080, "tok", "x").toUri()
        assertEquals(true, uri.startsWith("musicsm://jam?"))
    }

    @Test
    fun `an empty session name is allowed`() {
        val invite = JamInvite("10.0.0.7", 8080, "tok", "")
        assertEquals(invite, JamInvite.parse(invite.toUri()))
    }

    @Test
    fun `malformed links are rejected`() {
        listOf(
            "",
            "musicsm://jam",
            "musicsm://jam?h=&p=1&t=x",
            "musicsm://jam?p=1&t=x",
            "musicsm://jam?h=1.2.3.4&t=x",
            "musicsm://jam?h=1.2.3.4&p=1",
            "musicsm://jam?h=1.2.3.4&p=notaport&t=x",
            "musicsm://shared/playlist?d=abc",
            "https://example.com/jam?h=1.2.3.4&p=1&t=x",
        ).forEach { assertNull("expected null for '$it'", JamInvite.parse(it)) }
    }

    @Test
    fun `out of range ports are rejected`() {
        assertNull(JamInvite.parse("musicsm://jam?h=1.2.3.4&p=0&t=x"))
        assertNull(JamInvite.parse("musicsm://jam?h=1.2.3.4&p=70000&t=x"))
        assertNull(JamInvite.parse("musicsm://jam?h=1.2.3.4&p=-5&t=x"))
    }
}
