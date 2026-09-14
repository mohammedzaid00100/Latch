package com.zaid.latch.media

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.zaid.latch.data.DownloadRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.util.UUID

class MediaEngine(private val context: Context, private val networkValidation: ((String) -> Unit)? = null) {
    private val initLock = Mutex()
    private var initialized = false
    suspend fun initialize() = withContext(Dispatchers.IO) {
        initLock.withLock {
            if (!initialized) {
                ExtractorBundle.install(context)
                YoutubeDL.init(context)
                FFmpeg.init(context)
                initialized = true
            }
        }
    }
    private fun baseRequest(url: String) = YoutubeDLRequest(url).apply {
        addOption("--ignore-config")
        addOption("--no-playlist")
        addOption("--socket-timeout", 25)
        addOption("--retries", 2)
        addOption("--fragment-retries", 2)
        addOption("--age-limit", 0)
        addOption("--no-cache-dir")
    }
    private fun validateNetwork(url: String) {
        if (networkValidation != null) { networkValidation.invoke(url); return }
        val host = URI(url).host ?: error("Invalid link")
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty() && addresses.none {
            it.isAnyLocalAddress || it.isLoopbackAddress || it.isSiteLocalAddress ||
                it.isLinkLocalAddress || it.isMulticastAddress
        }) { "Local network links are not supported." }
    }
    suspend fun resolve(link: SharedLink): MediaInfo = withTimeout(90_000) {
        initialize()
        val processId = UUID.randomUUID().toString()
        try {
            runInterruptible(Dispatchers.IO) {
                validateNetwork(link.url)
                val request = baseRequest(link.url).apply {
                    addOption("--dump-single-json")
                    addOption("--skip-download")
                    addOption("--no-progress")
                }
                val response = YoutubeDL.execute(request, processId, false, null)
                require(response.out.length <= 12_000_000) { "This response is too large. Share a single video." }
                MediaParser.parse(response.out.trim(), link)
            }
        } finally {
            YoutubeDL.destroyProcessById(processId)
        }
    }
    suspend fun download(record: DownloadRecord, onProgress: (Float, Long, Boolean) -> Unit): File {
        initialize()
        return runInterruptible(Dispatchers.IO) {
            validateNetwork(record.url)
            val directory = File(context.cacheDir, "downloads/${record.id}").apply { mkdirs() }
            val needed = if (record.estimatedSize > 0) record.estimatedSize * 3 + 32L * 1024 * 1024 else 128L * 1024 * 1024
            require(directory.usableSpace > needed) { "There is not enough free storage for this download and its temporary files." }
            val request = baseRequest(record.url).apply {
                addOption("--no-simulate")
                addOption("--no-quiet")
                addOption("--progress")
                addOption("--newline")
                addOption("--continue")
                addOption("--no-mtime")
                addOption("-f", record.selector)
                addOption("-o", File(directory, "media.%(ext)s").absolutePath)
                addOption("--print", "after_move:filepath")
                if (record.kind == "audio") {
                    addOption("-x")
                    addOption("--audio-format", record.audioFormat)
                    if (record.audioBitrate > 0) addOption("--audio-quality", "${record.audioBitrate}K")
                } else if (record.selector.contains('+')) {
                    addOption("--merge-output-format", "mkv")
                }
            }
            try {
                val response = YoutubeDL.execute(request, record.id, false) { percent, eta, line ->
                    val processing = line.contains("[Merger]") || line.contains("[ExtractAudio]") ||
                        line.contains("[VideoConvertor]") || line.contains("[Fixup")
                    onProgress(if (processing) -1f else percent.coerceIn(-1f, 100f), eta, processing)
                }
                val allowed = setOf("mp4", "mkv", "webm", "mov", "mp3", "m4a", "ogg", "opus", "wav", "flac", "aac")
                val printed = response.out.lineSequence().map { File(it.trim()) }.lastOrNull {
                    it.parentFile?.canonicalPath == directory.canonicalPath && it.isFile && it.extension.lowercase() in allowed
                }
                val output = printed ?: directory.listFiles()?.filter { it.extension.lowercase() in allowed && it.isFile }
                    ?.maxByOrNull { it.lastModified() }
                require(output != null && output.length() > 0) { "The source did not produce a complete media file." }
                output
            } finally {
                YoutubeDL.destroyProcessById(record.id)
            }
        }
    }
    fun cancel(id: String) { YoutubeDL.destroyProcessById(id) }
}
