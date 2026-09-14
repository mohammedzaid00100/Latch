package com.zaid.latch

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

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
            compose.onNodeWithText("Copy error details").assertIsDisplayed()
            screenshot("share-error.png")
            compose.onNodeWithContentDescription("Close download panel").performClick()
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Latch-test-screenshots")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            try {
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    resolver.update(uri, android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    }, null, null)
                }
            } finally { bitmap.recycle() }
        }
    }
}
