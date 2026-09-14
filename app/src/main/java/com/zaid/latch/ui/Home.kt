package com.zaid.latch.ui

import android.content.*
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zaid.latch.*
import com.zaid.latch.data.*
import com.zaid.latch.download.Downloads
import com.zaid.latch.media.*
import kotlinx.coroutines.*

@Composable
fun LatchHome(showDownloads: Boolean) {
    val context = LocalContext.current
    val graph = context.graph
    val records by graph.dao.observeAll().collectAsStateWithLifecycle(emptyList())
    val preferences by graph.preferences.flow.collectAsStateWithLifecycle(UserPreferences())
    var selected by rememberSaveable { mutableIntStateOf(if (showDownloads) 1 else 0) }
    LaunchedEffect(showDownloads) { if (showDownloads) selected = 1 }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                listOf("Home", "Downloads", "Settings").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selected == index, onClick = { selected = index },
                        icon = { Icon(when(index) { 0 -> Icons.Rounded.Home; 1 -> Icons.Rounded.DownloadDone; else -> Icons.Rounded.Tune }, label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer)
                    )
                }
            }
        }
    ) { padding ->
        when (selected) {
            0 -> HomeScreen(records, Modifier.padding(padding)) { selected = 1 }
            1 -> DownloadsScreen(records, Modifier.padding(padding))
            else -> SettingsScreen(preferences, records.any { it.isActive }, Modifier.padding(padding))
        }
    }
}
@Composable
private fun HomeScreen(records: List<DownloadRecord>, modifier: Modifier, onLibrary: () -> Unit) {
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf("") }
    fun openPanel(value: String) {
        context.startActivity(Intent(context, ShareActivity::class.java)
            .setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value))
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Brand()
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                    Text("ON YOUR PHONE", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Eyebrow("SHARE. SAVE. DONE.")
                Spacer(Modifier.height(14.dp))
                Text("Keep the\ngood stuff.", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(14.dp))
                Text("A shorter way from a video you love\nto a file on your phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = Ink) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.IosShare, contentDescription = null, tint = Lime, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Start with Share", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                    Text("Open a video. Tap Share → More → Latch.\nChoose a format. We'll take it from there.",
                        color = Color(0xFFD0DAC7), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("YouTube", "Instagram", "Facebook").forEach {
                            Surface(color = Color(0xFF30392A), shape = RoundedCornerShape(20.dp)) {
                                Text(it, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Lime, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Eyebrow("HAVE A LINK ALREADY?")
                OutlinedTextField(
                    value = url, onValueChange = { url = it.take(16000) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Paste a video link") }, shape = RoundedCornerShape(18.dp),
                    leadingIcon = { Icon(Icons.Rounded.Link, null) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        url = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (url.isBlank()) Toast.makeText(context, "Copy a video link first.", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Paste")
                    }
                    Button(onClick = { openPanel(url) }, enabled = url.isNotBlank(),
                        modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Continue"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your latest saves", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onLibrary) { Text("View all") }
            }
        }
        if (records.isEmpty()) item {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Rounded.BookmarkBorder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("A little empty. For now.", fontWeight = FontWeight.SemiBold)
                    Text("Your downloads will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else items(records.take(3), key = { it.id }) { DownloadCard(it) }
        item { Text("Save media you own or have permission to download. Availability depends on the source.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable
private fun DownloadsScreen(records: List<DownloadRecord>, modifier: Modifier) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val filtered = when(filter) { "Active" -> records.filter { it.isActive }; "Saved" -> records.filter { it.status == DownloadRecord.COMPLETED }; else -> records }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Brand(compact = true) }
        item {
            Text("Your library", style = MaterialTheme.typography.headlineMedium)
            Text("${records.count { it.status == DownloadRecord.COMPLETED }} saved · ${records.count { it.isActive }} in progress",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Active", "Saved").forEach { value ->
                    FilterChip(selected = value == filter, onClick = { filter = value }, label = { Text(value) })
                }
            }
        }
        if (filtered.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Nothing here yet", style = MaterialTheme.typography.titleLarge)
                Text("Share a video to Latch to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(filtered, key = { it.id }) { DownloadCard(it) }
    }
}

@Composable
fun DownloadCard(record: DownloadRecord) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    fun openFile(share: Boolean) {
        scope.launch {
            if (!withContext(Dispatchers.IO) { graph.storage.exists(record.savedUri) }) {
                toast("This file was moved or deleted. Remove its history entry and share the link again.")
                return@launch
            }
            val uri = Uri.parse(record.savedUri)
            runCatching {
                val intent = if (share) Intent(Intent.ACTION_SEND).setType(record.mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri).setClipData(ClipData.newRawUri(record.title, uri))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                else Intent(Intent.ACTION_VIEW).setDataAndType(uri, record.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(intent, if (share) "Share saved file" else "Open saved file"))
            }.onFailure { toast("No app is available to open this format.") }
        }
    }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(if (record.kind == "audio") Icons.Rounded.Headphones else Icons.Rounded.Movie, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (record.thumbnail.isNotBlank()) AsyncImage(record.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(record.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text("${record.platform} · ${record.choiceLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (record.status == DownloadRecord.COMPLETED) {
                            DropdownMenuItem(text = { Text("Open file") }, onClick = { menu = false; openFile(false) })
                            DropdownMenuItem(text = { Text("Share file") }, onClick = { menu = false; openFile(true) })
                        }
                        if (!record.isActive) DropdownMenuItem(text = { Text(if (record.status == DownloadRecord.COMPLETED) "Delete file" else "Remove from history") },
                            onClick = { menu = false; deleteDialog = true })
                        if (record.isActive) DropdownMenuItem(text = { Text("Cancel download") }, onClick = { menu = false; Downloads.cancel(context, record.id) })
                    }
                }
            }
            if (record.isActive) {
                if (record.progress >= 0) LinearProgressIndicator(progress = { record.progress.coerceIn(0f, 100f) / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)))
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)))
            }
            if (record.message.isNotBlank()) Text(record.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    when {
                        record.status == DownloadRecord.COMPLETED -> "Saved · ${readableSize(record.actualSize)}"
                        record.progress >= 0 -> "${record.status} · ${record.progress.toInt()}%"
                        else -> record.status
                    }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (record.status == DownloadRecord.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    record.isActive -> TextButton(onClick = { Downloads.cancel(context, record.id) }) { Text("Cancel") }
                    record.status == DownloadRecord.COMPLETED -> TextButton(onClick = { openFile(false) }) { Text("Open") }
                    else -> TextButton(onClick = {
                        scope.launch {
                            graph.ready.await()
                            graph.dao.retry(record.id)
                            runCatching { Downloads.start(context) }.onFailure {
                                graph.dao.progress(record.id, DownloadRecord.FAILED, -1f, message = "Open Latch and tap retry to start the download.")
                                toast("Could not start the download. Try again while Latch is open.")
                            }
                        }
                    }) { Text("Retry") }
                }
            }
        }
    }
    if (deleteDialog) AlertDialog(
        onDismissRequest = { deleteDialog = false },
        title = { Text(if (record.status == DownloadRecord.COMPLETED) "Delete this file?" else "Remove this entry?") },
        text = { Text(if (record.status == DownloadRecord.COMPLETED) "The saved file will be removed from your phone." else "Any temporary download files will also be removed.") },
        confirmButton = { TextButton(onClick = {
            deleteDialog = false
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        if (record.status == DownloadRecord.COMPLETED) graph.storage.delete(record.savedUri)
                        java.io.File(context.cacheDir, "downloads/${record.id}").deleteRecursively()
                        graph.dao.remove(record.id)
                    }
                }.onFailure { toast("Could not delete the file. You can remove it with your phone's file manager.") }
            }
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Keep") } }
    )
}
@Composable
private fun SettingsScreen(preferences: UserPreferences, hasActive: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    var license by remember { mutableStateOf<String?>(null) }
    fun openWeb(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { Brand(compact = true) }
        item { Text("Make it yours.", style = MaterialTheme.typography.headlineMedium) }
        item {
            Surface(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    SettingSwitch("Wi-Fi downloads only", "Stop a transfer if Wi-Fi disconnects.", preferences.wifiOnly) {
                        scope.launch { graph.preferences.wifiOnly(it) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingSwitch("Start with audio", "Preselect Audio when a link has a soundtrack.", preferences.preferAudio) {
                        scope.launch { graph.preferences.preferAudio(it) }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("APPEARANCE")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system", "light", "dark").forEach { theme ->
                        FilterChip(selected = preferences.darkMode == theme,
                            onClick = { scope.launch { graph.preferences.theme(theme) } },
                            label = { Text(theme.replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Everything stays on your phone.", fontWeight = FontWeight.Bold)
                    Text("Videos → Movies/Latch\nAudio → Music/Latch", style = MaterialTheme.typography.bodyMedium)
                    Text("No account. No upload to a Latch server. Links are fetched from their original sources.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { scope.launch {
                    graph.dao.clearHistory()
                    Toast.makeText(context, "History cleared. Saved files remain on your phone.", Toast.LENGTH_LONG).show()
                } }) { Icon(Icons.Rounded.History, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text("Clear download history") }
                TextButton(enabled = !hasActive, onClick = { scope.launch {
                    withContext(Dispatchers.IO) { java.io.File(context.cacheDir, "downloads").deleteRecursively() }
                    Toast.makeText(context, "Temporary files cleared.", Toast.LENGTH_SHORT).show()
                } }) { Icon(Icons.Rounded.CleaningServices, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text("Clear temporary files") }
                TextButton(onClick = { openWeb("https://github.com/mohammedzaid00100/Latch") }) {
                    Icon(Icons.Rounded.Code, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text("Source code & updates")
                }
                TextButton(onClick = { scope.launch {
                    license = withContext(Dispatchers.IO) { context.assets.open("licenses/GPL-3.0.txt").bufferedReader().use { it.readText() } }
                } }) { Icon(Icons.Rounded.Info, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text("Open-source license") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Latch ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text("Made by Mohammed Zaid.", style = MaterialTheme.typography.bodyMedium)
                Text("Built with youtubedl-android, yt-dlp and FFmpeg. GPLv3; no warranty. Source and dependency notices are in the repository.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Only save content when the platform and rights holder permit it. Public links can still be unavailable. Private, protected and age-restricted media are unsupported.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (license != null) AlertDialog(onDismissRequest = { license = null },
        title = { Text("GNU GPL version 3") },
        text = { Text(license.orEmpty(), Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
        confirmButton = { TextButton(onClick = { license = null }) { Text("Close") } })
}
@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = onChange)
    }
}
