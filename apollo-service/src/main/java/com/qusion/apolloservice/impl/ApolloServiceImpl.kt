package com.qusion.apolloservice.impl

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.*
import com.apollographql.apollo.api.cache.http.HttpCachePolicy
import com.apollographql.apollo.cache.normalized.CacheKey
import com.apollographql.apollo.cache.normalized.CacheKeyResolver
import com.apollographql.apollo.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.apollo.coroutines.await
import com.apollographql.apollo.coroutines.toFlow
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.fetcher.ApolloResponseFetchers
import com.apollographql.apollo.fetcher.ResponseFetcher
import com.qusion.apolloservice.ApolloConfig
import com.qusion.apolloservice.IRefreshToken
import com.qusion.apolloservice.api.IApolloService
import com.qusion.apolloservice.exceptions.BusinessException
import com.qusion.apolloservice.exceptions.ExpiredSidException
import com.qusion.kotlin.lib.extensions.network.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SupportFactory
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * @param interceptors Custom header interceptors. Usually used to insert some token or sid.
 *                    null if not provided/used.
 * @param certificatePinner Custom certificate pinner. Used for SSH token pinning for secure connection.
 *                    null if not provided/used.
 * @param refreshToken Custom class containing refreshTokenLogic. Invoked if we get [ExpiredSidException]
 *
 * @param config Basic information wrapped in a data class.
 * @see ApolloConfig
 *
 * Build this class using some DI method.
 * */
class ApolloServiceImpl(
    private val context: Context,
    private val interceptors: List<Interceptor>? = null,
    private val certificatePinner: CertificatePinner? = null,
    private val refreshToken: IRefreshToken? = null,
    private val customTypeAdapters: List<Pair<ScalarType, CustomTypeAdapter<*>>>? = null,
    private val config: ApolloConfig
) : IApolloService {

    @Volatile
    private var queryClient: ApolloClient? = null

    @Volatile
    private var mutationClient: ApolloClient? = null

    private fun getQueryClient(): ApolloClient {
        return queryClient ?: synchronized(this) {
            queryClient ?: buildApolloQueryClient().also {
                queryClient = it
            }
        }
    }

    @Volatile
    private var isRefreshingToken: Boolean = false

    private fun getMutationClient(): ApolloClient {
        return mutationClient ?: synchronized(this) {
            mutationClient ?: buildApolloMutationClient().also {
                mutationClient = it
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
        } catch (e: ApolloNetworkException) {
            if (e.cause is ExpiredSidException && refreshToken != null) {
                if (!isRefreshingToken) {
                    isRefreshingToken = true
                    val refreshResult = refreshToken.refreshToken(this)

                    isRefreshingToken = false
                    if (refreshResult is NetworkResult.Error) {
                        return refreshResult
                    }
                } else {
                    var retryCount = 0
                    while(isRefreshingToken && retryCount < MAX_RETRY_COUNT) {
                        retryCount++
                        delay(500)
                    }
                }

                query(query)
            } else {
                NetworkResult.Error(cause = e)
            }
        }
    }

    override suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> safeMutation(
        mutation: Mutation<D, T, V>
    ): NetworkResult<T> {
        return try {
            mutate(mutation)
        } catch (e: ApolloNetworkException) {
            if (e.cause is ExpiredSidException && refreshToken != null) {
                if (!isRefreshingToken) {
                    isRefreshingToken = true
                    val refreshResult = refreshToken.refreshToken(this)

                    isRefreshingToken = false
                    if (refreshResult is NetworkResult.Error) {
                        return refreshResult
                    }
                } else {
                    var retryCount = 0
                    while(isRefreshingToken && retryCount < MAX_RETRY_COUNT) {
                        retryCount++
                        delay(500)
                    }
                }

                mutate(mutation)
            } else {

                NetworkResult.Error(cause = e)
            }
        }
    }

    @ExperimentalCoroutinesApi
    override suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> flow(
        query: Query<D, T, V>
    ): Flow<NetworkResult<T>> = getQueryClient()
        .query(query)
        .toBuilder()
        .responseFetcher(ApolloResponseFetchers.CACHE_AND_NETWORK)
        .build()
        .toFlow()
        .map {
            if (it.hasErrors()) {
                parseResponseError(it.errors?.first())
            } else {
                NetworkResult.Success(it.data!!)
            }
        }
        .catch { e ->
            if (e.cause is ExpiredSidException && refreshToken != null) {
                if (!isRefreshingToken) {
                    isRefreshingToken = true
                    val refreshResult = refreshToken.refreshToken(this@ApolloServiceImpl)

                    isRefreshingToken = false
                    if (refreshResult is NetworkResult.Error) {
                        emit(refreshResult)
                    } else {
                        emit(query(query))
                    }
                } else {
                    var retryCount = 0
                    while (isRefreshingToken && retryCount < MAX_RETRY_COUNT) {
                        retryCount++
                        delay(500)
                    }
                    emit(query(query))
                }
            } else {
                emit(NetworkResult.Error(cause = e))
            }
        }
        .flowOn(Dispatchers.IO)

    override suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> query(
        query: Query<D, T, V>,
        cachePolicy: HttpCachePolicy.Policy,
        responseFetcher: ResponseFetcher
    ): NetworkResult<T> = withContext(Dispatchers.IO) {

        val response =
            getQueryClient().query(query).toBuilder().httpCachePolicy(cachePolicy)
                .responseFetcher(responseFetcher).build().await()

        if (response.hasErrors()) {
            return@withContext parseResponseError(response.errors?.first())
        }
        NetworkResult.Success(response.data!!)
    }

    override suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> mutate(
        mutation: Mutation<D, T, V>
    ): NetworkResult<T> = withContext(Dispatchers.IO) {

        val response = getMutationClient().mutate(mutation).await()

        if (response.hasErrors()) {
            return@withContext parseResponseError(response.errors?.first())
        }
        NetworkResult.Success(response.data!!)
    }

    private fun parseResponseError(error: Error?): NetworkResult.Error {
        return NetworkResult.Error(
            cause = BusinessException(
                message = error?.message
            )
        )
    }

    override fun clearData() {
        getQueryClient().clearNormalizedCache()
    }

    override suspend fun refreshToken() {
        refreshToken?.refreshToken(this)
    }

    private fun buildApolloQueryClient(): ApolloClient {
        val okHttpClient = OkHttpClient.Builder().apply {
            addInterceptor(HttpLoggingInterceptor().setLevel(config.HTTP_LOG_LEVEL))
            readTimeout(config.TIMEOUT_TIME, TimeUnit.SECONDS)
            writeTimeout(config.TIMEOUT_TIME, TimeUnit.SECONDS)
            interceptors?.forEach {
                addInterceptor(it)
            }

            if (certificatePinner != null) {
                certificatePinner(certificatePinner)
            }
        }.build()

        val cacheFactory = SqlNormalizedCacheFactory(
            context,
            config.DB_NAME,
            SupportFactory(config.DB_PASSPHRASE.toByteArray())
        )

        val resolver = object : CacheKeyResolver() {
            override fun fromFieldRecordSet(
                field: ResponseField,
                recordSet: Map<String, Any>
            ): CacheKey {
                return if (recordSet.containsKey("id") && recordSet["id"] is String) {
                    CacheKey.from(recordSet["id"] as String)
                } else {
                    CacheKey.NO_KEY
                }
            }

            override fun fromFieldArguments(
                field: ResponseField,
                variables: Operation.Variables
            ): CacheKey {
                return if (field.resolveArgument("id", variables) is String) {
                    CacheKey.from(field.resolveArgument("id", variables) as String)
                } else {
                    CacheKey.NO_KEY
                }
            }
        }

        return ApolloClient.builder().apply {
            serverUrl(config.SERVER_BASE_URL)
            customTypeAdapters?.forEach {
                addCustomTypeAdapter(it.first, it.second)
            }
            okHttpClient(okHttpClient)
            normalizedCache(cacheFactory, resolver)
        }.build()
    }

    private fun buildApolloMutationClient(): ApolloClient {
        val okHttpClient = OkHttpClient.Builder().apply {
            addInterceptor(HttpLoggingInterceptor().setLevel(config.HTTP_LOG_LEVEL))
            readTimeout(config.TIMEOUT_TIME, TimeUnit.SECONDS)
            writeTimeout(config.TIMEOUT_TIME, TimeUnit.SECONDS)
            interceptors?.forEach {
                addInterceptor(it)
            }

            if (certificatePinner != null) {
                certificatePinner(certificatePinner)
            }
        }.build()

        return ApolloClient.builder().apply {
            serverUrl(config.SERVER_BASE_URL)
            customTypeAdapters?.forEach {
                addCustomTypeAdapter(it.first, it.second)
            }
            okHttpClient(okHttpClient)
        }.build()
    }

    companion object {
        private const val MAX_RETRY_COUNT = 10
    }
}