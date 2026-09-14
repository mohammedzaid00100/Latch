package com.zaid.latch

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaid.latch.data.UserPreferences
import com.zaid.latch.media.*
import com.zaid.latch.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ShareState(val loading: Boolean = true, val media: MediaInfo? = null, val error: String = "", val errorDetails: String = "")
class ShareModel(app: Application) : AndroidViewModel(app) {
    private val mutableState = MutableStateFlow(ShareState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var lastText = ""
    var hasLoaded = false
        private set
    fun load(text: String) {
        hasLoaded = true
        lastText = text
        job?.cancel()
        mutableState.value = ShareState()
        job = viewModelScope.launch {
            var link: SharedLink? = null
            try {
                link = LinkParser.parse(text)
                val graph = getApplication<Application>().graph
                graph.ready.await()
                mutableState.value = ShareState(loading = false, media = graph.engine.resolve(link))
            } catch (e: TimeoutCancellationException) {
                mutableState.value = ShareState(loading = false, error = DownloadErrors.describe(e, link?.provider).message,
                    errorDetails = DownloadErrors.details(e, link?.provider))
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) {
                mutableState.value = ShareState(loading = false, error = DownloadErrors.describe(e, link?.provider).message,
                    errorDetails = DownloadErrors.details(e, link?.provider))
            }
        }
    }
    fun retry() { load(lastText) }
}
class ShareActivity : ComponentActivity() {
    private val model: ShareModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!model.hasLoaded) handle(intent)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val preferences by graph.preferences.flow.collectAsStateWithLifecycle(UserPreferences())
            LatchTheme(preferences.darkMode) {
                Box(Modifier.fillMaxSize()) {
                    SharePanel(state, preferences, onRetry = model::retry, onClose = ::finish)
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }
    private fun handle(intent: Intent) {
        model.load(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
    }
}
