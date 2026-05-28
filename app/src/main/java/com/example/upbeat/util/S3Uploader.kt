package com.example.upbeat.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.content.ByteStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.hours

object S3Uploader {

    private val BUCKET_NAME = AwsConfig.BUCKET_NAME
    private val REGION = AwsConfig.REGION

    data class UploadResult(
        val bucket: String,
        val key: String,
        val s3Url: String
    )

    data class SongInfo(
        val displayName: String, // e.g. "My Song" (no extension, no _with_beats)
        val s3Key: String        // e.g. "uuid/My Song.flac"
    )

    private fun buildS3Client(context: Context) = S3Client {
        region = REGION
        credentialsProvider = object : CredentialsProvider {
            override suspend fun resolve(attributes: Attributes) =
                CognitoCredentialsProvider.getCredentials(context)
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun uploadSong(context: Context, fileUri: Uri, fileName: String): UploadResult? {
        if (!isNetworkAvailable(context)) {
            Log.e("S3Uploader", "No network available — skipping upload")
            return null
        }
        val file = uriToFile(context, fileUri) ?: return null
        val userId = UserIdentityManager.getUserId(context)
        val objectKey = "$userId/$fileName"

        return withContext(Dispatchers.IO) {
            try {
                val s3Client = buildS3Client(context)

                s3Client.putObject(PutObjectRequest {
                    bucket = BUCKET_NAME
                    key = objectKey
                    body = ByteStream.fromBytes(file.readBytes())
                    contentLength = file.length()
                })

                Log.d("S3Uploader", "Upload successful: $objectKey")
                s3Client.close()
                UploadResult(
                    bucket = BUCKET_NAME,
                    key = objectKey,
                    s3Url = "s3://$BUCKET_NAME/$objectKey"
                )
            } catch (e: Exception) {
                Log.e("S3Uploader", "Upload failed", e)
                null
            }
        }
    }

    suspend fun listSongsFromS3(context: Context): List<SongInfo>? {
        if (!isNetworkAvailable(context)) {
            Log.e("S3Uploader", "No network available — skipping list")
            return null
        }
        val userId = UserIdentityManager.getUserId(context)
        return withContext(Dispatchers.IO) {
            try {
                val s3Client = buildS3Client(context)
                val response = s3Client.listObjectsV2(ListObjectsV2Request {
                    bucket = BUCKET_NAME
                    prefix = "$userId/"
                })
                Log.d("S3Uploader", "List response: keyCount=${response.keyCount}, contents=${response.contents?.size}")
                val songs = response.contents?.mapNotNull { obj ->
                    val key = obj.key ?: return@mapNotNull null
                    val filename = key.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val displayName = filename.substringBeforeLast(".", filename)
                    // Skip the beat-detection output files
                    if (displayName.endsWith("_with_beats")) return@mapNotNull null
                    SongInfo(displayName = displayName, s3Key = key)
                } ?: emptyList()
                Log.d("S3Uploader", "Found ${songs.size} songs for user $userId")
                s3Client.close()
                songs
            } catch (e: Exception) {
                Log.e("S3Uploader", "Failed to list songs from S3: ${e::class.simpleName}: ${e.message}", e)
                null
            }
        }
    }

    suspend fun getPresignedUrl(context: Context, key: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val s3Client = buildS3Client(context)
                val presigned = s3Client.presignGetObject(
                    input = GetObjectRequest { bucket = BUCKET_NAME; this.key = key },
                    duration = 1.hours
                )
                s3Client.close()
                presigned.url.toString()
            } catch (e: Exception) {
                Log.e("S3Uploader", "Failed to get presigned URL for $key", e)
                null
            }
        }
    }

    data class ObjectMetadata(val contentType: String?, val contentLength: Long?)

    suspend fun checkFileExists(context: Context, key: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val s3Client = buildS3Client(context)
                s3Client.headObject(HeadObjectRequest { bucket = BUCKET_NAME; this.key = key })
                s3Client.close()
                true
            } catch (e: Exception) {
                Log.d("S3Uploader", "File not found or error for $key: ${e.message}")
                false
            }
        }
    }

    suspend fun getObjectMetadata(context: Context, key: String): ObjectMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                val s3Client = buildS3Client(context)
                val response = s3Client.headObject(HeadObjectRequest { bucket = BUCKET_NAME; this.key = key })
                s3Client.close()
                ObjectMetadata(
                    contentType = response.contentType,
                    contentLength = response.contentLength
                )
            } catch (e: Exception) {
                Log.e("S3Uploader", "Failed to get metadata for $key: ${e.message}")
                null
            }
        }
    }

    private fun uriToFile(context: Context, uri: Uri): File? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = File(context.cacheDir, "temp_upload_${System.currentTimeMillis()}")
        return try {
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.use { input ->
                    input.copyTo(outputStream)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
