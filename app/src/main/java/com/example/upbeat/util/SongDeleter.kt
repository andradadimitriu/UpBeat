package com.example.upbeat.util

import android.content.Context
import android.util.Log
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles deletion of songs from S3 and local cache.
 * Deletes both the original song and its _with_beats version if it exists.
 */
object SongDeleter {

    private const val TAG = "SongDeleter"

    /**
     * Deletes a song from S3 (original and beats version) and local cache.
     *
     * @param context Android context
     * @param s3Key The S3 key of the original song
     * @return true if deletion was successful, false otherwise
     */
    suspend fun deleteSong(context: Context, s3Key: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val s3Client = S3Client {
                    region = AwsConfig.REGION
                    credentialsProvider = object : CredentialsProvider {
                        override suspend fun resolve(attributes: Attributes) =
                            CognitoCredentialsProvider.getCredentials(context)
                    }
                }

                // Delete original file from S3
                Log.d(TAG, "Deleting original: $s3Key")
                s3Client.deleteObject(DeleteObjectRequest {
                    bucket = AwsConfig.BUCKET_NAME
                    key = s3Key
                })

                // Delete beats version if it exists
                val beatsKey = buildBeatsKey(s3Key)
                Log.d(TAG, "Attempting to delete beats file: $beatsKey")
                try {
                    s3Client.deleteObject(DeleteObjectRequest {
                        bucket = AwsConfig.BUCKET_NAME
                        key = beatsKey
                    })
                    Log.d(TAG, "Beats file deleted: $beatsKey")
                } catch (e: Exception) {
                    // Beats file might not exist, that's okay
                    Log.d(TAG, "Beats file not found or already deleted: $beatsKey")
                }

                s3Client.close()

                // Delete from local cache
                SongDownloader.evict(context, s3Key)
                SongDownloader.evict(context, beatsKey)

                Log.d(TAG, "Successfully deleted song: $s3Key")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete song: $s3Key", e)
                false
            }
        }
    }

    /**
     * Builds the S3 key for the beats version of a song.
     */
    private fun buildBeatsKey(s3Key: String): String {
        val folder = s3Key.substringBeforeLast("/")
        val filename = s3Key.substringAfterLast("/")
        val name = filename.substringBeforeLast(".", filename)
        val ext = filename.substringAfterLast(".", "")
        return "$folder/${name}_with_beats.wav"
    }
}

