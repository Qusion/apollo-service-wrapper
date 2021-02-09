package com.qusion.apolloservice

import okhttp3.logging.HttpLoggingInterceptor

data class ApolloConfig(
    val SERVER_BASE_URL: String,
    val DB_NAME: String,
    val DB_PASSPHRASE: String,
    val TIMEOUT_TIME: Long = 10,
    val HTTP_LOG_LEVEL: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC
)