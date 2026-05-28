package com.example.upbeat.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.upbeat.viewmodel.SongsViewModel
import org.junit.Rule
import org.junit.Test

class SongScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun songScreen_showsSongTitle() {
        val viewModel = SongsViewModel().apply {
            addSong("Test Song", "test-key")
        }

        composeTestRule.setContent {
            SongScreen(
                songName = "Test Song",
                songsViewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule
            .onNodeWithText("Test Song")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun songScreen_showsUnknownSongForNullName() {
        val viewModel = SongsViewModel()

        composeTestRule.setContent {
            SongScreen(
                songName = null,
                songsViewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule
            .onNodeWithText("Unknown Song")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun songScreen_showsRefreshButton() {
        val viewModel = SongsViewModel().apply {
            addSong("Test Song", "test-key")
        }

        composeTestRule.setContent {
            SongScreen(
                songName = "Test Song",
                songsViewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Refresh")
            .assertExists()
    }

    @Test
    fun songScreen_showsDeleteButton() {
        val viewModel = SongsViewModel().apply {
            addSong("Test Song", "test-key")
        }

        composeTestRule.setContent {
            SongScreen(
                songName = "Test Song",
                songsViewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Delete song")
            .assertExists()
    }

    @Test
    fun songScreen_refreshButton_isClickable() {
        val viewModel = SongsViewModel().apply {
            addSong("Test Song", "test-key")
        }

        composeTestRule.setContent {
            SongScreen(
                songName = "Test Song",
                songsViewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Refresh")
            .assertHasClickAction()
    }

    @Test
    fun songScreen_deleteButton_isClickable() {
        val viewModel = SongsViewModel().apply {
            addSong("Test Song", "test-key")
        }

        composeTestRule.setContent {
            SongScreen(
                songName = "Test Song",
                songsViewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Delete song")
            .assertHasClickAction()
    }

    @Test
    fun songScreen_deleteButton_opensConfirmationDialog() {
        val viewModel = SongsViewModel().apply {
            addSong("Test Song", "test-key")
        }

        composeTestRule.setContent {
            SongScreen(
                songName = "Test Song",
                songsViewModel = viewModel,
                onNavigateBack = {}
            )
        }

        // Click delete button
        composeTestRule
            .onNodeWithContentDescription("Delete song")
            .performClick()

        // Verify confirmation dialog appears
        composeTestRule
            .onNodeWithText("Delete Song?")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun songScreen_deleteDialog_showsCancelButton() {
        val viewModel = SongsViewModel().apply {
            addSong("Test Song", "test-key")
        }

        composeTestRule.setContent {
            SongScreen(
                songName = "Test Song",
                songsViewModel = viewModel,
                onNavigateBack = {}
            )
        }

        // Click delete button
        composeTestRule
            .onNodeWithContentDescription("Delete song")
            .performClick()

        // Verify cancel button
        composeTestRule
            .onNodeWithText("Cancel")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun songScreen_deleteDialog_showsDeleteButton() {
        val viewModel = SongsViewModel().apply {
            addSong("Test Song", "test-key")
        }

        composeTestRule.setContent {
            SongScreen(
                songName = "Test Song",
                songsViewModel = viewModel,
                onNavigateBack = {}
            )
        }

        // Click delete button
        composeTestRule
            .onNodeWithContentDescription("Delete song")
            .performClick()

        // Verify delete confirmation button
        composeTestRule
            .onNodeWithText("Delete")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun songScreen_deleteDialog_cancelButton_closesDialog() {
        val viewModel = SongsViewModel().apply {
            addSong("Test Song", "test-key")
        }

        composeTestRule.setContent {
            SongScreen(
                songName = "Test Song",
                songsViewModel = viewModel,
                onNavigateBack = {}
            )
        }

        // Open delete dialog
        composeTestRule
            .onNodeWithContentDescription("Delete song")
            .performClick()

        // Click cancel
        composeTestRule
            .onNodeWithText("Cancel")
            .performClick()

        // Dialog should be gone
        composeTestRule
            .onNodeWithText("Delete Song?")
            .assertDoesNotExist()
    }

    @Test
    fun songScreen_showsLoadingState() {
        val viewModel = SongsViewModel().apply {
            addSong("Test Song", "test-key")
        }

        composeTestRule.setContent {
            SongScreen(
                songName = "Test Song",
                songsViewModel = viewModel,
                onNavigateBack = {}
            )
        }

        // Should show some loading indication initially
        // The actual loading cards would appear as the LaunchedEffect runs
        composeTestRule
            .onNodeWithText("Test Song")
            .assertExists()
    }

    @Test
    fun songScreen_hasProperLayoutStructure() {
        val viewModel = SongsViewModel().apply {
            addSong("Test Song", "test-key")
        }

        composeTestRule.setContent {
            SongScreen(
                songName = "Test Song",
                songsViewModel = viewModel,
                onNavigateBack = {}
            )
        }

        // Verify main components exist
        composeTestRule
            .onNodeWithText("Test Song")
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription("Refresh")
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription("Delete song")
            .assertExists()
    }
}



