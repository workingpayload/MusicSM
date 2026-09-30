package com.example.musicsm.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportLinkTest {

    private val list = "PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"

    @Test
    fun `reads YouTube and YouTube Music playlist links`() {
        val youTube = ImportLink.YouTube(list)
        assertEquals(youTube, parseImportLink("https://www.youtube.com/playlist?list=$list"))
        assertEquals(youTube, parseImportLink("https://youtube.com/playlist?list=$list&si=abc"))
        assertEquals(youTube, parseImportLink("https://m.youtube.com/playlist?list=$list"))
        assertEquals(youTube, parseImportLink("https://music.youtube.com/playlist?list=$list"))
        assertEquals(youTube, parseImportLink("https://music.youtube.com/browse/VL$list"))
        // Opened from inside the playlist: the video is ignored, the playlist imported.
        assertEquals(youTube, parseImportLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=$list&index=3"))
        assertEquals(youTube, parseImportLink("https://youtu.be/dQw4w9WgXcQ?list=$list"))
        assertEquals(
            ImportLink.YouTube("RDCLAK5uy_kGLDDW42tws3jDBNB3m8eRcn3iDWMlwd8"),
            parseImportLink("https://music.youtube.com/playlist?list=RDCLAK5uy_kGLDDW42tws3jDBNB3m8eRcn3iDWMlwd8"),
        )
        assertEquals(
            ImportLink.YouTube("OLAK5uy_nq81InQBifozkEJvDr7L9K3kURX7BfMlo"),
            parseImportLink("OLAK5uy_nq81InQBifozkEJvDr7L9K3kURX7BfMlo"),
        )
    }

    @Test
    fun `a YouTube link without a playlist is not importable`() {
        assertNull(parseImportLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(parseImportLink("https://music.youtube.com/browse/MPREb_beMs50hcV8R"))
    }

    @Test
    fun `identifies YouTube radio mixes`() {
        assertTrue(isRadioMix("RDdQw4w9WgXcQ"))
        assertTrue(isRadioMix("RDAMVMdQw4w9WgXcQ"))
        assertFalse(isRadioMix("RDCLAK5uy_kGLDDW42tws3jDBNB3m8eRcn3iDWMlwd8"))
        assertFalse(isRadioMix(list))
    }

    @Test
    fun `reads Apple Music playlist links`() {
        assertEquals(
            ImportLink.AppleMusic("us", "pl.f4d106fed2bd41149aaacabb233eb5eb"),
            parseImportLink("https://music.apple.com/us/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb"),
        )
        val shared = "pl.u-aeBl6H6kVEd"
        assertEquals(ImportLink.AppleMusic("in", shared), parseImportLink("https://music.apple.com/in/playlist/fall-2026/$shared?l=en-GB"))
        assertEquals(ImportLink.AppleMusic("us", shared), parseImportLink("https://music.apple.com/playlist/$shared"))
        assertEquals(ImportLink.AppleMusic("gb", shared), parseImportLink("Listen: https://music.apple.com/GB/playlist/x/$shared"))
        assertEquals(ImportLink.AppleMusic("us", shared), parseImportLink(shared))
        assertNull(parseImportLink("https://music.apple.com/us/album/patient-zero/6814997249"))
    }

    @Test
    fun `still reads Spotify links`() {
        val spotify = ImportLink.Spotify("37i9dQZF1DXcBWIGoYBM5M")
        assertEquals(spotify, parseImportLink("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc123"))
        assertEquals(spotify, parseImportLink("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"))
        assertEquals(spotify, parseImportLink("  37i9dQZF1DXcBWIGoYBM5M "))
    }

    @Test
    fun `rejects anything else`() {
        assertNull(parseImportLink(""))
        assertNull(parseImportLink("hello world"))
        assertNull(parseImportLink("https://example.com/playlist?list=$list"))
    }
}
