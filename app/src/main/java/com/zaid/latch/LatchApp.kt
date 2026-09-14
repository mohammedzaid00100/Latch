package com.zaid.latch

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.zaid.latch.data.*
import com.zaid.latch.download.MediaStorage
import com.zaid.latch.media.MediaEngine
import kotlinx.coroutines.*

class LatchApp : Application() {
    lateinit var graph: AppGraph
        private set
    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db = Room.databaseBuilder(context, LatchDatabase::class.java, "latch.db").build()
    val dao = db.downloads()
    val preferences = Preferences(context)
    val engine = MediaEngine(context)
    val storage = MediaStorage(context)
    val ready = scope.async {
        dao.unfinishedWithFiles().forEach { runCatching { storage.delete(it.savedUri) } }
        dao.recoverInterrupted()
    }
}
val Context.graph: AppGraph get() = (applicationContext as LatchApp).graph
