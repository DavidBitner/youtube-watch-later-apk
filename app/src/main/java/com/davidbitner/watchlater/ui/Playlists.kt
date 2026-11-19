package com.davidbitner.watchlater.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidbitner.watchlater.data.PlaylistRef
import com.davidbitner.watchlater.data.Repo
import com.davidbitner.watchlater.sync.SyncGate
import com.davidbitner.watchlater.youtube.YouTubeApi

private fun PlaylistRef.label(): String = title.ifBlank { url.substringAfter("list=") }

@Composable
fun AddPlaylistForm(buttonLabel: String = "Add playlist", onAdded: () -> Unit = {}) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun submit() {
        val url = Repo.normalizeUrl(text)
        if (url == null) {
            error = true
            return
        }
        Repo.selectPlaylist(url)
        SyncGate.request(context, restart = true)
        text = ""
        error = false
        onAdded()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; error = false },
            label = { Text("Playlist link") },
            placeholder = { Text("youtube.com/playlist?list=…") },
            singleLine = true,
            isError = error,
            supportingText = {
                Text(
                    if (error) "That doesn't look like a YouTube playlist link"
                    else "The playlist must be public or unlisted",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = ::submit, enabled = text.isNotBlank()) { Text(buttonLabel) }
    }
}

@Composable
fun AccountPlaylistPicker(buttonLabel: String = "Choose from my playlists", onPicked: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lists by remember { mutableStateOf<List<YouTubeApi.PlaylistInfo>?>(null) }
    val activeUrl = Repo.settings.collectAsStateWithLifecycle().value.playlistUrl

    fun load(token: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { YouTubeApi.listPlaylists(token) } }
            loading = false
            result
                .onSuccess { lists = it }
                .onFailure { error = it.message ?: "Could not load playlists" }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val token = if (res.resultCode == Activity.RESULT_OK) {
            runCatching { YouTubeApi.resultFromIntent(context, res.data).accessToken }.getOrNull()
        } else {
            null
        }
        if (token != null) {
            load(token)
        } else {
            loading = false
            error = "Sign-in cancelled"
        }
    }

    fun start() {
        loading = true
        error = null
        scope.launch {
            try {
                val result = YouTubeApi.authorize(context)
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    val token = result.accessToken
                    if (token != null) {
                        load(token)
                    } else {
                        loading = false
                        error = "Could not sign in"
                    }
                }
            } catch (e: Exception) {
                loading = false
                error = "Sign-in failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = ::start, enabled = !loading) {
            Text(if (loading) "Loading…" else buttonLabel)
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    lists?.let { all ->
        AlertDialog(
            onDismissRequest = { lists = null },
            title = { Text("Your playlists") },
            text = {
                if (all.isEmpty()) {
                    Text("No playlists found in this account.")
                } else {
                    LazyColumn {
                        items(all, key = { it.id }) { info ->
                            val url = "https://www.youtube.com/playlist?list=${info.id}"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        lists = null
                                        if (url != activeUrl) {
                                            Repo.selectPlaylist(url)
                                            SyncGate.request(context, restart = true)
                                        }
                                        onPicked()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = if (url == activeUrl) MaterialTheme.colorScheme.primary
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                )
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(
                                        info.title.ifBlank { info.id },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${info.count} videos · ${info.privacy}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { lists = null }) { Text("Close") } },
        )
    }
}

@Composable
fun WelcomeScreen(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Watch Later",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick one of your YouTube playlists. Its videos download on Wi-Fi and play offline at your speed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            val settings by Repo.settings.collectAsStateWithLifecycle()
            val sdOk = remember { Repo.sdAvailable() }
            if (sdOk) {
                Text(
                    "Where should videos be saved?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.storage != "sd",
                        onClick = { Repo.setStorage("internal") },
                        label = { Text("Internal · ${formatSize(Repo.freeBytes("internal"))} free") },
                    )
                    FilterChip(
                        selected = settings.storage == "sd",
                        onClick = { Repo.setStorage("sd") },
                        label = { Text("SD card · ${formatSize(Repo.freeBytes("sd"))} free") },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            AccountPlaylistPicker()
            Spacer(Modifier.height(16.dp))
            Text(
                "or paste a link",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            AddPlaylistForm(buttonLabel = "Start")
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Text("  Settings")
            }
        }
    }
}

@Composable
fun PlaylistTitle(
    title: String,
    history: List<PlaylistRef>,
    activeUrl: String,
    onManage: () -> Unit,
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch playlist")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            history.forEach { ref ->
                DropdownMenuItem(
                    text = { Text(ref.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        if (ref.url == activeUrl) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = {
                        open = false
                        if (ref.url != activeUrl) {
                            Repo.selectPlaylist(ref.url)
                            SyncGate.request(context, restart = true)
                        }
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Add or manage playlists") },
                onClick = { open = false; onManage() },
            )
        }
    }
}

@Composable
fun PlaylistManager(history: List<PlaylistRef>, activeUrl: String) {
    val context = LocalContext.current
    var pendingRemove by remember { mutableStateOf<PlaylistRef?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        history.forEach { ref ->
            val active = ref.url == activeUrl
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (!active) {
                            Repo.selectPlaylist(ref.url)
                            SyncGate.request(context, restart = true)
                        }
                    }
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = if (active) "Current playlist" else null,
                    tint = if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        ref.label(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (active) {
                        Text(
                            "Current",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = { pendingRemove = ref }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove playlist")
                }
            }
        }
    }

    pendingRemove?.let { ref ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove playlist?") },
            text = { Text("“${ref.label()}” and its downloaded videos are deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemove = null
                    val wasActive = ref.url == activeUrl
                    Repo.removePlaylist(ref.url)
                    if (wasActive && Repo.settings.value.playlistUrl.isNotBlank()) {
                        SyncGate.request(context, restart = true)
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancel") } },
        )
    }
}
