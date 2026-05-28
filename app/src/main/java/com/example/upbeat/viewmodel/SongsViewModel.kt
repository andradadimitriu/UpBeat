package com.example.upbeat.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.upbeat.model.Song
import com.example.upbeat.util.S3Uploader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SongsViewModel : ViewModel() {
    private val _songs = mutableStateListOf<Song>()
    val songs: List<Song> get() = _songs

    var isLoading = mutableStateOf(false)
        private set
    var loadError = mutableStateOf(false)
        private set

    fun loadSongs(context: Context, retryCount: Int = 3, retryDelayMs: Long = 2000L) {
        viewModelScope.launch {
            isLoading.value = true
            loadError.value = false
            var succeeded = false
            repeat(retryCount) { attempt ->
                if (succeeded) return@repeat
                val songInfos = S3Uploader.listSongsFromS3(context)
                if (songInfos != null) {
                    _songs.clear()
                    _songs.addAll(songInfos.map { Song(name = it.displayName, s3Key = it.s3Key) })
                    succeeded = true
                } else if (attempt < retryCount - 1) {
                    delay(retryDelayMs)
                }
            }
            loadError.value = !succeeded
            isLoading.value = false
        }
    }

    fun getSong(name: String): Song? = _songs.find { it.name.equals(name, ignoreCase = true) }

    fun addSong(name: String, s3Key: String = "") {
        if (!songExists(name)) {
            _songs.add(Song(name.trim(), s3Key))
        }
    }

    fun removeSong(name: String) {
        _songs.removeAll { it.name.equals(name, ignoreCase = true) }
    }

    fun songExists(name: String): Boolean {
        val candidate = name.trim()
        if (candidate.isEmpty()) return false
        return _songs.any { it.name.equals(candidate, ignoreCase = true) }
    }
}
