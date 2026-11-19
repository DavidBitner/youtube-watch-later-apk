package com.davidbitner.watchlater.youtube

import android.content.Context
import android.content.Intent
import com.davidbitner.watchlater.data.Video
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object YouTubeApi {
    private const val SCOPE = "https://www.googleapis.com/auth/youtube"
    private const val BASE = "https://www.googleapis.com/youtube/v3/"
    private val LIST_REGEX = Regex("[?&]list=([A-Za-z0-9_-]+)")

    private val DURATION_REGEX = Regex("^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")

    class ApiException(message: String, val code: Int) : Exception(message)

    data class PlaylistInfo(val id: String, val title: String, val count: Int, val privacy: String)

    suspend fun authorize(context: Context): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(SCOPE)))
                .build()
            Identity.getAuthorizationClient(context).authorize(request)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    suspend fun silentToken(context: Context): String? = try {
        val result = authorize(context)
        if (result.hasResolution()) null else result.accessToken
    } catch (_: Exception) {
        null
    }

    fun resultFromIntent(context: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    fun playlistId(url: String): String? = LIST_REGEX.find(url)?.groupValues?.get(1)

    fun listPlaylists(token: String): List<PlaylistInfo> {
        val out = ArrayList<PlaylistInfo>()
        var page: String? = null
        do {
            val path = "playlists?part=snippet,contentDetails,status&mine=true&maxResults=50" +
                (page?.let { "&pageToken=$it" } ?: "")
            val json = JSONObject(request("GET", path, token))
            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    out.add(
                        PlaylistInfo(
                            id = item.getString("id"),
                            title = item.optJSONObject("snippet")?.optString("title").orEmpty(),
                            count = item.optJSONObject("contentDetails")?.optInt("itemCount") ?: 0,
                            privacy = item.optJSONObject("status")?.optString("privacyStatus").orEmpty(),
                        )
                    )
                }
            }
            page = json.optString("nextPageToken").ifBlank { null }
        } while (page != null)
        return out
    }

    fun fetchPlaylist(token: String, playlistId: String): Pair<String, List<Video>> {
        val head = JSONObject(request("GET", "playlists?part=snippet&id=$playlistId", token))
        val headItem = head.optJSONArray("items")?.optJSONObject(0)
            ?: throw ApiException("Playlist not found or not accessible with this account", 404)
        val title = headItem.optJSONObject("snippet")?.optString("title").orEmpty()

        class Raw(val itemId: String, val videoId: String, val title: String, val channel: String, val position: Int)

        val raw = ArrayList<Raw>()
        var index = 0
        var page: String? = null
        do {
            val path = "playlistItems?part=snippet,contentDetails&maxResults=50&playlistId=$playlistId" +
                (page?.let { "&pageToken=$it" } ?: "")
            val json = JSONObject(request("GET", path, token))
            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    index++
                    val snippet = item.optJSONObject("snippet")
                    val name = snippet?.optString("title").orEmpty()
                    val videoId = item.optJSONObject("contentDetails")?.optString("videoId").orEmpty()
                    if (videoId.isBlank() || name == "Private video" || name == "Deleted video") continue
                    raw.add(
                        Raw(
                            itemId = item.optString("id"),
                            videoId = videoId,
                            title = name,
                            channel = snippet?.optString("videoOwnerChannelTitle").orEmpty(),
                            position = index,
                        )
                    )
                }
            }
            page = json.optString("nextPageToken").ifBlank { null }
        } while (page != null)

        val durations = HashMap<String, Long>()
        raw.chunked(50).forEach { chunk ->
            val ids = chunk.joinToString(",") { it.videoId }
            val json = JSONObject(request("GET", "videos?part=contentDetails&maxResults=50&id=$ids", token))
            val items = json.optJSONArray("items") ?: return@forEach
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                durations[item.getString("id")] =
                    parseDuration(item.optJSONObject("contentDetails")?.optString("duration").orEmpty())
            }
        }

        val list = raw.map {
            Video(
                id = it.videoId,
                title = it.title,
                channel = it.channel,
                durationSec = durations[it.videoId] ?: 0L,
                position = it.position,
                itemId = it.itemId,
            )
        }
        return title to list
    }

    private fun parseDuration(iso: String): Long {
        val m = DURATION_REGEX.find(iso) ?: return 0L
        fun part(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
        return part(1) * 86_400 + part(2) * 3_600 + part(3) * 60 + part(4)
    }

    fun removeFromPlaylist(token: String, playlistId: String, videoId: String, itemId: String = "") {
        if (itemId.isNotBlank()) {
            try {
                request("DELETE", "playlistItems?id=$itemId", token)
                return
            } catch (e: ApiException) {
                if (e.code != 404) throw e
            }
        }
        val found = findItemId(token, playlistId, videoId) ?: return
        request("DELETE", "playlistItems?id=$found", token)
    }

    private fun findItemId(token: String, playlistId: String, videoId: String): String? {
        var page: String? = null
        do {
            val path = "playlistItems?part=contentDetails&maxResults=50&playlistId=$playlistId" +
                (page?.let { "&pageToken=$it" } ?: "")
            val json = JSONObject(request("GET", path, token))
            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    if (item.getJSONObject("contentDetails").optString("videoId") == videoId) {
                        return item.getString("id")
                    }
                }
            }
            page = json.optString("nextPageToken").ifBlank { null }
        } while (page != null)
        return null
    }

    private fun request(method: String, path: String, token: String): String {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException(errorMessage(body, code), code)
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(body: String, code: Int): String = try {
        val error = JSONObject(body).getJSONObject("error")
        val reason = error.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
        when {
            reason == "quotaExceeded" -> "Daily YouTube quota exceeded. It resets at midnight Pacific time."
            code == 401 -> "Google session expired, try again."
            code == 404 -> "Playlist not found or not accessible with this account"
            code == 403 -> "YouTube refused the request: ${error.optString("message")}"
            else -> error.optString("message").ifBlank { "Request failed ($code)" }
        }
    } catch (_: Exception) {
        "Request failed ($code)"
    }
}
