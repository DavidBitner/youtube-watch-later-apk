package com.davidbitner.watchlater.youtube

import android.content.Context
import android.content.Intent
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

    class ApiException(message: String, val code: Int) : Exception(message)

    suspend fun authorize(context: Context): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(SCOPE)))
                .build()
            Identity.getAuthorizationClient(context).authorize(request)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    fun resultFromIntent(context: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    fun playlistId(url: String): String? = LIST_REGEX.find(url)?.groupValues?.get(1)

    fun removeFromPlaylist(token: String, playlistId: String, videoId: String) {
        val itemId = findItemId(token, playlistId, videoId) ?: return
        request("DELETE", "playlistItems?id=$itemId", token)
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
            code == 403 -> "YouTube refused the request: ${error.optString("message")}"
            else -> error.optString("message").ifBlank { "Request failed ($code)" }
        }
    } catch (_: Exception) {
        "Request failed ($code)"
    }
}
