package com.davidbitner.watchlater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidbitner.watchlater.data.Repo
import com.davidbitner.watchlater.sync.SyncScheduler
import com.davidbitner.watchlater.ytdlp.YtDlp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s by Repo.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var ytVersion by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var pendingStorage by remember { mutableStateOf<String?>(null) }
    val moveState by Repo.move.collectAsStateWithLifecycle()
    val sdOk = remember(s.storage) { Repo.sdAvailable() }
    val currentStorage = if (s.storage == "sd") "sd" else "internal"

    LaunchedEffect(Unit) {
        ytVersion = withContext(Dispatchers.IO) { YtDlp.version(context) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Playback")
            Label("Default speed")
            ChipRow(
                options = listOf(0.5f, 1f, 1.5f, 1.75f, 2f, 2.5f),
                selected = s.speed,
                label = { formatSpeed(it) },
            ) { v -> Repo.updateSettings { it.copy(speed = v) } }

            Section("Downloads")
            Label("Storage location")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentStorage == "internal",
                    onClick = { if (currentStorage != "internal") pendingStorage = "internal" },
                    label = { Text("Internal") },
                )
                FilterChip(
                    selected = currentStorage == "sd",
                    enabled = sdOk || currentStorage == "sd",
                    onClick = {
                        if (currentStorage != "sd") {
                            if (sdOk) pendingStorage = "sd"
                            else scope.launch { snackbar.showSnackbar("No SD card found") }
                        }
                    },
                    label = { Text("SD card") },
                )
            }
            Hint(
                if (currentStorage == "sd" && !sdOk) "SD card not found"
                else "${formatSize(Repo.freeBytes(currentStorage))} free · changing moves your downloaded videos",
            )

            Label("Maximum quality")
            ChipRow(
                options = listOf(480, 720, 1080),
                selected = s.maxHeight,
                label = { "${it}p" },
            ) { v -> Repo.updateSettings { it.copy(maxHeight = v) } }
            Hint("Prefers VP9 for smaller files, falls back to H.264.")

            SwitchRow(
                title = "Wi-Fi only",
                subtitle = "Never download on mobile data",
                checked = s.wifiOnly,
            ) { v ->
                Repo.updateSettings { it.copy(wifiOnly = v) }
                SyncScheduler.schedulePeriodic(context)
            }

            Label("Storage limit")
            ChipRow(
                options = listOf(0, 10, 25, 50, 100, 200, 300),
                selected = s.capGb,
                label = { if (it == 0) "No limit" else "$it GB" },
            ) { v -> Repo.updateSettings { it.copy(capGb = v) } }

            SwitchRow(
                title = "Delete after watching",
                subtitle = "Free space once a video is finished",
                checked = s.autoDeleteWatched,
            ) { v -> Repo.updateSettings { it.copy(autoDeleteWatched = v) } }

            Section("List")
            SwitchRow(
                title = "Reverse order",
                subtitle = "Show and download from the bottom of the playlist first",
                checked = s.reverseOrder,
            ) { v -> Repo.updateSettings { it.copy(reverseOrder = v) } }

            Section("Playlists")
            PlaylistManager(history = s.history, activeUrl = s.playlistUrl)
            Spacer(Modifier.height(4.dp))
            Label("Add a playlist")
            AddPlaylistForm(onAdded = onBack)

            Section("Maintenance")
            Label("yt-dlp ${ytVersion ?: "…"}")
            Button(
                onClick = {
                    scope.launch {
                        updating = true
                        val result = withContext(Dispatchers.IO) {
                            try {
                                val v = YtDlp.update(context)
                                ytVersion = v
                                "Updated to $v"
                            } catch (e: Exception) {
                                "Update failed: ${e.message?.lineSequence()?.firstOrNull()?.take(120) ?: "unknown error"}"
                            }
                        }
                        updating = false
                        snackbar.showSnackbar(result)
                    }
                },
                enabled = !updating,
            ) { Text(if (updating) "Updating…" else "Update yt-dlp") }

            OutlinedButton(onClick = { confirmReset = true }) { Text("Delete all downloads") }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingStorage?.let { target ->
        val stored = remember(target) { Repo.storedBytes() }
        val name = if (target == "sd") "SD card" else "internal storage"
        AlertDialog(
            onDismissRequest = { pendingStorage = null },
            title = { Text("Use $name?") },
            text = {
                Text(
                    if (stored > 0) "${formatSize(stored)} of downloaded videos will be moved. Keep the app open until it finishes."
                    else "New downloads will be saved there.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingStorage = null
                    scope.launch {
                        SyncScheduler.pause(context)
                        delay(1500)
                        val error = withContext(Dispatchers.IO) { Repo.moveStorage(target) }
                        SyncScheduler.schedulePeriodic(context)
                        snackbar.showSnackbar(error ?: "Storage changed to $name")
                    }
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { pendingStorage = null }) { Text("Cancel") } },
        )
    }

    moveState?.let { m ->
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Moving videos") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (m.totalBytes > 0) (m.doneBytes.toFloat() / m.totalBytes).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${formatSize(m.doneBytes)} of ${formatSize(m.totalBytes)}")
                }
            },
            confirmButton = {},
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Delete all downloads?") },
            text = { Text("Videos are removed from this device and will download again on the next sync.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    Repo.resetDownloads()
                    SyncScheduler.syncNow(context)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Section(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Hint(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
