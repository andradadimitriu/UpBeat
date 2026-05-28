package com.example.upbeat.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.upbeat.model.Song
import org.junit.Rule
import org.junit.Test

class SongListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun songListScreen_showsTitle() {
        composeTestRule.setContent {
            SongListScreen(
                songs = emptyList(),
                isLoading = false,
                loadError = false,
                onSongClick = {},
                onAddSong = {},
                onRetry = {}
            )
        }

        composeTestRule
            .onNodeWithText("My Songs")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun songListScreen_whenLoading_showsProgressIndicator() {
        composeTestRule.setContent {
            SongListScreen(
                songs = emptyList(),
                isLoading = true,
                loadError = false,
                onSongClick = {},
                onAddSong = {},
                onRetry = {}
            )
        }

        composeTestRule
            .onNode(hasProgressBarInHierarchy())
            .assertExists()
    }

    @Test
    fun songListScreen_whenEmpty_showsEmptyMessage() {
        composeTestRule.setContent {
            SongListScreen(
                songs = emptyList(),
                isLoading = false,
                loadError = false,
                onSongClick = {},
                onAddSong = {},
                onRetry = {}
            )
        }

        composeTestRule
            .onNodeWithText("No songs yet. Add one!")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun songListScreen_whenError_showsErrorMessage() {
        composeTestRule.setContent {
            SongListScreen(
                songs = emptyList(),
                isLoading = false,
                loadError = true,
                onSongClick = {},
                onAddSong = {},
                onRetry = {}
            )
        }

        composeTestRule
            .onNodeWithText("Could not load songs.")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Retry")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun songListScreen_whenError_retryButtonCallsCallback() {
        var retryClicked = false

        composeTestRule.setContent {
            SongListScreen(
                songs = emptyList(),
                isLoading = false,
                loadError = true,
                onSongClick = {},
                onAddSong = {},
                onRetry = { retryClicked = true }
            )
        }

        composeTestRule
            .onNodeWithText("Retry")
            .performClick()

        assert(retryClicked)
    }

    @Test
    fun songListScreen_displaysSongs() {
        val songs = listOf(
            Song("Song 1", "key1"),
            Song("Song 2", "key2"),
            Song("Song 3", "key3")
        )

        composeTestRule.setContent {
            SongListScreen(
                songs = songs,
                isLoading = false,
                loadError = false,
                onSongClick = {},
                onAddSong = {},
                onRetry = {}
            )
        }

        composeTestRule.onNodeWithText("Song 1").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithText("Song 2").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithText("Song 3").assertExists().assertIsDisplayed()
    }

    @Test
    fun songListScreen_songClick_triggersCallback() {
        var clickedSong: Song? = null
        val songs = listOf(Song("Test Song", "key"))

        composeTestRule.setContent {
            SongListScreen(
                songs = songs,
                isLoading = false,
                loadError = false,
                onSongClick = { clickedSong = it },
                onAddSong = {},
                onRetry = {}
            )
        }

        composeTestRule
            .onNodeWithText("Test Song")
            .performClick()

        assert(clickedSong?.name == "Test Song")
    }

    @Test
    fun songListScreen_showsAddButton() {
        composeTestRule.setContent {
            SongListScreen(
                songs = emptyList(),
                isLoading = false,
                loadError = false,
                onSongClick = {},
                onAddSong = {},
                onRetry = {}
            )
        }

        composeTestRule
            .onNodeWithText("Add New Song")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun songListScreen_addButtonClick_triggersCallback() {
        var addClicked = false

        composeTestRule.setContent {
            SongListScreen(
                songs = emptyList(),
                isLoading = false,
                loadError = false,
                onSongClick = {},
                onAddSong = { addClicked = true },
                onRetry = {}
            )
        }

        composeTestRule
            .onNodeWithText("Add New Song")
            .performClick()

        assert(addClicked)
    }

    @Test
    fun songListScreen_refreshButton_exists() {
        composeTestRule.setContent {
            SongListScreen(
                songs = emptyList(),
                isLoading = false,
                loadError = false,
                onSongClick = {},
                onAddSong = {},
                onRetry = {}
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Refresh songs")
            .assertExists()
    }

    @Test
    fun songListScreen_refreshButton_triggersCallback() {
        var refreshClicked = false

        composeTestRule.setContent {
            SongListScreen(
                songs = emptyList(),
                isLoading = false,
                loadError = false,
                onSongClick = {},
                onAddSong = {},
                onRetry = { refreshClicked = true }
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Refresh songs")
            .performClick()

        assert(refreshClicked)
    }

    @Test
    fun songListScreen_refreshButton_disabledWhenLoading() {
        composeTestRule.setContent {
            SongListScreen(
                songs = emptyList(),
                isLoading = true,
                loadError = false,
                onSongClick = {},
                onAddSong = {},
                onRetry = {}
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Refresh songs")
            .assertIsNotEnabled()
    }

    // Helper function to check for progress bar
    private fun hasProgressBarInHierarchy(): SemanticsMatcher {
        return SemanticsMatcher("Has progress bar") { node ->
            androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo in node.config
        }
    }
}

