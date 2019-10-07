package com.qusion.apolloservice

import okhttp3.logging.HttpLoggingInterceptor

data class ApolloConfig(
    val SERVER_BASE_URL: String,
    val DB_NAME: String,
    val HTTP_LOG_LEVEL: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC
)