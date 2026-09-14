package com.zaid.latch.media

import java.net.URI
import java.util.Locale
import kotlinx.serialization.json.*

enum class Provider(val displayName: String) {
    YOUTUBE("YouTube"), INSTAGRAM("Instagram"), FACEBOOK("Facebook"), DIRECT("Direct media")
}
data class SharedLink(val url: String, val provider: Provider)
object LinkParser {
    private val links = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val mediaExtensions = setOf("mp4", "webm", "mov", "mkv", "mp3", "m4a", "ogg", "opus", "wav", "flac")
    fun parse(text: String): SharedLink {
        require(text.length <= 16000) { "That shared message is too long. Paste a single video link." }
        val candidates = links.findAll(text).map { it.value.trimEnd('.', ',', ')', ']', '}', ';') }.toList()
        require(candidates.isNotEmpty()) { "Share a video link, or paste an https link here." }
        candidates.forEach { candidate ->
            val parsed = runCatching { parseUrl(candidate) }.getOrNull()
            if (parsed != null) return parsed
        }
        error("Use a YouTube video, Instagram Reel or video post, Facebook video, or a direct media-file link.")
    }
    private fun parseUrl(url: String): SharedLink {
        val uri = URI(url.replace("&amp;", "&"))
        require(uri.scheme.equals("https", true)) { "Only secure https links are supported." }
        require(uri.userInfo == null && (uri.port == -1 || uri.port == 443))
        val host = (uri.host ?: error("Invalid link")).lowercase(Locale.ROOT).trimEnd('.')
        require(host != "localhost" && !host.endsWith(".local") && !host.endsWith(".internal"))
        require(!Regex("""^\d+\.\d+\.\d+\.\d+$""").matches(host) && !host.contains(':'))
        val path = uri.path.orEmpty()
        val provider = when (host) {
            "youtu.be" -> { require(path.length > 1); Provider.YOUTUBE }
            "youtube.com", "www.youtube.com", "m.youtube.com" -> {
                require(path.startsWith("/shorts/") || path.startsWith("/live/") ||
                    (path == "/watch" && uri.rawQuery.orEmpty().split('&').any { it.startsWith("v=") }))
                Provider.YOUTUBE
            }
            "instagram.com", "www.instagram.com", "m.instagram.com" -> {
                require(listOf("/reel/", "/reels/", "/p/", "/tv/").any { path.startsWith(it) })
                Provider.INSTAGRAM
            }
            "facebook.com", "www.facebook.com", "m.facebook.com", "web.facebook.com", "fb.watch" -> {
                require(path.isNotBlank() && path != "/" || uri.rawQuery.orEmpty().contains("v="))
                Provider.FACEBOOK
            }
            else -> {
                require(path.substringAfterLast('.').lowercase(Locale.ROOT) in mediaExtensions)
                Provider.DIRECT
            }
        }
        return SharedLink(uri.toASCIIString(), provider)
    }
}
data class MediaFormat(
    val id: String, val extension: String, val height: Int, val width: Int,
    val fps: Double, val audioBitrate: Double, val totalBitrate: Double,
    val size: Long, val hasVideo: Boolean, val hasAudio: Boolean
)
data class MediaInfo(
    val id: String, val url: String, val provider: Provider, val title: String,
    val thumbnail: String, val duration: Double, val formats: List<MediaFormat>
) {
    val hasAudio get() = formats.any { it.hasAudio }
}
data class DownloadChoice(
    val key: String, val label: String, val detail: String, val selector: String,
    val kind: String, val audioFormat: String = "best", val audioBitrate: Int = 0,
    val estimatedSize: Long = 0
)
object MediaParser {
    private val json = Json { ignoreUnknownKeys = true }
    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.doubleOrNull ?: 0.0
    private fun JsonObject.flag(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull ?: false
    fun parse(raw: String, link: SharedLink): MediaInfo {
        val root = json.parseToJsonElement(raw).jsonObject
        require(root.string("_type") !in setOf("playlist", "multi_video")) { "Share one video at a time. Carousels and playlists are not supported yet." }
        require(root.number("age_limit") == 0.0) { "Age-restricted media is not supported." }
        require(!root.flag("is_live") && root.string("live_status") != "is_live") { "Live streams are not supported." }
        require(!root.flag("has_drm")) { "Protected media cannot be downloaded." }
        val items = (root["formats"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty().ifEmpty { listOf(root) }
        val formats = items.filter { !it.flag("has_drm") && it.string("url").isNotBlank() }.mapNotNull { item ->
            val ext = item.string("ext").lowercase(Locale.ROOT)
            val vc = item.string("vcodec")
            val ac = item.string("acodec")
            val video = vc != "none" && (vc.isNotBlank() || ext in setOf("mp4", "webm", "mov", "mkv"))
            val audio = ac != "none" && (ac.isNotBlank() || ext in setOf("mp3", "m4a", "ogg", "opus", "wav", "flac") || (video && vc.isBlank()))
            val id = item.string("format_id").ifBlank { "best" }
            if (!video && !audio || !Regex("""[a-zA-Z0-9._-]+""").matches(id)) null else MediaFormat(
                id, ext, item.number("height").toInt(), item.number("width").toInt(),
                item.number("fps"), item.number("abr"), item.number("tbr"),
                item.number("filesize").takeIf { it > 0 }?.toLong() ?: item.number("filesize_approx").toLong(),
                video, audio
            )
        }
        require(formats.isNotEmpty()) { "No downloadable media was provided for this link." }
        return MediaInfo(
            root.string("id").ifBlank { link.url }, link.url, link.provider,
            root.string("title").ifBlank { "Untitled video" }.take(220),
            root.string("thumbnail").takeIf { it.startsWith("https://") }.orEmpty(),
            root.number("duration"), formats
        )
    }
}
object FormatChoices {
    fun video(media: MediaInfo): List<DownloadChoice> {
        val audio = media.formats.filter { it.hasAudio && !it.hasVideo }.maxByOrNull { it.audioBitrate }
        return media.formats.filter { it.hasVideo }
            .sortedWith(compareByDescending<MediaFormat> { it.height }.thenByDescending { it.fps }.thenByDescending { it.totalBitrate })
            .distinctBy { it.height to it.fps.toInt() }
            .take(12).map { format ->
                val merge = !format.hasAudio && audio != null
                val selector = if (merge) "${format.id}+${audio!!.id}" else format.id
                val label = if (format.height > 0) "${minOf(format.width.takeIf { it > 0 } ?: format.height, format.height)}p" else "Original"
                val detail = buildList {
                    add(if (merge) "Video + audio" else if (format.hasAudio) "Video with audio" else "Video only · no audio")
                    if (format.fps >= 50) add("${format.fps.toInt()} fps")
                    add(if (merge) "MKV" else format.extension.uppercase(Locale.ROOT))
                }.joinToString(" · ")
                DownloadChoice("video:$selector", label, detail, selector, "video",
                    estimatedSize = if (merge && format.size > 0 && audio!!.size > 0) format.size + audio.size else if (!merge) format.size else 0)
            }
    }
    fun audio(media: MediaInfo): List<DownloadChoice> {
        if (!media.hasAudio) return emptyList()
        val separate = media.formats.filter { it.hasAudio && !it.hasVideo }
            .sortedByDescending { it.audioBitrate }.distinctBy { it.extension to it.audioBitrate.toInt() }.take(4)
        val originals = if (separate.isEmpty()) listOf(
            DownloadChoice("audio:original", "Original audio", "Extract audio without an intentional quality increase", "bestaudio/best", "audio")
        ) else separate.map {
            DownloadChoice("audio:${it.id}", it.extension.uppercase(Locale.ROOT),
                if (it.audioBitrate > 0) "Source · approximately ${it.audioBitrate.toInt()} kbps" else "Original source audio",
                it.id, "audio", estimatedSize = it.size)
        }
        val conversions = listOf(128, 192, 320).map {
            DownloadChoice("audio:mp3:$it", "MP3 · $it kbps", "Conversion preset · cannot improve the source", "bestaudio/best", "audio", "mp3", it)
        }
        return originals + conversions
    }
}
object FileNames {
    fun safe(title: String): String = title.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
        .trim().trim('.').take(100).ifBlank { "Latch media" }
}
fun readableSize(bytes: Long): String = when {
    bytes <= 0 -> "Size unavailable"
    bytes >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    else -> "${(bytes / 1024).coerceAtLeast(1)} KB"
}
fun readableDuration(seconds: Double): String {
    if (seconds <= 0) return ""
    val value = seconds.toLong()
    return if (value >= 3600) "%d:%02d:%02d".format(value / 3600, value / 60 % 60, value % 60)
    else "%d:%02d".format(value / 60, value % 60)
}
