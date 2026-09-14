package com.zaid.latch.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.zaid.latch.*
import com.zaid.latch.data.*
import com.zaid.latch.download.Downloads
import com.zaid.latch.media.*
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePanel(state: ShareState, preferences: UserPreferences, onRetry: () -> Unit, onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = sheet,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Brand(compact = true)
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close download panel") }
            }
            when {
                state.loading -> {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 3.dp)
                        Text("Getting your options…", style = MaterialTheme.typography.titleLarge)
                        Text("The first link can take a little longer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.error.isNotEmpty() -> {
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.LinkOff, null, tint = MaterialTheme.colorScheme.error)
                            Text("Couldn't open this link", style = MaterialTheme.typography.titleLarge)
                            Text(state.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Try again") }
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Go back") }
                }
                state.media != null -> MediaOptions(state.media, preferences, onClose)
            }
        }
    }
}
@Composable
private fun MediaOptions(media: MediaInfo, preferences: UserPreferences, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable(media.url) { mutableStateOf(if (preferences.preferAudio && media.hasAudio) "audio" else if (FormatChoices.video(media).isNotEmpty()) "video" else "audio") }
    val choices = remember(media, kind) { if (kind == "video") FormatChoices.video(media) else FormatChoices.audio(media) }
    var selectedKey by rememberSaveable(media.url, kind) { mutableStateOf(
        choices.firstOrNull { it.label == "720p" }?.key ?: choices.firstOrNull()?.key.orEmpty()
    ) }
    val selected = choices.firstOrNull { it.key == selectedKey } ?: choices.firstOrNull()
    var starting by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf("") }
    val startDownload: () -> Unit = {
        if (!starting && selected != null) {
            starting = true
            actionError = ""
            scope.launch {
                try {
                    val result = Downloads.enqueue(context, media, selected)
                    if (result.second) {
                        try { Downloads.start(context) }
                        catch (e: Exception) {
                            context.graph.dao.progress(result.first.id, DownloadRecord.FAILED, -1f, message = "Could not start in the background. Open Latch and tap retry.")
                            throw e
                        }
                    }
                    Toast.makeText(context,
                        if (result.second) "Download added. You can keep browsing."
                        else if (result.first.status == DownloadRecord.COMPLETED) "This file is already saved in Latch."
                        else "This download is already in your queue.", Toast.LENGTH_LONG).show()
                    onClose()
                } catch (e: CancellationException) { throw e }
                catch (e: Throwable) { actionError = friendlyError(e) }
                finally { starting = false }
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startDownload() }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(82.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayCircle, null, Modifier.size(32.dp))
                    if (media.thumbnail.isNotBlank()) AsyncImage(media.thumbnail, contentDescription = "Video thumbnail", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow(media.provider.displayName.uppercase())
                    Text(media.title, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    if (media.duration > 0) Text(readableDuration(media.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("How do you want to keep it?", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("video", "audio").forEach { mode ->
                val enabled = if (mode == "audio") media.hasAudio else FormatChoices.video(media).isNotEmpty()
                FilterChip(
                    selected = kind == mode, enabled = enabled && !starting, onClick = { kind = mode },
                    modifier = Modifier.weight(1f),
                    label = { Text(if (mode == "video") "Video" else "Audio", Modifier.padding(vertical = 7.dp)) },
                    leadingIcon = { Icon(if (mode == "video") Icons.Rounded.Movie else Icons.Rounded.Headphones, null, Modifier.size(20.dp)) },
                    shape = RoundedCornerShape(15.dp)
                )
            }
        }
        if (!media.hasAudio) Text("This source has no downloadable audio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Eyebrow(if (kind == "video") "AVAILABLE QUALITY" else "AUDIO FORMAT")
        Column(Modifier.heightIn(max = 245.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                val isSelected = choice.key == selected?.key
                Surface(
                    onClick = { selectedKey = choice.key }, enabled = !starting,
                    shape = RoundedCornerShape(17.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(choice.label, style = MaterialTheme.typography.titleMedium)
                            Text(choice.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (choice.estimatedSize > 0) Text("~" + readableSize(choice.estimatedSize), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (kind == "audio") Text("Conversion settings change the output file; they cannot improve the original recording.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (kind == "audio") "Music / Latch" else "Movies / Latch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionError.isNotBlank()) Text(actionError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else startDownload()
            },
            enabled = !starting && selected != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)
        ) {
            if (starting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Ink)
            else Icon(Icons.Rounded.Download, null, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (starting) "Starting…" else "Download $kind", fontWeight = FontWeight.Bold)
        }
    }
}
