package com.zaid.latch

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zaid.latch.data.DownloadRecord
import com.zaid.latch.download.MediaStorage
import com.zaid.latch.media.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URI
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MediaPipelineTest {
    @Test fun nativeEngineDownloadsVideoAndConvertsMp3IntoMediaStore() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bytes = instrumentation.context.assets.open("latch-test.mp4").use { it.readBytes() }
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val response = MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "video/mp4").setHeader("Content-Length", bytes.size)
                return if (request.method == "HEAD") response else response.setBody(Buffer().write(bytes))
            }
        }
        server.start()
        val engine = MediaEngine(context) { url ->
            check(URI(url).host in setOf("localhost", "127.0.0.1", "[::1]"))
        }
        val storage = MediaStorage(context)
        val saved = mutableListOf<String>()
        val folders = mutableListOf<File>()
        try {
            val info = engine.resolve(SharedLink(server.url("/latch-test.mp4").toString(), Provider.DIRECT))
            assertTrue(info.formats.isNotEmpty())
            val choices = listOf(FormatChoices.video(info).first(), FormatChoices.audio(info).first { it.audioFormat == "mp3" && it.audioBitrate == 192 })
            for (choice in choices) {
                val id = UUID.randomUUID().toString()
                val record = DownloadRecord(id, id, info.url, "Latch synthetic test", "Direct media", "",
                    choice.kind, choice.label, choice.selector, choice.audioFormat, choice.audioBitrate, choice.estimatedSize)
                folders += File(context.cacheDir, "downloads/$id")
                val output = engine.download(record) { _, _, _ -> }
                assertTrue(output.length() > 0)
                storage.verify(output, choice.kind)
                val result = storage.save(output, record.title, choice.kind) { _, _ -> }
                saved += result.uri.toString()
                assertTrue(storage.exists(result.uri.toString()))
                assertEquals(output.length(), result.size)
                if (choice.kind == "audio") {
                    assertEquals("audio/mpeg", result.mimeType)
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(context, result.uri, null)
                        assertTrue((0 until extractor.trackCount).all {
                            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
                        })
                    } finally { extractor.release() }
                }
            }
        } finally {
            saved.forEach { storage.delete(it) }
            folders.forEach { it.deleteRecursively() }
            server.shutdown()
        }
    }
}
