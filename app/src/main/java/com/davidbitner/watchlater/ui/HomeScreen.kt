package com.davidbitner.watchlater.ui

import androidx.compose.foundation.background
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.davidbitner.watchlater.data.DownloadState
import com.davidbitner.watchlater.data.Repo
import com.davidbitner.watchlater.data.VideoItem
import com.davidbitner.watchlater.sync.SyncScheduler
import kotlinx.coroutines.launch

private enum class Filter(val label: String) {
    ALL("All"),
    DOWNLOADED("Downloaded"),
    UNWATCHED("Unwatched"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onPlay: (String) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val state by Repo.home.collectAsStateWithLifecycle()
    val settings by Repo.settings.collectAsStateWithLifecycle()
    if (state.noPlaylist) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(Modifier.padding(padding)) { WelcomeScreen(onOpenSettings) }
        }
        return
    }
    val waiting by remember { SyncScheduler.isWaiting(context) }
        .collectAsStateWithLifecycle(initialValue = false)
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!Repo.home.value.noPlaylist && Repo.home.value.totalCount == 0) SyncScheduler.syncNow(context)
    }

    val visible = remember(state.items, filter) {
        when (filter) {
            Filter.ALL -> state.items
            Filter.DOWNLOADED -> state.items.filter { it.state == DownloadState.DOWNLOADED }
            Filter.UNWATCHED -> state.items.filter { !it.watched }
        }
    }
    val downloadedCount = remember(state.items) {
        state.items.count { it.state == DownloadState.DOWNLOADED }
    }
    val busy = state.sync.running || waiting

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    PlaylistTitle(
                        title = state.playlistTitle,
                        history = settings.history,
                        activeUrl = settings.playlistUrl,
                        onManage = onOpenSettings,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (busy) {
                        IconButton(onClick = { SyncScheduler.stop(context) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Stop sync")
                        }
                    } else {
                        IconButton(onClick = { SyncScheduler.syncNow(context) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Sync now")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 400.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Header(
                    summary = "${state.totalCount} videos · $downloadedCount downloaded · ${formatSize(state.usedBytes)}",
                    detail = if (state.sync.running) "" else state.sync.detail,
                    status = when {
                        state.sync.running -> state.sync.message
                        waiting -> "Waiting for Wi-Fi…"
                        else -> state.sync.message
                    },
                    running = state.sync.running,
                    progress = state.items.firstOrNull { it.state == DownloadState.DOWNLOADING }?.downloadProgress,
                    filter = filter,
                    onFilter = { filter = it },
                )
            }

            if (visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = if (state.totalCount == 0) "No videos yet. Pull the playlist with the sync button."
                        else "Nothing here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                    )
                }
            }

            items(visible, key = { it.video.id }) { item ->
                VideoRow(
                    item = item,
                    onClick = {
                        when (item.state) {
                            DownloadState.DOWNLOADED -> onPlay(item.video.id)
                            DownloadState.DOWNLOADING ->
                                scope.launch { snackbar.showSnackbar("Downloading…") }
                            DownloadState.QUEUED ->
                                scope.launch { snackbar.showSnackbar("Queued, downloads on Wi-Fi") }
                            DownloadState.NOT_DOWNLOADED ->
                                scope.launch { snackbar.showSnackbar("Not downloaded. Use the menu to download it.") }
                        }
                    },
                    onDownload = {
                        Repo.requestDownload(item.video.id)
                        SyncScheduler.syncNow(context)
                    },
                    onDelete = { Repo.deleteDownload(item.video.id) },
                    onToggleWatched = { Repo.markWatched(item.video.id, !item.watched) },
                )
            }
        }
    }
}

@Composable
private fun Header(
    summary: String,
    status: String,
    detail: String,
    running: Boolean,
    progress: Float?,
    filter: Filter,
    onFilter: (Filter) -> Unit,
) {
    var showDetail by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                if (detail.isNotBlank()) "$status  ›" else status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = if (detail.isNotBlank()) Modifier.clickable { showDetail = true } else Modifier,
            )
        }
        if (running) {
            Spacer(Modifier.height(8.dp))
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        if (showDetail) {
            AlertDialog(
                onDismissRequest = { showDetail = false },
                title = { Text("Sync error") },
                text = {
                    SelectionContainer {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { showDetail = false }) { Text("Close") } },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach { f ->
                FilterChip(
                    selected = f == filter,
                    onClick = { onFilter(f) },
                    label = { Text(f.label) },
                )
            }
        }
    }
}

@Composable
private fun VideoRow(
    item: VideoItem,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val available = item.state == DownloadState.DOWNLOADED || item.state == DownloadState.DOWNLOADING
    val video = item.video

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${video.position}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(28.dp),
        )
        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .width(132.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = "https://i.ytimg.com/vi/${video.id}/mqdefault.jpg",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (available) 1f else 0.4f),
            )
            Text(
                text = formatDuration(video.durationSec),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
            if (item.watched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Watched",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            } else if (item.positionMs > 0 && video.durationSec > 0) {
                LinearProgressIndicator(
                    progress = { (item.positionMs / (video.durationSec * 1000f)).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0x66000000),
                )
            }
        }

        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (available) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (video.channel.isNotBlank()) {
                Text(
                    text = video.channel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = statusText(item),
                style = MaterialTheme.typography.bodySmall,
                color = if (available) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (item.state == DownloadState.DOWNLOADED) {
                    DropdownMenuItem(
                        text = { Text("Delete download") },
                        onClick = { menu = false; onDelete() },
                    )
                } else if (item.state != DownloadState.DOWNLOADING) {
                    DropdownMenuItem(
                        text = { Text("Download") },
                        onClick = { menu = false; onDownload() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (item.watched) "Mark as unwatched" else "Mark as watched") },
                    onClick = { menu = false; onToggleWatched() },
                )
            }
        }
    }
}

private fun statusText(item: VideoItem): String = when (item.state) {
    DownloadState.DOWNLOADED ->
        (if (item.watched) "Watched" else "Downloaded") + " · " + formatSize(item.fileSize)
    DownloadState.DOWNLOADING -> "Downloading · ${(item.downloadProgress * 100).toInt()}%"
    DownloadState.QUEUED -> "Queued"
    DownloadState.NOT_DOWNLOADED -> "Not downloaded"
}
