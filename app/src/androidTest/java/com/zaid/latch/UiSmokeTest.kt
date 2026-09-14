package com.zaid.latch

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun navigationAndEmptyLibraryDoNotCrash() {
        compose.onNodeWithText("Keep the\ngood stuff.").assertIsDisplayed()
        screenshot("home.png")
        compose.onNodeWithText("Downloads", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your library").assertIsDisplayed()
        screenshot("downloads.png")
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Make it yours.").assertIsDisplayed()
        screenshot("settings.png")
    }
    @Test fun invalidShareShowsAnActionableErrorAndCanClose() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, ShareActivity::class.java)
            .setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "not a link")
        ActivityScenario.launch<ShareActivity>(intent).use {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Couldn't open this link").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Couldn't open this link").assertIsDisplayed()
            screenshot("share-error.png")
            compose.onNodeWithContentDescription("Close download panel").performClick()
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
