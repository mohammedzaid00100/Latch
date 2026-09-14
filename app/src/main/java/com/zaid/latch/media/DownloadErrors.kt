package com.zaid.latch.media

import com.zaid.latch.BuildConfig
import kotlinx.coroutines.TimeoutCancellationException

data class DownloadFailure(val code: String, val message: String)

object DownloadErrors {
    private fun reason(error: Throwable): String {
        val lines = generateSequence(error) { it.cause }.take(5)
            .flatMap { it.message.orEmpty().lineSequence() }.toList()
        // Hints such as "Use --cookies" appear on unrelated failures as well.
        val primary = lines.lastOrNull { it.trimStart().startsWith("ERROR:", ignoreCase = true) }
            ?: error.message.orEmpty()
        return primary.replace(Regex("(?i)use --cookies.*"), "").trim()
    }
    fun describe(error: Throwable, provider: Provider? = null): DownloadFailure {
        val message = reason(error).lowercase()
        return when {
            error is TimeoutCancellationException ->
                DownloadFailure("TIMEOUT", "The source took too long to respond. Check your connection and try again.")
            Regex("age[ -]?(restricted|restriction|limit)|restricted video").containsMatchIn(message) ->
                DownloadFailure("AGE_RESTRICTED", "Age-restricted media is not supported.")
            "drm" in message || "protected media" in message ->
                DownloadFailure("PROTECTED", "Protected media cannot be downloaded.")
            "rate-limit reached or login required" in message || "empty media response" in message ->
                DownloadFailure("INSTAGRAM_RESPONSE", "Instagram did not return this video's details. This can happen with a public reel too; it does not prove the reel is private. Wait a little and retry.")
            "429" in message || Regex("rate[ -]?limit").containsMatchIn(message) ->
                DownloadFailure("RATE_LIMITED", "The platform is temporarily limiting requests. Wait before trying again.")
            "403" in message || "forbidden" in message || "challenge_required" in message || "captcha" in message ||
                "confirm you're not a bot" in message ->
                DownloadFailure("BLOCKED", "The platform blocked this request. Try later or use its own download option.")
            "private" in message || "login required" in message || "login_required" in message ||
                "log in to" in message || "sign in to" in message || "redirected to the login" in message ||
                "only available for registered users" in message ->
                DownloadFailure("LOGIN_REQUIRED", "The source requires account access for this link. Being signed into Instagram does not sign Latch in.")
            "404" in message || "removed" in message || "video is unavailable" in message ->
                DownloadFailure("UNAVAILABLE", "This video is unavailable or has been removed.")
            "unsupported" in message ->
                DownloadFailure("UNSUPPORTED", "This link or media format is not supported.")
            "space" in message || "storage" in message || "enospc" in message ->
                DownloadFailure("STORAGE", "Not enough free storage. Free some space and retry.")
            "network" in message || "unable to resolve host" in message || "name resolution" in message ||
                "timed out" in message || "connection" in message ->
                DownloadFailure("NETWORK", "Could not reach the source. Check your internet connection and try again.")
            error is IllegalArgumentException || error is IllegalStateException ->
                DownloadFailure("INVALID_INPUT", error.message.orEmpty().take(220))
            provider == Provider.INSTAGRAM || "[instagram]" in message ->
                DownloadFailure("INSTAGRAM_EXTRACTOR", "Latch could not read Instagram's response. Retry once. If it keeps failing, copy the error details so the cause can be checked.")
            else ->
                DownloadFailure("EXTRACTOR", "The source could not complete this request. Retry or check for a newer Latch build.")
        }
    }
    fun details(error: Throwable, provider: Provider? = null): String {
        val failure = describe(error, provider)
        val safeReason = reason(error)
            .replace(Regex("https?://[^\\s<>\\\"']+"), "[link removed]")
            .replace(Regex("(?i)(sessionid|csrftoken|authorization|cookie)\\s*[:=]\\s*[^\\s;,]+"), "$1=[removed]")
            .take(1800)
        return "Latch ${BuildConfig.VERSION_NAME}\nyt-dlp ${BuildConfig.EXTRACTOR_VERSION}\nSource: ${provider?.displayName ?: "Unknown"}\nCode: ${failure.code}\nReason: $safeReason"
    }
}
fun friendlyError(error: Throwable): String = DownloadErrors.describe(error).message
