package com.zaid.latch

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.zaid.latch.media.ExtractorBundle
import com.zaid.latch.media.MediaEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExtractorUpgradeTest {
    @Test fun replacesStaleExtractorAndRunsThePinnedVersionOnAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = ExtractorBundle.destination(context)
        destination.parentFile!!.mkdirs()
        // Reproduce an existing installation keeping the old library resource.
        destination.writeText("stale extractor retained from the previous APK")
        ExtractorBundle.install(context)
        assertTrue(destination.length() > 1_000_000)
        val installedTime = destination.lastModified()
        ExtractorBundle.install(context)
        assertEquals(installedTime, destination.lastModified())
        MediaEngine(context).initialize()
        val processId = UUID.randomUUID().toString()
        try {
            val request = YoutubeDLRequest(emptyList<String>()).apply { addOption("--version") }
            val response = YoutubeDL.execute(request, processId, false, null)
            assertEquals(BuildConfig.EXTRACTOR_VERSION, response.out.trim())
        } finally { YoutubeDL.destroyProcessById(processId) }
    }
}
