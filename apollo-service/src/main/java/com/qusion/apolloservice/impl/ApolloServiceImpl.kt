package com.qusion.apolloservice.impl

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.api.ResponseField
import com.apollographql.apollo.api.cache.http.HttpCachePolicy
import com.apollographql.apollo.cache.normalized.CacheKey
import com.apollographql.apollo.cache.normalized.CacheKeyResolver
import com.apollographql.apollo.cache.normalized.sql.ApolloSqlHelper
import com.apollographql.apollo.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.apollo.coroutines.toDeferred
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.fetcher.ApolloResponseFetchers
import com.apollographql.apollo.fetcher.ResponseFetcher
import com.qusion.apolloservice.ApolloConfig
import com.qusion.apolloservice.api.ApolloService
import com.qusion.apolloservice.exceptions.BusinessException
import com.qusion.apolloservice.exceptions.NonExistentDataException
import com.qusion.kotlin.lib.extensions.network.NetworkResult
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * @param interceptor Custom header interceptor. Usually used to insert some token or sid.
 *                    null if not provided/used.
 * @param certificatePinner Custom certificate pinner. Used for SSH token pinning for secure connection.
 *                    null if not provided/used.
 * @see ISessionProvider
 *
 * @param config Basic information wrapped in a data class.
 * @see ApolloConfig
 *
 * Build this class using some DI method.
 * */
class ApolloServiceImpl(
        private val context: Context,
        private val interceptor: Interceptor? = null,
        private val certificatePinner: CertificatePinner? = null,
        private val config: ApolloConfig
) : ApolloService {

    @Volatile
    private var client: ApolloClient? = null

    private fun getClient(): ApolloClient {
        return client ?: synchronized(this) {
            client ?: buildApolloClient().also {
                client = it
            }
        }
    }

    override suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> safeQuery(
            query: Query<D, T, V>,
            cachePolicy: HttpCachePolicy.Policy,
            responseFetcher: ResponseFetcher
    ): NetworkResult<T> {
        return try {
            query(query, cachePolicy, responseFetcher)
        } catch (ex: ApolloNetworkException) {
            NetworkResult.Error(ex)
        }
    }

    override suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> safeMutation(
            mutation: Mutation<D, T, V>
    ): NetworkResult<T> {
        return try {
            mutate(mutation)
        } catch (ex: ApolloNetworkException) {
            NetworkResult.Error(ex)
        }
    }

    override fun clearData() {
        getClient().clearNormalizedCache()
    }

    private suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> query(
            query: Query<D, T, V>,
            cachePolicy: HttpCachePolicy.Policy = HttpCachePolicy.NETWORK_ONLY,
            responseFetcher: ResponseFetcher = ApolloResponseFetchers.NETWORK_ONLY
    ): NetworkResult<T> {

        val response =
                getClient().query(query).httpCachePolicy(cachePolicy).responseFetcher(responseFetcher)
                        .toDeferred().await()

        if (response.hasErrors()) {
            val error = response.errors().first()
            return NetworkResult.Error(
                    cause = BusinessException(error.message() ?: "")
            )
        }
        if (response.data() == null) {
            return NetworkResult.Error(
                    cause = NonExistentDataException()
            )
        }

        return NetworkResult.Success(response.data()!!)
    }

    private suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> mutate(
            mutation: Mutation<D, T, V>
    ): NetworkResult<T> {

        val response = getClient().mutate(mutation).toDeferred().await()

        if (response.hasErrors()) {
            val error = response.errors().first()
            return NetworkResult.Error(
                    cause = BusinessException(error.message() ?: "")
            )
        }
        if (response.data() == null) {
            return NetworkResult.Error(
                    cause = NonExistentDataException()
            )
        }
        return NetworkResult.Success(response.data()!!)

    }

    private fun buildApolloClient(): ApolloClient {
        val okHttpClient = OkHttpClient.Builder().apply {
            addInterceptor(HttpLoggingInterceptor().setLevel(config.HTTP_LOG_LEVEL))
            if (interceptor != null) {
                addInterceptor(interceptor)
            }
            if (certificatePinner != null) {
                certificatePinner(certificatePinner)
            }
        }.build()

        val apolloSqlHelper = ApolloSqlHelper.create(context, config.DB_NAME)

        val cacheFactory = SqlNormalizedCacheFactory(apolloSqlHelper)
        val resolver = object : CacheKeyResolver() {

            override fun fromFieldRecordSet(
                    field: ResponseField,
                    recordSet: MutableMap<String, Any>
            ): CacheKey {
                return CacheKey.NO_KEY
            }

            override fun fromFieldArguments(
                    field: ResponseField,
                    variables: Operation.Variables
            ): CacheKey {
                return CacheKey.NO_KEY
            }
        }

        return ApolloClient.builder()
                .serverUrl(config.SERVER_BASE_URL)
                .okHttpClient(okHttpClient)
                .normalizedCache(cacheFactory, resolver)
                .build()
    }
}