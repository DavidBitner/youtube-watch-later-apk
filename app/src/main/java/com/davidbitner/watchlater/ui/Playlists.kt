package com.davidbitner.watchlater.ui

import androidx.compose.foundation.clickable
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
import com.davidbitner.watchlater.sync.SyncScheduler

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
        SyncScheduler.restartSync(context)
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
                "Paste a YouTube playlist link. Its videos download on Wi-Fi and play offline at your speed.",
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
                            SyncScheduler.restartSync(context)
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
                            SyncScheduler.restartSync(context)
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
                        SyncScheduler.restartSync(context)
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancel") } },
        )
    }
}
