package com.example.musicsm.data.applemusic

import com.example.musicsm.data.importer.ImportedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppleMusicPlaylistParseTest {

    @Test
    fun `reads the name, cover and tracks from the page data`() {
        val playlist = parseAppleMusicPlaylist(PAGE)!!

        assertEquals("Fall 2026", playlist.name)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/x/600x600bb.jpg", playlist.coverUrl)
        assertEquals(
            listOf(
                ImportedTrack("Do It Again", "Nada Surf", 219_320),
                // No artistName: the credit links are used instead.
                ImportedTrack("Babylon", "Taylor Swift, Ed Sheeran", 0),
            ),
            playlist.tracks,
        )
    }

    @Test
    fun `a page without playlist data reads as nothing`() {
        assertNull(parseAppleMusicPlaylist("<html><body>Not found</body></html>"))
        assertNull(parseAppleMusicPlaylist("""<script type="application/json" id="serialized-server-data">{"data":[]}</script>"""))
        assertNull(parseAppleMusicPlaylist("""<script type="application/json" id="serialized-server-data">{not json</script>"""))
    }
}

private const val PAGE = """
<html><head><title>Fall 2026</title>
<script type="application/json" id="serialized-server-data">{"data":[{"intent":{},"data":{"sections":[
  {"id":"playlist-detail-header-section","itemKind":"containerDetailHeaderLockup","items":[
    {"title":"Fall 2026","artwork":{"dictionary":{"url":"https://is1-ssl.mzstatic.com/image/thumb/x/{w}x{h}bb.{f}"}}}]},
  {"id":"track-list","itemKind":"trackLockup","items":[
    {"title":"Do It Again","artistName":"Nada Surf","duration":219320},
    {"title":"Babylon","subtitleLinks":[{"title":"Taylor Swift"},{"title":"Ed Sheeran"}]},
    {"title":""}
  ]}
]}}],"userTokenHash":"x"}</script>
</head><body></body></html>
"""
