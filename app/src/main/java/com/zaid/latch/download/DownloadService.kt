package com.zaid.latch.download

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.zaid.latch.*
import com.zaid.latch.data.DownloadRecord
import com.zaid.latch.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

object Downloads {
    private val queueLock = Mutex()
    suspend fun enqueue(context: Context, media: MediaInfo, choice: DownloadChoice): Pair<DownloadRecord, Boolean> {
        val graph = context.graph
        graph.ready.await()
        return queueLock.withLock {
            val key = "${media.provider.name}:${media.id}:${choice.key}"
            graph.db.withTransaction {
                val duplicate = graph.dao.duplicate(key)
                if (duplicate != null && (duplicate.status != DownloadRecord.COMPLETED || graph.storage.exists(duplicate.savedUri)))
                    return@withTransaction duplicate to false
                if (duplicate != null) graph.dao.remove(duplicate.id)
                val record = DownloadRecord(
                    UUID.randomUUID().toString(), key, media.url, media.title, media.provider.displayName,
                    media.thumbnail, choice.kind, choice.label, choice.selector, choice.audioFormat,
                    choice.audioBitrate, choice.estimatedSize
                )
                graph.dao.insert(record)
                record to true
            }
        }
    }
    fun start(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }
    fun networkAllowed(context: Context, wifiOnly: Boolean): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (!wifiOnly || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
    }
    fun cancel(context: Context, id: String) {
        context.graph.scope.launch {
            context.graph.dao.cancel(id)
            DownloadService.current?.cancelItem(id)
            context.graph.engine.cancel(id)
        }
    }
}

class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        if (runCatching { UUID.fromString(id) }.isFailure) return
        val pending = goAsync()
        context.graph.scope.launch {
            try {
                context.graph.dao.cancel(id)
                DownloadService.current?.cancelItem(id)
                context.graph.engine.cancel(id)
            } finally { pending.finish() }
        }
    }
}

class DownloadService : Service() {
    companion object {
        private const val ACTIVE_CHANNEL = "latch_active"
        private const val DONE_CHANNEL = "latch_done"
        private const val FOREGROUND_ID = 41
        @Volatile var current: DownloadService? = null
            private set
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val starts = Channel<Int>(Channel.CONFLATED)
    private var itemJob: Job? = null
    @Volatile private var currentId: String? = null
    @Volatile private var wifiOnly = false
    private var stoppingForNetwork = false
    private lateinit var connectivity: ConnectivityManager
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { enforceNetwork() }
        override fun onLost(network: Network) { enforceNetwork() }
    }

    override fun onCreate() {
        super.onCreate()
        current = this
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(ACTIVE_CHANNEL, "Downloads in progress", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(DONE_CHANNEL, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT))
        connectivity = getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(callback)
        serviceScope.launch {
            graph.ready.await()
            graph.preferences.flow.collect {
                wifiOnly = it.wifiOnly
                enforceNetwork()
            }
        }
        serviceScope.launch {
            graph.ready.await()
            for (startId in starts) {
                wifiOnly = graph.preferences.current().wifiOnly
                if (!Downloads.networkAllowed(this@DownloadService, wifiOnly)) {
                    graph.dao.interruptActive(if (wifiOnly) "Connect to Wi-Fi, then tap retry." else "Connect to the internet, then tap retry.")
                } else {
                    while (isActive) {
                        val record = graph.dao.next() ?: break
                        if (!Downloads.networkAllowed(this@DownloadService, wifiOnly)) {
                            graph.dao.interruptActive("The connection changed. Reconnect and tap retry.")
                            break
                        }
                        currentId = record.id
                        itemJob = serviceScope.launch { process(record) }
                        itemJob?.join()
                        currentId = null
                        itemJob = null
                    }
                }
                if (stopSelfResult(startId)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    break
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val types = if (Build.VERSION.SDK_INT >= 35)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        ServiceCompat.startForeground(this, FOREGROUND_ID, notification("Getting your download ready", -1f), types)
        starts.trySend(startId)
        return START_NOT_STICKY
    }
    private fun enforceNetwork() {
        if (currentId == null || stoppingForNetwork || Downloads.networkAllowed(this, wifiOnly)) return
        stoppingForNetwork = true
        serviceScope.launch {
            try {
                graph.dao.interruptActive(if (wifiOnly) "Wi-Fi was disconnected. Reconnect and tap retry." else "Connection lost. Reconnect and tap retry.")
                currentId?.let { graph.engine.cancel(it) }
                itemJob?.cancel()
            } finally { stoppingForNetwork = false }
        }
    }
    fun cancelItem(id: String) {
        if (currentId == id) {
            graph.engine.cancel(id)
            itemJob?.cancel()
        }
    }
    private suspend fun process(record: DownloadRecord) {
        var reserved: String? = null
        var lastProgress = 0L
        var processing = false
        try {
            if (graph.dao.get(record.id)?.status == DownloadRecord.CANCELLED) return
            graph.dao.progress(record.id, DownloadRecord.RUNNING, -1f)
            updateNotification(record.title, -1f, record.id)
            val file = graph.engine.download(record) { percent, eta, isProcessing ->
                if (isProcessing) processing = true
                val now = SystemClock.elapsedRealtime()
                if (now - lastProgress > 650 || isProcessing) {
                    lastProgress = now
                    runBlocking {
                        graph.dao.progress(record.id,
                            if (processing) DownloadRecord.PROCESSING else DownloadRecord.RUNNING,
                            if (processing) -1f else percent, eta)
                    }
                    updateNotification(if (processing) "Processing · ${record.title}" else record.title,
                        if (processing) -1f else percent, record.id)
                }
            }
            currentCoroutineContext().ensureActive()
            if (graph.dao.get(record.id)?.status == DownloadRecord.CANCELLED) return
            graph.dao.progress(record.id, DownloadRecord.SAVING, -1f)
            updateNotification("Saving · ${record.title}", -1f, record.id)
            val saved = graph.storage.save(file, record.title, record.kind) { uri, mime ->
                reserved = uri.toString()
                graph.dao.reserveUri(record.id, uri.toString(), mime)
            }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val changed = graph.dao.complete(record.id, saved.uri.toString(), saved.mimeType, saved.size)
                if (changed == 0) {
                    graph.storage.delete(saved.uri.toString())
                } else {
                    reserved = null
                    showFinished(record, saved)
                }
            }
            File(cacheDir, "downloads/${record.id}").deleteRecursively()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                reserved?.let { runCatching { graph.storage.delete(it) } }
                val latest = graph.dao.get(record.id)
                if (latest?.status == DownloadRecord.CANCELLED) File(cacheDir, "downloads/${record.id}").deleteRecursively()
                else if (latest?.isActive == true) graph.dao.progress(record.id, DownloadRecord.INTERRUPTED, -1f, message = "Download interrupted. Tap retry to continue.")
            }
            throw e
        } catch (e: Throwable) {
            reserved?.let { runCatching { graph.storage.delete(it) } }
            if (graph.dao.get(record.id)?.status !in setOf(DownloadRecord.CANCELLED, DownloadRecord.INTERRUPTED)) {
                graph.dao.progress(record.id, DownloadRecord.FAILED, -1f, message = friendlyError(e))
                showFailure(record)
            }
        }
    }
    private fun notification(title: String, percent: Float, id: String? = currentId): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).putExtra("downloads", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, ACTIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Latch")
            .setContentText(title.take(100)).setContentIntent(open)
            .setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100, percent.coerceIn(0f, 100f).toInt(), percent < 0)
            .apply {
                if (id != null) {
                    val cancel = PendingIntent.getBroadcast(this@DownloadService, id.hashCode(),
                        Intent(this@DownloadService, CancelReceiver::class.java).putExtra("id", id),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    addAction(0, "Cancel", cancel)
                }
            }.build()
    }
    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    @android.annotation.SuppressLint("MissingPermission")
    private fun updateNotification(title: String, percent: Float, id: String) {
        if (canNotify()) NotificationManagerCompat.from(this).notify(FOREGROUND_ID, notification(title, percent, id))
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun showFinished(record: DownloadRecord, saved: SavedMedia) {
        if (!canNotify()) return
        val open = Intent(Intent.ACTION_VIEW).setDataAndType(saved.uri, saved.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(this, record.id.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(this).notify(record.id.hashCode(),
            NotificationCompat.Builder(this, DONE_CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Saved to your phone").setContentText(record.title)
                .setContentIntent(pending).addAction(0, "Open", pending).setAutoCancel(true).build())
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun showFailure(record: DownloadRecord) {
        if (!canNotify()) return
        val pending = PendingIntent.getActivity(this, record.id.hashCode(),
            Intent(this, MainActivity::class.java).putExtra("downloads", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(this).notify(record.id.hashCode(),
            NotificationCompat.Builder(this, DONE_CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Download needs attention").setContentText(record.title)
                .setContentIntent(pending).setAutoCancel(true).build())
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        currentId?.let { id -> graph.scope.launch { graph.engine.cancel(id) } }
        itemJob?.cancel()
        graph.scope.launch { graph.dao.interruptActive("Android stopped this long-running transfer. Tap retry when ready.") }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (current === this) current = null
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        currentId?.let { id -> graph.scope.launch {
            graph.engine.cancel(id)
            if (graph.dao.get(id)?.isActive == true)
                graph.dao.progress(id, DownloadRecord.INTERRUPTED, -1f, message = "The download was interrupted. Tap retry.")
        } }
        serviceScope.cancel()
        super.onDestroy()
    }
}
