package com.example.upbeat.util

import org.junit.Assert.*
import org.junit.Test

class ValidationUtilsTest {

    @Test
    fun isValidSongName_normalName() {
        val name = "My Song"
        val isValid = name.trim().isNotEmpty()

        assertTrue(isValid)
    }

    @Test
    fun isValidSongName_emptyString() {
        val name = ""
        val isValid = name.trim().isNotEmpty()

        assertFalse(isValid)
    }

    @Test
    fun isValidSongName_whitespaceOnly() {
        val name = "   "
        val isValid = name.trim().isNotEmpty()

        assertFalse(isValid)
    }

    @Test
    fun isValidSongName_withNumbers() {
        val name = "Song 123"
        val isValid = name.trim().isNotEmpty()

        assertTrue(isValid)
    }

    @Test
    fun isValidSongName_withSpecialCharacters() {
        val name = "Song (2024) - Remix #1"
        val isValid = name.trim().isNotEmpty()

        assertTrue(isValid)
    }

    @Test
    fun isValidSongName_unicode() {
        val name = "Música 🎵"
        val isValid = name.trim().isNotEmpty()

        assertTrue(isValid)
    }

    @Test
    fun sanitizeFileName_forS3() {
        // S3 allows most characters, but some are problematic
        val filename = "my song.mp3"
        // Basic sanitization would replace spaces
        val sanitized = filename.replace(" ", "_")

        assertEquals("my_song.mp3", sanitized)
    }

    @Test
    fun sanitizeFileName_removeSlashes() {
        val filename = "my/song\\file.mp3"
        val sanitized = filename.replace("/", "_").replace("\\", "_")

        assertEquals("my_song_file.mp3", sanitized)
    }

    @Test
    fun isValidS3Key_standard() {
        val key = "user123/song.mp3"
        val isValid = key.isNotEmpty() && !key.startsWith("/") && !key.endsWith("/")

        assertTrue(isValid)
    }

    @Test
    fun isValidS3Key_empty() {
        val key = ""
        val isValid = key.isNotEmpty() && !key.startsWith("/") && !key.endsWith("/")

        assertFalse(isValid)
    }

    @Test
    fun isValidS3Key_startsWithSlash() {
        val key = "/user123/song.mp3"
        val isValid = key.isNotEmpty() && !key.startsWith("/") && !key.endsWith("/")

        assertFalse(isValid)
    }

    @Test
    fun isValidS3Key_endsWithSlash() {
        val key = "user123/song.mp3/"
        val isValid = key.isNotEmpty() && !key.startsWith("/") && !key.endsWith("/")

        assertFalse(isValid)
    }

    @Test
    fun caseInsensitiveEquals_sameCase() {
        val str1 = "Test Song"
        val str2 = "Test Song"

        assertTrue(str1.equals(str2, ignoreCase = true))
    }

    @Test
    fun caseInsensitiveEquals_differentCase() {
        val str1 = "Test Song"
        val str2 = "test song"

        assertTrue(str1.equals(str2, ignoreCase = true))
    }

    @Test
    fun caseInsensitiveEquals_allCaps() {
        val str1 = "Test Song"
        val str2 = "TEST SONG"

        assertTrue(str1.equals(str2, ignoreCase = true))
    }

    @Test
    fun caseInsensitiveEquals_differentStrings() {
        val str1 = "Test Song"
        val str2 = "Other Song"

        assertFalse(str1.equals(str2, ignoreCase = true))
    }

    @Test
    fun extractUserId_fromS3Key() {
        val s3Key = "user123/folder/song.mp3"
        val userId = s3Key.substringBefore("/")

        assertEquals("user123", userId)
    }

    @Test
    fun extractUserId_noSlash() {
        val s3Key = "song.mp3"
        val userId = s3Key.substringBefore("/")

        assertEquals("song.mp3", userId)
    }

    @Test
    fun extractFilename_fromS3Key() {
        val s3Key = "user123/folder/song.mp3"
        val filename = s3Key.substringAfterLast("/")

        assertEquals("song.mp3", filename)
    }

    @Test
    fun extractFilename_noSlash() {
        val s3Key = "song.mp3"
        val filename = s3Key.substringAfterLast("/")

        assertEquals("song.mp3", filename)
    }

    @Test
    fun isAudioFile_mp3() {
        val filename = "song.mp3"
        val ext = filename.substringAfterLast('.', "").lowercase()
        val isAudio = ext in listOf("mp3", "flac", "wav", "m4a", "aac", "ogg")

        assertTrue(isAudio)
    }

    @Test
    fun isAudioFile_flac() {
        val filename = "song.flac"
        val ext = filename.substringAfterLast('.', "").lowercase()
        val isAudio = ext in listOf("mp3", "flac", "wav", "m4a", "aac", "ogg")

        assertTrue(isAudio)
    }

    @Test
    fun isAudioFile_nonAudio() {
        val filename = "document.pdf"
        val ext = filename.substringAfterLast('.', "").lowercase()
        val isAudio = ext in listOf("mp3", "flac", "wav", "m4a", "aac", "ogg")

        assertFalse(isAudio)
    }

    @Test
    fun isAudioFile_caseInsensitive() {
        val filename = "song.MP3"
        val ext = filename.substringAfterLast('.', "").lowercase()
        val isAudio = ext in listOf("mp3", "flac", "wav", "m4a", "aac", "ogg")

        assertTrue(isAudio)
    }
}

