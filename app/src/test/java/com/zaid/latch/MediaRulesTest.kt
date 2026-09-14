package com.zaid.latch

import com.zaid.latch.media.*
import org.junit.Assert.*
import org.junit.Test

class MediaRulesTest {
    private val link = SharedLink("https://www.youtube.com/watch?v=test123", Provider.YOUTUBE)
    private val data = """
    {"id":"test123","title":"A permitted test clip","duration":2,"age_limit":0,
     "formats":[
      {"format_id":"18","url":"https://cdn.example.com/a.mp4","ext":"mp4","height":360,"width":640,"fps":30,"vcodec":"h264","acodec":"aac","filesize":1200},
      {"format_id":"137","url":"https://cdn.example.com/b.mp4","ext":"mp4","height":1080,"width":1920,"fps":30,"vcodec":"h264","acodec":"none","filesize":3000},
      {"format_id":"140","url":"https://cdn.example.com/c.m4a","ext":"m4a","vcodec":"none","acodec":"aac","abr":128,"filesize":700}
     ]}
    """.trimIndent()
    @Test fun sharedCaptionFindsSupportedLink() {
        val result = LinkParser.parse("Have a look! https://example.com/about and https://www.instagram.com/reel/abc123/?igsh=hello")
        assertEquals(Provider.INSTAGRAM, result.provider)
        assertEquals("https://www.instagram.com/reel/abc123/", result.url)
    }
    @Test fun supportsYoutubeShortsAndFacebookShareLinks() {
        assertEquals(Provider.YOUTUBE, LinkParser.parse("https://youtube.com/shorts/abc123").provider)
        assertEquals(Provider.FACEBOOK, LinkParser.parse("https://www.facebook.com/share/r/abc123/").provider)
        assertEquals(Provider.DIRECT, LinkParser.parse("https://cdn.example.org/clip.mp4?signature=keep-me").provider)
    }
    @Test fun rejectsUnsafeAndUnsupportedInputs() {
        listOf("file:///etc/passwd", "http://example.com/a.mp4", "https://127.0.0.1/a.mp4",
            "https://localhost/a.mp4", "https://user:pass@example.com/a.mp4",
            "https://youtube.com.attacker.invalid/watch?v=abc", "https://instagram.com/someprofile",
            "https://youtube.com/playlist?list=abc").forEach {
            assertTrue("Unexpectedly accepted: $it", runCatching { LinkParser.parse(it) }.isFailure)
        }
    }
    @Test fun videoChoicesUseExistingFormatsAndMergeRealAudio() {
        val choices = FormatChoices.video(MediaParser.parse(data, link))
        assertEquals(setOf("1080p", "360p"), choices.map { it.label }.toSet())
        val fullHd = choices.first { it.label == "1080p" }
        assertEquals("137+140", fullHd.selector)
        assertEquals(3700L, fullHd.estimatedSize)
        assertTrue(choices.none { it.label == "4K" || it.label == "720p" })
        assertEquals("18", choices.first { it.label == "360p" }.selector)
    }
    @Test fun silentSourcesNeverOfferAudioConversion() {
        val silent = """{"id":"silent","formats":[{"format_id":"1","url":"https://cdn.example.com/s.mp4","ext":"mp4","height":720,"width":1280,"vcodec":"h264","acodec":"none"}]}"""
        val info = MediaParser.parse(silent, link)
        assertFalse(info.hasAudio)
        assertTrue(FormatChoices.audio(info).isEmpty())
        assertTrue(FormatChoices.video(info).single().detail.contains("no audio"))
    }
    @Test fun conversionPresetsAreExplicitAndOriginalsKeepSourceBitrate() {
        val choices = FormatChoices.audio(MediaParser.parse(data, link))
        assertTrue(choices.first().detail.contains("128 kbps"))
        assertEquals("140", choices.first().selector)
        assertTrue(choices.filter { it.audioFormat == "mp3" }.all { it.detail.contains("Conversion") })
    }
    @Test fun protectedAgeRestrictedAndLiveMediaAreRejected() {
        assertTrue(runCatching { MediaParser.parse(data.replace("\"age_limit\":0", "\"age_limit\":18"), link) }.isFailure)
        assertTrue(runCatching { MediaParser.parse(data.replace("\"age_limit\":0", "\"has_drm\":true"), link) }.isFailure)
        assertTrue(runCatching { MediaParser.parse(data.replace("\"age_limit\":0", "\"is_live\":true"), link) }.isFailure)
        assertTrue(runCatching { MediaParser.parse("""{"_type":"playlist","entries":[]}""", link) }.isFailure)
    }
    @Test fun filenamesCannotEscapeTheMediaFolder() {
        val name = FileNames.safe("../unsafe\\folder:video?*")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains(':'))
        assertEquals("Latch media", FileNames.safe("..."))
    }
}
