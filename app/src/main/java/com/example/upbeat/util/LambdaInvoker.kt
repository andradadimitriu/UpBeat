package com.example.upbeat.util

import android.content.Context
import android.util.Log
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

    private val REGION = AwsConfig.REGION
    private val FUNCTION_ARN = AwsConfig.LAMBDA_ARN

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
                        override suspend fun resolve(attributes: Attributes) =
                            CognitoCredentialsProvider.getCredentials(context)
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
