package com.example.upbeat.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.upbeat.util.LambdaInvoker
import com.example.upbeat.util.S3Uploader
import kotlinx.coroutines.launch

@Composable
fun AddSongScreen(
    onSongAdded: (name: String, s3Key: String) -> Unit,
    onSongExists: (String) -> Boolean,
//    onUploadSong: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var songName by remember { mutableStateOf("") }
    var showDuplicateNameError by remember { mutableStateOf(false) }
    var duplicateSongName by remember { mutableStateOf("") }
    var showUploadError by remember { mutableStateOf(false) }
    var showLambdaError by remember { mutableStateOf(false) }
    var pendingSongName by remember { mutableStateOf<String?>(null) }
    var pendingS3Key by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // originalFileName keeps the extension (e.g. "song.mp3") — used to extract the extension
            val originalFileName = getFullFileName(context, it)
                ?: "uploaded_song_${System.currentTimeMillis()}"
            val originalExtension = originalFileName.substringAfterLast('.', "")
                .let { ext -> if (ext.isNotEmpty()) ".$ext" else "" }
            // displayName has no extension — used as the in-app song title
            val displayName = if (songName.isNotBlank()) songName.trim()
                else originalFileName.substringBeforeLast('.', originalFileName).trim()
            // S3 key uses the display name + original extension so the Lambda gets the right format
            val s3FileName = "$displayName$originalExtension"

            if (onSongExists(displayName)) {
                duplicateSongName = displayName
                showDuplicateNameError = true
                return@let
            }
            coroutineScope.launch {
                val uploadResult = S3Uploader.uploadSong(context, it, s3FileName)
                if (uploadResult == null) {
                    showUploadError = true
                    return@launch
                }

                Log.d("AddSongScreen", "Uploaded to key=${uploadResult.key}, invoking Lambda")
                val lambdaResponse = LambdaInvoker.invokeProcessSong(
                    s3Key = uploadResult.key,
                    bucket = uploadResult.bucket
                )
                Log.d("AddSongScreen", "lambdaResponse=$lambdaResponse")
                if (lambdaResponse == null) {
                    pendingSongName = displayName
                    pendingS3Key = uploadResult.key
                    showLambdaError = true
                    return@launch
                }
                onSongAdded(displayName, uploadResult.key)
            }
        }
    }

    if (showDuplicateNameError) {
        AlertDialog(
            onDismissRequest = { showDuplicateNameError = false },
            title = { Text("Song already exists") },
            text = { Text("A song named \"$duplicateSongName\" already exists. Please choose another name.") },
            confirmButton = {
                TextButton(onClick = { showDuplicateNameError = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showUploadError) {
        AlertDialog(
            onDismissRequest = { showUploadError = false },
            title = { Text("Upload failed") },
            text = { Text("Could not upload the song. Please try again.") },
            confirmButton = {
                TextButton(onClick = { showUploadError = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showLambdaError) {
        AlertDialog(
            onDismissRequest = {
                showLambdaError = false
                pendingSongName?.let { onSongAdded(it, pendingS3Key) }
                pendingSongName = null
            },
            title = { Text("Processing failed") },
            text = { Text("Song uploaded, but beat detection could not be started. The song was still added to your library.") },
            confirmButton = {
                TextButton(onClick = {
                    showLambdaError = false
                    pendingSongName?.let { onSongAdded(it, pendingS3Key) }
                    pendingSongName = null
                }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Add a New Song",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = songName,
            onValueChange = { songName = it },
            label = { Text("Song Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                launcher.launch("audio/*")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Upload Song")
        }
    }
}

private fun getFullFileName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    return cursor.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex == -1 || !it.moveToFirst()) return@use null
        it.getString(nameIndex)?.trim()?.ifBlank { null }
    }
}

private fun getFileNameWithoutExtension(context: android.content.Context, uri: Uri): String? {
    return getFullFileName(context, uri)?.let { fullName ->
        fullName.substringBeforeLast('.', fullName).ifBlank { null }
    }
}
