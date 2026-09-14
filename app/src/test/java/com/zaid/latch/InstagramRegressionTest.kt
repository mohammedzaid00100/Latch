package com.zaid.latch

import com.zaid.latch.media.*
import org.junit.Assert.*
import org.junit.Test

class InstagramRegressionTest {
    @Test fun instagramShareVariantsKeepTheShortcodeAndRemoveTracking() {
        listOf(
            "https://www.instagram.com/reel/AbC_123-xyz/?igsh=abc&utm_source=share#fragment",
            "https://m.instagram.com/reels/AbC_123-xyz/",
            "https://instagram.com/creator.name/reel/AbC_123-xyz/"
        ).forEach {
            assertEquals("https://www.instagram.com/reel/AbC_123-xyz/", LinkParser.parse(it).url)
        }
        assertEquals("https://cdn.example.com/a.mp4?signature=secret",
            LinkParser.parse("https://cdn.example.com/a.mp4?signature=secret").url)
    }
    @Test fun rejectsAudioPagesAndMalformedReelPaths() {
        listOf("https://instagram.com/reels/audio/1234/",
            "https://instagram.com/reel/", "https://instagram.com/reel/abc/extra/path").forEach {
            assertTrue(runCatching { LinkParser.parse(it) }.isFailure)
        }
    }
    @Test fun ambiguousInstagramErrorIsNotClaimedToRequireLogin() {
        val error = Exception("ERROR: [Instagram] abc: Requested content is not available, rate-limit reached or login required. Use --cookies to authenticate")
        val result = DownloadErrors.describe(error, Provider.INSTAGRAM)
        assertEquals("INSTAGRAM_RESPONSE", result.code)
        assertTrue(result.message.contains("does not prove"))
    }
    @Test fun anonymousRateLimitRedirectIsNeitherAgeRestrictionNorPrivateVideo() {
        val error = Exception("ERROR: [Instagram] abc: The webpage request was redirected to the login page. You have exceeded the rate-limit for accessing posts anonymously. Use --cookies")
        assertEquals("RATE_LIMITED", DownloadErrors.describe(error).code)
    }
    @Test fun emptyInstagramResponseIsNotMisreportedAsPrivate() {
        val error = Exception("ERROR: [Instagram] abc: Instagram sent an empty media response. Check if this post is accessible in your browser without being logged-in. Use --cookies")
        assertEquals("INSTAGRAM_RESPONSE", DownloadErrors.describe(error).code)
    }
    @Test fun cookieHintDoesNotOverrideTheActualFailure() {
        assertEquals("BLOCKED", DownloadErrors.describe(Exception("ERROR: HTTP Error 403: Forbidden. Use --cookies to log in")).code)
        assertEquals("INSTAGRAM_EXTRACTOR", DownloadErrors.describe(Exception("WARNING: Cookies may be needed\nERROR: [Instagram] Unable to extract LSD token"), Provider.INSTAGRAM).code)
    }
    @Test fun definiteRestrictionsRemainExplicit() {
        assertEquals("AGE_RESTRICTED", DownloadErrors.describe(Exception("ERROR: age-restricted content")).code)
        assertEquals("LOGIN_REQUIRED", DownloadErrors.describe(Exception("ERROR: This content is only available for registered users who follow this account")).code)
        assertEquals("PROTECTED", DownloadErrors.describe(Exception("Protected media cannot be downloaded.")).code)
    }
    @Test fun diagnosticsIdentifyTheBuildWithoutCopyingSignedLinksOrSessions() {
        val details = DownloadErrors.details(Exception("ERROR: [Instagram] Failed at https://instagram.com/reel/abc/?igsh=private sessionid=secret csrftoken=token"), Provider.INSTAGRAM)
        assertTrue(details.contains(BuildConfig.EXTRACTOR_VERSION))
        assertTrue(details.contains("Source: Instagram"))
        assertFalse(details.contains("igsh"))
        assertFalse(details.contains("secret"))
        assertFalse(details.contains("csrftoken=token"))
    }
}
