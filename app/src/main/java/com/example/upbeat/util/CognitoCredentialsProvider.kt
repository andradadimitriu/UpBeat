package com.example.upbeat.util

import android.content.Context
import android.util.Log
import aws.sdk.kotlin.services.cognitoidentity.CognitoIdentityClient
import aws.sdk.kotlin.services.cognitoidentity.model.GetCredentialsForIdentityRequest
import aws.sdk.kotlin.services.cognitoidentity.model.GetIdRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Centralized Cognito credentials provider for AWS services.
 * Manages credential fetching and caching for S3, Lambda, and other AWS services.
 */
object CognitoCredentialsProvider {

    private const val TAG = "CognitoCredentials"
    private const val CREDENTIAL_BUFFER_MS = 300_000L // 5 minutes

    // Shared credential cache
    private var cachedCredentials: Credentials? = null
    private var credentialsExpiration: Long = 0

    /**
     * Gets Cognito credentials, using cached values if still valid.
     * Thread-safe and automatically refreshes expired credentials.
     */
    suspend fun getCredentials(context: Context): Credentials {
        // Return cached credentials if still valid (with 5 min buffer)
        val now = System.currentTimeMillis()
        if (cachedCredentials != null && now < credentialsExpiration - CREDENTIAL_BUFFER_MS) {
            Log.d(TAG, "Using cached credentials")
            return cachedCredentials!!
        }

        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching new credentials from Cognito")

                val cognitoClient = CognitoIdentityClient {
                    region = AwsConfig.REGION
                }

                // Get Cognito Identity ID (reuse from UserIdentityManager if possible)
                val userId = UserIdentityManager.getUserId(context)
                val identityId = if (userId.startsWith("${AwsConfig.REGION}:")) {
                    userId // Already a Cognito Identity ID
                } else {
                    // Get new identity from pool
                    val idResponse = cognitoClient.getId(GetIdRequest {
                        identityPoolId = AwsConfig.IDENTITY_POOL_ID
                    })
                    idResponse.identityId ?: throw Exception("Failed to get Cognito Identity ID")
                }

                // Get temporary credentials
                val credsResponse = cognitoClient.getCredentialsForIdentity(
                    GetCredentialsForIdentityRequest {
                        this.identityId = identityId
                    }
                )

                val awsCreds = credsResponse.credentials
                    ?: throw Exception("Failed to get credentials from Cognito")

                cognitoClient.close()

                // Cache credentials
                cachedCredentials = Credentials(
                    accessKeyId = awsCreds.accessKeyId ?: throw Exception("No access key"),
                    secretAccessKey = awsCreds.secretKey ?: throw Exception("No secret key"),
                    sessionToken = awsCreds.sessionToken,
                    expiration = awsCreds.expiration
                )
                credentialsExpiration = awsCreds.expiration?.epochSeconds?.times(1000) ?: (now + 3600_000)

                Log.d(TAG, "Credentials fetched successfully, expire at: $credentialsExpiration")
                cachedCredentials!!
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get Cognito credentials", e)
                throw e
            }
        }
    }

    /**
     * Clears cached credentials, forcing a refresh on next request.
     * Useful for testing or when credentials become invalid.
     */
    fun clearCache() {
        cachedCredentials = null
        credentialsExpiration = 0
        Log.d(TAG, "Credential cache cleared")
    }
}

