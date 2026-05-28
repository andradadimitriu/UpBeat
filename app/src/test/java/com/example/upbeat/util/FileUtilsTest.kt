package com.example.upbeat.util

import org.junit.Assert.*
import org.junit.Test

class FileUtilsTest {

    @Test
    fun extractExtension_withStandardExtension() {
        val filename = "song.mp3"
        val extension = filename.substringAfterLast('.', "")
        
        assertEquals("mp3", extension)
    }

    @Test
    fun extractExtension_withMultipleDots() {
        val filename = "my.song.file.mp3"
        val extension = filename.substringAfterLast('.', "")
        
        assertEquals("mp3", extension)
    }

    @Test
    fun extractExtension_noExtension() {
        val filename = "songfile"
        val extension = filename.substringAfterLast('.', "")
        
        assertEquals("", extension)
    }

    @Test
    fun extractExtension_endsWithDot() {
        val filename = "song."
        val extension = filename.substringAfterLast('.', "")
        
        assertEquals("", extension)
    }

    @Test
    fun extractNameWithoutExtension_standard() {
        val filename = "song.mp3"
        val name = filename.substringBeforeLast('.', filename)
        
        assertEquals("song", name)
    }

    @Test
    fun extractNameWithoutExtension_multipleDots() {
        val filename = "my.song.file.mp3"
        val name = filename.substringBeforeLast('.', filename)
        
        assertEquals("my.song.file", name)
    }

    @Test
    fun extractNameWithoutExtension_noExtension() {
        val filename = "songfile"
        val name = filename.substringBeforeLast('.', filename)
        
        assertEquals("songfile", name)
    }

    @Test
    fun buildS3Key_withFolderAndExtension() {
        val userId = "user123"
        val displayName = "My Song"
        val extension = ".mp3"
        val s3Key = "$userId/$displayName$extension"
        
        assertEquals("user123/My Song.mp3", s3Key)
    }

    @Test
    fun buildS3Key_noExtension() {
        val userId = "user123"
        val displayName = "My Song"
        val extension = ""
        val s3Key = "$userId/$displayName$extension"
        
        assertEquals("user123/My Song", s3Key)
    }

    @Test
    fun buildBeatsKey_standardCase() {
        val s3Key = "user123/song.mp3"
        val folder = s3Key.substringBeforeLast("/")
        val filename = s3Key.substringAfterLast("/")
        val name = filename.substringBeforeLast(".", filename)
        val ext = filename.substringAfterLast(".", "")
        val beatsKey = if (ext.isEmpty()) "$folder/${name}_with_beats" else "$folder/${name}_with_beats.$ext"
        
        assertEquals("user123/song_with_beats.mp3", beatsKey)
    }

    @Test
    fun buildBeatsKey_noExtension() {
        val s3Key = "user123/song"
        val folder = s3Key.substringBeforeLast("/")
        val filename = s3Key.substringAfterLast("/")
        val name = filename.substringBeforeLast(".", filename)
        val ext = filename.substringAfterLast(".", "")
        val beatsKey = if (ext.isEmpty()) "$folder/${name}_with_beats" else "$folder/${name}_with_beats.$ext"
        
        assertEquals("user123/song_with_beats", beatsKey)
    }

    @Test
    fun buildBeatsKey_noFolder() {
        val s3Key = "song.mp3"
        val folder = s3Key.substringBeforeLast("/")
        val filename = s3Key.substringAfterLast("/")
        val name = filename.substringBeforeLast(".", filename)
        val ext = filename.substringAfterLast(".", "")
        val beatsKey = if (ext.isEmpty()) "$folder/${name}_with_beats" else "$folder/${name}_with_beats.$ext"
        
        // When there's no "/" it returns the whole string as folder
        assertEquals("song.mp3/song_with_beats.mp3", beatsKey)
    }

    @Test
    fun buildBeatsKey_multipleSlashes() {
        val s3Key = "users/user123/songs/song.flac"
        val folder = s3Key.substringBeforeLast("/")
        val filename = s3Key.substringAfterLast("/")
        val name = filename.substringBeforeLast(".", filename)
        val ext = filename.substringAfterLast(".", "")
        val beatsKey = if (ext.isEmpty()) "$folder/${name}_with_beats" else "$folder/${name}_with_beats.$ext"
        
        assertEquals("users/user123/songs/song_with_beats.flac", beatsKey)
    }

    @Test
    fun formatTime_zeroSeconds() {
        val ms = 0L
        val s = ms / 1000
        val formatted = "%d:%02d".format(s / 60, s % 60)
        
        assertEquals("0:00", formatted)
    }

    @Test
    fun formatTime_oneMinute() {
        val ms = 60_000L
        val s = ms / 1000
        val formatted = "%d:%02d".format(s / 60, s % 60)
        
        assertEquals("1:00", formatted)
    }

    @Test
    fun formatTime_twoMinutesThirtySeconds() {
        val ms = 150_000L
        val s = ms / 1000
        val formatted = "%d:%02d".format(s / 60, s % 60)
        
        assertEquals("2:30", formatted)
    }

    @Test
    fun formatTime_oneHour() {
        val ms = 3_600_000L
        val s = ms / 1000
        val formatted = "%d:%02d".format(s / 60, s % 60)
        
        assertEquals("60:00", formatted)
    }

    @Test
    fun formatTime_withPadding() {
        val ms = 65_000L // 1:05
        val s = ms / 1000
        val formatted = "%d:%02d".format(s / 60, s % 60)
        
        assertEquals("1:05", formatted)
    }

    @Test
    fun trimWhitespace_leadingAndTrailing() {
        val input = "  test  "
        val result = input.trim()
        
        assertEquals("test", result)
    }

    @Test
    fun trimWhitespace_onlyWhitespace() {
        val input = "   "
        val result = input.trim()
        
        assertEquals("", result)
    }

    @Test
    fun trimWhitespace_middleWhitespace() {
        val input = "hello  world"
        val result = input.trim()
        
        assertEquals("hello  world", result)
    }
}

