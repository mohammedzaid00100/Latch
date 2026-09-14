package com.zaid.latch.media

import android.content.Context
import android.util.AtomicFile
import com.yausername.youtubedl_android.YoutubeDL
import com.zaid.latch.BuildConfig
import java.io.File
import java.security.MessageDigest

/** Installs only the checksum-pinned extractor included in this APK. */
object ExtractorBundle {
    fun destination(context: Context) = File(
        File(File(context.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName),
        YoutubeDL.ytdlpBin
    )

    @Synchronized
    fun install(context: Context) {
        val destination = destination(context)
        val atomic = AtomicFile(destination)
        val current = runCatching {
            atomic.openRead().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(65536)
                var count = input.read(buffer)
                while (count != -1) {
                    digest.update(buffer, 0, count)
                    count = input.read(buffer)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }.getOrNull()
        if (current == BuildConfig.EXTRACTOR_SHA256) return
        val bytes = context.assets.open("engine/yt-dlp").use { it.readBytes() }
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        check(expected == BuildConfig.EXTRACTOR_SHA256) {
            "The bundled download engine could not be verified. Reinstall this Latch update."
        }
        check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs()) {
            "There is not enough storage to prepare the download engine."
        }
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }
}
