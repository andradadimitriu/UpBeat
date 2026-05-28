package com.example.upbeat.viewmodel

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SongsViewModelTest {

    private lateinit var viewModel: SongsViewModel

    @Before
    fun setup() {
        viewModel = SongsViewModel()
    }

    @Test
    fun addSong_addsToList() {
        viewModel.addSong("Test Song", "test-key")

        assertEquals(1, viewModel.songs.size)
        assertEquals("Test Song", viewModel.songs[0].name)
        assertEquals("test-key", viewModel.songs[0].s3Key)
    }

    @Test
    fun addSong_withWhitespace_trimsName() {
        viewModel.addSong("  Test Song  ", "test-key")

        assertEquals("Test Song", viewModel.songs[0].name)
    }

    @Test
    fun addSong_duplicate_doesNotAdd() {
        viewModel.addSong("Test Song", "key1")
        viewModel.addSong("Test Song", "key2")

        assertEquals(1, viewModel.songs.size)
    }

    @Test
    fun addSong_duplicateCaseInsensitive_doesNotAdd() {
        viewModel.addSong("Test Song", "key1")
        viewModel.addSong("test song", "key2")

        assertEquals(1, viewModel.songs.size)
    }

    @Test
    fun songExists_returnsTrueForExistingSong() {
        viewModel.addSong("Test Song", "test-key")

        assertTrue(viewModel.songExists("Test Song"))
    }

    @Test
    fun songExists_returnsFalseForNonExistingSong() {
        viewModel.addSong("Test Song", "test-key")

        assertFalse(viewModel.songExists("Other Song"))
    }

    @Test
    fun songExists_caseInsensitive() {
        viewModel.addSong("Test Song", "test-key")

        assertTrue(viewModel.songExists("test song"))
        assertTrue(viewModel.songExists("TEST SONG"))
    }

    @Test
    fun songExists_emptyString_returnsFalse() {
        assertFalse(viewModel.songExists(""))
    }

    @Test
    fun songExists_whitespaceOnly_returnsFalse() {
        assertFalse(viewModel.songExists("   "))
    }

    @Test
    fun getSong_returnsCorrectSong() {
        viewModel.addSong("Song 1", "key1")
        viewModel.addSong("Song 2", "key2")

        val song = viewModel.getSong("Song 2")

        assertNotNull(song)
        assertEquals("Song 2", song?.name)
        assertEquals("key2", song?.s3Key)
    }

    @Test
    fun getSong_caseInsensitive() {
        viewModel.addSong("Test Song", "test-key")

        val song = viewModel.getSong("test song")

        assertNotNull(song)
        assertEquals("Test Song", song?.name)
    }

    @Test
    fun getSong_nonExistent_returnsNull() {
        viewModel.addSong("Test Song", "test-key")

        val song = viewModel.getSong("Other Song")

        assertNull(song)
    }

    @Test
    fun removeSong_removesSong() {
        viewModel.addSong("Song 1", "key1")
        viewModel.addSong("Song 2", "key2")

        viewModel.removeSong("Song 1")

        assertEquals(1, viewModel.songs.size)
        assertEquals("Song 2", viewModel.songs[0].name)
    }

    @Test
    fun removeSong_caseInsensitive() {
        viewModel.addSong("Test Song", "test-key")

        viewModel.removeSong("test song")

        assertEquals(0, viewModel.songs.size)
    }

    @Test
    fun removeSong_nonExistent_doesNothing() {
        viewModel.addSong("Test Song", "test-key")

        viewModel.removeSong("Other Song")

        assertEquals(1, viewModel.songs.size)
    }

    @Test
    fun multipleSongs_maintainOrder() {
        viewModel.addSong("Song A", "keyA")
        viewModel.addSong("Song B", "keyB")
        viewModel.addSong("Song C", "keyC")

        assertEquals("Song A", viewModel.songs[0].name)
        assertEquals("Song B", viewModel.songs[1].name)
        assertEquals("Song C", viewModel.songs[2].name)
    }
}


