package com.nikira.videocompressor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    @Test fun settings_open_and_close() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Maximum output resolution").assertIsDisplayed()
        composeRule.onNodeWithText("1080p").assertIsDisplayed()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithText("Choose video").assertIsDisplayed()
    }
    @Test fun default_preset_updates_visible_size() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("20 MB").performClick()
        composeRule.onNodeWithText("Maximum final size: 20 MB").assertIsDisplayed()
    }
    @Test fun avc_is_selected_by_default() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("H.264").assertIsSelected()
    }
}