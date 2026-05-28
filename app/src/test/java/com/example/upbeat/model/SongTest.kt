package com.example.upbeat.model

import org.junit.Assert.*
import org.junit.Test

class SongTest {

    @Test
    fun song_creation_setsProperties() {
        val song = Song("Test Song", "s3://bucket/test.mp3")

        assertEquals("Test Song", song.name)
        assertEquals("s3://bucket/test.mp3", song.s3Key)
    }

    @Test
    fun song_equality_sameName() {
        val song1 = Song("Test Song", "key1")
        val song2 = Song("Test Song", "key2")

        // Songs with same name but different keys
        assertEquals(song1.name, song2.name)
        assertNotEquals(song1.s3Key, song2.s3Key)
    }

    @Test
    fun song_withEmptyName() {
        val song = Song("", "test-key")

        assertEquals("", song.name)
        assertEquals("test-key", song.s3Key)
    }

    @Test
    fun song_withSpecialCharacters() {
        val song = Song("Song (2024) - Mix #1", "key/with/slashes.mp3")

        assertEquals("Song (2024) - Mix #1", song.name)
        assertEquals("key/with/slashes.mp3", song.s3Key)
    }

    @Test
    fun song_withUnicodeCharacters() {
        val song = Song("Música española 🎵", "test-key")

        assertEquals("Música española 🎵", song.name)
    }

    @Test
    fun song_withLongName() {
        val longName = "A".repeat(500)
        val song = Song(longName, "test-key")

        assertEquals(longName, song.name)
    }

    @Test
    fun song_withWhitespace() {
        val song = Song("  Song With Spaces  ", "test-key")

        // Note: trimming should happen at ViewModel level, not in model
        assertEquals("  Song With Spaces  ", song.name)
    }
}

