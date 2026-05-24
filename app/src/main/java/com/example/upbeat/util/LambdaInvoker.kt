package com.example.upbeat.util

import android.content.Context
import android.util.Log
import aws.sdk.kotlin.services.cognitoidentity.CognitoIdentityClient
import aws.sdk.kotlin.services.cognitoidentity.model.GetCredentialsForIdentityRequest
import aws.sdk.kotlin.services.cognitoidentity.model.GetIdRequest
import aws.sdk.kotlin.services.lambda.LambdaClient
import aws.sdk.kotlin.services.lambda.model.InvocationType
import aws.sdk.kotlin.services.lambda.model.InvokeRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object LambdaInvoker {

    private const val IDENTITY_POOL_ID = "YOUR_IDENTITY_POOL_ID" // e.g., "eu-north-1:xxxx-xxxx-xxxx"
    private const val REGION = "eu-north-1"
    private const val FUNCTION_ARN = "arn:aws:lambda:eu-north-1:599289652541:function:beat-detection"

    // Cache credentials to avoid repeated Cognito calls
    private var cachedCredentials: Credentials? = null
    private var credentialsExpiration: Long = 0

    private suspend fun getCognitoCredentials(context: Context): Credentials {
        // Return cached credentials if still valid (with 5 min buffer)
        val now = System.currentTimeMillis()
        if (cachedCredentials != null && now < credentialsExpiration - 300_000) {
            return cachedCredentials!!
        }

        return withContext(Dispatchers.IO) {
            try {
                val cognitoClient = CognitoIdentityClient {
                    region = REGION
                }

                // Get Cognito Identity ID (reuse from UserIdentityManager if possible)
                val userId = UserIdentityManager.getUserId(context)
                val identityId = if (userId.startsWith("eu-north-1:")) {
                    userId // Already a Cognito Identity ID
                } else {
                    // Get new identity from pool
                    val idResponse = cognitoClient.getId(GetIdRequest {
                        identityPoolId = IDENTITY_POOL_ID
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

                cachedCredentials!!
            } catch (e: Exception) {
                Log.e("LambdaInvoker", "Failed to get Cognito credentials", e)
                throw e
            }
        }
    }

    suspend fun invokeProcessSong(context: Context, s3Key: String, bucket: String = "beat-detection-audio"): String? {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("bucket", bucket)
                    put("key", s3Key)
                }.toString()

                Log.d("LambdaInvoker", "Invoking with payload: $payload")

                val lambdaClient = LambdaClient {
                    region = REGION
                    credentialsProvider = object : CredentialsProvider {
                        override suspend fun resolve(attributes: Attributes) = getCognitoCredentials(context)
                    }
                }

                val response = lambdaClient.invoke(InvokeRequest {
                    functionName = FUNCTION_ARN
                    invocationType = InvocationType.Event  // async: returns 202 immediately
                    this.payload = payload.toByteArray()
                })

                val responseBody = response.payload?.decodeToString()
                Log.d(
                    "LambdaInvoker",
                    "status=${response.statusCode}, functionError=${response.functionError}, body=$responseBody"
                )

                lambdaClient.close()

                // Async invocation returns 202 — treat as success
                val statusCode = response.statusCode
                if (statusCode == 202 || statusCode == 200) {
                    Log.d("LambdaInvoker", "Lambda accepted (status=$statusCode)")
                    return@withContext ""
                }

                // functionError is set for synchronous invocations when the handler throws
                if (response.functionError != null) {
                    Log.e("LambdaInvoker", "Lambda execution error: ${response.functionError}")
                    return@withContext null
                }

                responseBody ?: ""
            } catch (e: Exception) {
                Log.e("LambdaInvoker", "Error invoking lambda", e)
                null
            }
        }
    }
}
