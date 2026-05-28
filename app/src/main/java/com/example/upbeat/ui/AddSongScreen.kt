package com.example.upbeat.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var songName by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf("") }
    var showDuplicateNameError by remember { mutableStateOf(false) }
    var duplicateSongName by remember { mutableStateOf("") }
    var showUploadError by remember { mutableStateOf(false) }
    var uploadErrorMessage by remember { mutableStateOf("") }

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
                isUploading = true
                uploadStatus = "Uploading to cloud..."

                val uploadResult = S3Uploader.uploadSong(context, it, s3FileName)
                if (uploadResult == null) {
                    isUploading = false
                    uploadErrorMessage = "Failed to upload song. Please check your connection and try again."
                    showUploadError = true
                    return@launch
                }

                Log.d("AddSongScreen", "Uploaded to key=${uploadResult.key}, invoking Lambda")
                uploadStatus = "Starting beat detection..."

                val lambdaResponse = LambdaInvoker.invokeProcessSong(
                    context = context,
                    s3Key = uploadResult.key,
                    bucket = uploadResult.bucket
                )

                Log.d("AddSongScreen", "lambdaResponse=$lambdaResponse")

                // Lambda failure is non-critical - song is already uploaded
                if (lambdaResponse == null) {
                    Log.w("AddSongScreen", "Beat detection could not be started, but song was uploaded")
                }

                // Success! Add song and navigate to its detail screen
                isUploading = false
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
            title = { Text("Upload Failed") },
            text = { Text(uploadErrorMessage) },
            confirmButton = {
                TextButton(onClick = { showUploadError = false }) {
                    Text("OK")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = uploadStatus,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                OutlinedTextField(
                    value = songName,
                    onValueChange = { songName = it },
                    label = { Text("Song Name (optional)") },
                    placeholder = { Text("Leave blank to use file name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        launcher.launch("audio/*")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                ) {
                    Text("Upload Song")
                }
            }
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


