package com.example.upbeat.ui

import android.net.Uri
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import com.example.upbeat.util.S3Uploader
import com.example.upbeat.util.SongDownloader
import com.example.upbeat.viewmodel.SongsViewModel
import kotlinx.coroutines.delay
import java.io.File

private enum class BeatsStatus { CHECKING, NOT_READY, AVAILABLE }

/** Tracks the download state of a single audio file. */
private sealed class DownloadState {
    object Idle        : DownloadState()
    data class Progress(val fraction: Float) : DownloadState()
    data class Ready(val file: File)         : DownloadState()
    object Error       : DownloadState()
}

@Composable
fun SongScreen(songName: String?, songsViewModel: SongsViewModel) {
    val context = LocalContext.current
    val song = remember(songName) { songsViewModel.getSong(songName ?: "") }

    var originalState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var beatsState    by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var beatsStatus   by remember { mutableStateOf(BeatsStatus.CHECKING) }

    LaunchedEffect(song) {
        song ?: return@LaunchedEffect

        // ── Original ──────────────────────────────────────────────────────────
        originalState = DownloadState.Progress(0f)
        val origFile = SongDownloader.ensureDownloaded(context, song.s3Key) { done, total ->
            val frac = if (total > 0) done.toFloat() / total else 0f
            originalState = DownloadState.Progress(frac)
        }
        originalState = if (origFile != null) DownloadState.Ready(origFile) else DownloadState.Error

        // ── With-beats ────────────────────────────────────────────────────────
        val beatsKey = buildBeatsKey(song.s3Key)
        Log.d("SongScreen", "Checking for beats file: $beatsKey")
        val exists = S3Uploader.checkFileExists(context, beatsKey)
        if (exists) {
            beatsStatus = BeatsStatus.AVAILABLE
            beatsState  = DownloadState.Progress(0f)
            val beatsFile = SongDownloader.ensureDownloaded(context, beatsKey) { done, total ->
                val frac = if (total > 0) done.toFloat() / total else 0f
                beatsState = DownloadState.Progress(frac)
            }
            beatsState = if (beatsFile != null) DownloadState.Ready(beatsFile) else DownloadState.Error
        } else {
            beatsStatus = BeatsStatus.NOT_READY
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(songName ?: "Unknown Song", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Spacer(modifier = Modifier.height(8.dp))
        when (val s = originalState) {
            is DownloadState.Idle     -> LoadingCard("Preparing…")
            is DownloadState.Progress -> DownloadingCard("Downloading original…", s.fraction)
            is DownloadState.Ready    -> AudioPlayerCard("Original", Uri.fromFile(s.file))
            is DownloadState.Error    -> ErrorCard("Failed to load original.")
        }

        Spacer(modifier = Modifier.height(8.dp))
        when (beatsStatus) {
            BeatsStatus.CHECKING  -> LoadingCard("Checking beat detection…")
            BeatsStatus.NOT_READY -> NotReadyCard()
            BeatsStatus.AVAILABLE -> when (val s = beatsState) {
                is DownloadState.Idle        -> LoadingCard("Preparing beats…")
                is DownloadState.Progress    -> DownloadingCard("Downloading beats…", s.fraction)
                is DownloadState.Ready       -> AudioPlayerCard("With Beats", Uri.fromFile(s.file))
                is DownloadState.Error       -> ErrorCard("Failed to load beats.")
            }
        }
    }
}

private fun buildBeatsKey(s3Key: String): String {
    val folder   = s3Key.substringBeforeLast("/")
    val filename = s3Key.substringAfterLast("/")
    val name     = filename.substringBeforeLast(".", filename)
    val ext      = filename.substringAfterLast(".", "")
    return if (ext.isEmpty()) "$folder/${name}_with_beats" else "$folder/${name}_with_beats.$ext"
}

/** Routes to ExoPlayer using a local file URI. */
@Composable
private fun AudioPlayerCard(title: String, uri: Uri) {
    ExoPlayerCard(title, uri)
}

// ── ExoPlayer card (plays from local file URI) ────────────────────────────────

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ExoPlayerCard(title: String, uri: Uri) {
    val context   = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isReady   by remember(uri) { mutableStateOf(false) }
    var position  by remember { mutableLongStateOf(0L) }
    var duration  by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }

    val exoPlayer = remember(uri) {
        // DefaultDataSource handles file://, content://, and http:// URIs
        val dataSourceFactory = DefaultDataSource.Factory(context)
        // Do NOT use setConstantBitrateSeekingAlwaysEnabled — it forces CBR assumption on all
        // formats (including WAV/PCM from beat-detection output) causing ExoPlayer to
        // miscalculate positions and jump to silence mid-playback.
        val extractors = DefaultExtractorsFactory()
        val msFactory = ProgressiveMediaSource.Factory(dataSourceFactory, extractors)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(msFactory)
            .build().apply {
                setMediaItem(MediaItem.fromUri(uri))
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

    DisposableEffect(uri) { onDispose { exoPlayer.release() } }

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
private fun DownloadingCard(message: String, fraction: Float) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            if (fraction > 0f) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text("⚠️ $message",
            modifier = Modifier.padding(16.dp),
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.error)
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
