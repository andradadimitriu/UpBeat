package com.example.upbeat.ui

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import com.example.upbeat.util.S3Uploader
import com.example.upbeat.viewmodel.SongsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class BeatsStatus { CHECKING, NOT_READY, AVAILABLE }

@Composable
fun SongScreen(songName: String?, songsViewModel: SongsViewModel) {
    val context = LocalContext.current
    val song = remember(songName) { songsViewModel.getSong(songName ?: "") }

    var originalUrl  by remember { mutableStateOf<String?>(null) }
    var beatsUrl     by remember { mutableStateOf<String?>(null) }
    var beatsStatus  by remember { mutableStateOf(BeatsStatus.CHECKING) }
    var originalMime by remember { mutableStateOf<String?>(null) }
    var beatsMime    by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(song) {
        song ?: return@LaunchedEffect

        val origMeta = S3Uploader.getObjectMetadata(song.s3Key)
        Log.d("SongScreen", "Original  → type=${origMeta?.contentType}  size=${origMeta?.contentLength}B")
        originalUrl = S3Uploader.getPresignedUrl(song.s3Key)
        originalMime = originalUrl?.let { probeAudioMime("Original", it) }

        val beatsKey = buildBeatsKey(song.s3Key)
        Log.d("SongScreen", "Checking for beats file: $beatsKey")
        val exists = S3Uploader.checkFileExists(beatsKey)
        if (exists) {
            val beatsMeta = S3Uploader.getObjectMetadata(beatsKey)
            Log.d("SongScreen", "With-beats → type=${beatsMeta?.contentType}  size=${beatsMeta?.contentLength}B")
            beatsUrl = S3Uploader.getPresignedUrl(beatsKey)
            beatsMime = beatsUrl?.let { probeAudioMime("With-beats", it) }
        }
        beatsStatus = if (exists) BeatsStatus.AVAILABLE else BeatsStatus.NOT_READY
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(songName ?: "Unknown Song", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Spacer(modifier = Modifier.height(8.dp))
        if (originalUrl != null) AudioPlayerCard("Original", originalUrl!!, originalMime)
        else LoadingCard("Loading original…")

        Spacer(modifier = Modifier.height(8.dp))
        when (beatsStatus) {
            BeatsStatus.CHECKING  -> LoadingCard("Checking beat detection…")
            BeatsStatus.NOT_READY -> NotReadyCard()
            BeatsStatus.AVAILABLE -> beatsUrl?.let { AudioPlayerCard("With Beats", it, beatsMime) }
                ?: LoadingCard("Loading beats…")
        }
    }
}

private suspend fun probeAudioMime(label: String, url: String): String? =
    withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(url)
            for (i in 0 until extractor.trackCount) {
                val fmt  = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue
                val exoMime = if (mime == "audio/raw") "audio/wav" else mime
                val sampleRate = runCatching { fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(-1)
                val channels   = runCatching { fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(-1)
                val durationUs = runCatching { fmt.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(-1L)
                Log.d("SongScreen", "[$label] mime=$mime → $exoMime  sr=$sampleRate  ch=$channels  dur=${durationUs/1_000_000}s")
                return@withContext exoMime
            }
            null
        } catch (e: Exception) {
            Log.e("SongScreen", "[$label] probe failed: ${e.message}")
            null
        } finally {
            extractor.release()
        }
    }

private fun buildBeatsKey(s3Key: String): String {
    val folder   = s3Key.substringBeforeLast("/")
    val filename = s3Key.substringAfterLast("/")
    val name     = filename.substringBeforeLast(".", filename)
    val ext      = filename.substringAfterLast(".", "")
    return if (ext.isEmpty()) "$folder/${name}_with_beats" else "$folder/${name}_with_beats.$ext"
}

/** Routes to MediaPlayer (WAV/raw PCM) or ExoPlayer (compressed) based on probed MIME. */
@Composable
private fun AudioPlayerCard(title: String, url: String, mimeType: String? = null) {
    if (mimeType == "audio/wav") {
        MediaPlayerCard(title, url)
    } else {
        ExoPlayerCard(title, url)
    }
}

// ── MediaPlayer card (for raw PCM / WAV content) ─────────────────────────────

@Composable
private fun MediaPlayerCard(title: String, url: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var isReady   by remember(url) { mutableStateOf(false) }
    var position  by remember { mutableLongStateOf(0L) }
    var duration  by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }

    val player = remember(url) {
        MediaPlayer().apply {
            setOnPreparedListener { mp ->
                isReady  = true
                duration = mp.duration.toLong()
                Log.d("AudioPlayer", "[$title][MP] ready — duration: ${mp.duration}ms")
            }
            setOnCompletionListener  { isPlaying = false }
            setOnErrorListener { _, what, extra ->
                Log.e("AudioPlayer", "[$title][MP] error what=$what extra=$extra")
                false
            }
            setDataSource(url)
            prepareAsync()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (isReady && !isSeeking) {
                try { position = player.currentPosition.toLong() } catch (_: Exception) {}
            }
            delay(500)
        }
    }

    DisposableEffect(url) {
        onDispose {
            player.stop()
            player.release()
        }
    }

    PlayerCardContent(
        title     = title,
        isReady   = isReady,
        isPlaying = isPlaying,
        position  = position,
        duration  = duration,
        isSeeking = isSeeking,
        onSeekStart   = { isSeeking = true; position = it },
        onSeekFinish  = { player.seekTo(position.toInt()); isSeeking = false },
        onTogglePlay  = {
            if (player.isPlaying) {
                player.pause(); isPlaying = false
            } else {
                player.start(); isPlaying = true
            }
        }
    )
}

// ── ExoPlayer card (for compressed audio: FLAC, MP3, AAC …) ──────────────────

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ExoPlayerCard(title: String, url: String) {
    val context   = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isReady   by remember(url) { mutableStateOf(false) }
    var position  by remember { mutableLongStateOf(0L) }
    var duration  by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }

    val exoPlayer = remember(url) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
        val msFactory = ProgressiveMediaSource.Factory(httpFactory, extractors)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(msFactory)
            .build().apply {
                setMediaItem(MediaItem.fromUri(url))
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            isReady = true
                            val dur = this@apply.duration
                            if (dur != C.TIME_UNSET) {
                                duration = dur
                                Log.d("AudioPlayer", "[$title][Exo] ready — duration: ${dur}ms")
                            }
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("AudioPlayer", "[$title][Exo] error: ${error.errorCodeName}")
                    }
                })
                prepare()
            }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isSeeking) {
                position = exoPlayer.currentPosition
                val dur = exoPlayer.duration
                if (dur != C.TIME_UNSET && dur > 0L) duration = dur
            }
            delay(500)
        }
    }

    DisposableEffect(url) { onDispose { exoPlayer.release() } }

    PlayerCardContent(
        title     = title,
        isReady   = isReady,
        isPlaying = isPlaying,
        position  = position,
        duration  = duration,
        isSeeking = isSeeking,
        onSeekStart   = { isSeeking = true; position = it },
        onSeekFinish  = { exoPlayer.seekTo(position); isSeeking = false },
        onTogglePlay  = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }
    )
}

// ── Shared card UI ────────────────────────────────────────────────────────────

@Composable
private fun PlayerCardContent(
    title: String,
    isReady: Boolean,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    isSeeking: Boolean,
    onSeekStart: (Long) -> Unit,
    onSeekFinish: () -> Unit,
    onTogglePlay: () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (!isReady) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        )
                    }
                }
            }
            if (isReady && duration > 0L) {
                Slider(
                    value         = position.coerceIn(0L, duration).toFloat(),
                    valueRange    = 0f..duration.toFloat(),
                    onValueChange = { onSeekStart(it.toLong()) },
                    onValueChangeFinished = onSeekFinish,
                    modifier      = Modifier.fillMaxWidth()
                )
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMs(position), style = MaterialTheme.typography.labelSmall)
                    Text(formatMs(duration), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun LoadingCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NotReadyCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text("⏳  Beat detection in progress — check back later.",
            modifier = Modifier.padding(16.dp),
            style    = MaterialTheme.typography.bodyMedium)
    }
}
