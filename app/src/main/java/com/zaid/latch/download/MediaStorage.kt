package com.zaid.latch.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.zaid.latch.media.FileNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

data class SavedMedia(val uri: Uri, val mimeType: String, val size: Long)
class MediaStorage(private val context: Context) {
    fun verify(file: File, kind: String) {
        require(file.isFile && file.length() > 0) { "The downloaded file is empty." }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val types = (0 until extractor.trackCount).map {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty()
            }
            require(types.any { it.startsWith(if (kind == "audio") "audio/" else "video/") }) {
                "This output could not be verified as playable $kind. Try a different format."
            }
        } finally {
            extractor.release()
        }
    }
    suspend fun save(file: File, title: String, kind: String, onReserved: suspend (Uri, String) -> Unit): SavedMedia =
        withContext(Dispatchers.IO) {
            verify(file, kind)
            currentCoroutineContext().ensureActive()
            val extension = file.extension.lowercase()
            val mime = when (extension) {
                "mp4", "m4v" -> "video/mp4"
                "webm" -> if (kind == "audio") "audio/webm" else "video/webm"
                "mkv" -> "video/x-matroska"
                "mov" -> "video/quicktime"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "ogg", "opus" -> "audio/ogg"
                "flac" -> "audio/flac"
                "wav" -> "audio/wav"
                "aac" -> "audio/aac"
                else -> if (kind == "audio") "audio/*" else "video/*"
            }
            val folder = if (kind == "audio") Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
            val collection = if (kind == "audio") MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FileNames.safe(title) + "_" + System.currentTimeMillis() + "." + extension)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Latch")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: error("Could not create the destination file.")
            try {
                onReserved(uri, mime)
                resolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output) { "Could not open the destination folder." }
                    file.inputStream().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                }
                currentCoroutineContext().ensureActive()
                resolver.openFileDescriptor(uri, "r").use { descriptor ->
                    require(descriptor != null && descriptor.statSize == file.length()) { "The saved file was incomplete." }
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                SavedMedia(uri, mime, file.length())
            } catch (e: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        }
    fun delete(uri: String) {
        if (uri.startsWith("content://media/")) context.contentResolver.delete(Uri.parse(uri), null, null)
    }
    fun exists(uri: String): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { it.statSize > 0 } ?: false
    }.getOrDefault(false)
}
