package com.example.upbeat.util

/**
 * Shared AWS configuration for the application.
 * These values are safe to embed in the app as they are public identifiers.
 */
object AwsConfig {
    const val IDENTITY_POOL_ID = "eu-north-1:d6ca04bb-eef5-4264-9986-ceedb5bbfe96"
    const val REGION = "eu-north-1"
    const val BUCKET_NAME = "beat-detection-audio"
    const val LAMBDA_ARN = "arn:aws:lambda:eu-north-1:599289652541:function:beat-detection"
}

