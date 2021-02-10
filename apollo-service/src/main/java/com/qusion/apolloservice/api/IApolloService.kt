package com.qusion.apolloservice.api

import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.api.cache.http.HttpCachePolicy
import com.apollographql.apollo.fetcher.ApolloResponseFetchers
import com.apollographql.apollo.fetcher.ResponseFetcher
import com.qusion.kotlin.lib.extensions.network.NetworkResult
import kotlinx.coroutines.flow.Flow

/** Injected in every Repository */
interface IApolloService {

    /** Calls query on ApolloClient.
     * Catches errors and responds to expired sid.
     * This is the exposed entry point that repositories talk to when they need to make a query
     * Safe to call in any coroutine (its main-safe)
     *
     * @param cachePolicy specify which cache policy should the query be built with
     * @see HttpCachePolicy
     *
     * @param responseFetcher specify which responseFetcher should the query be built with
     * (usually the same as cachePolicy)
     * @see ApolloResponseFetchers
     *
     * @return NetworkResult.Error of the cause or NetworkResult.Success with the correct data
     * @see NetworkResult */
    suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> safeQuery(
        query: Query<D, T, V>,
        cachePolicy: HttpCachePolicy.Policy = HttpCachePolicy.NETWORK_ONLY,
        responseFetcher: ResponseFetcher = ApolloResponseFetchers.NETWORK_ONLY
    ): NetworkResult<T>

    /** Calls query on ApolloClient
     * Returns cached data first then calls the query and gets latest network data.
     * It exposes all of that as [Flow]
     * Safe to call in any coroutine (its main-safe)
     *
     * It uses [ApolloResponseFetchers.CACHE_AND_NETWORK]
     *
     * @return [Flow] of NetworkResult.Error of the cause or NetworkResult.Success with the correct data
     * @see NetworkResult */
    suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> flow(
        query: Query<D, T, V>
    ): Flow<NetworkResult<T>>

    /** Calls mutation on ApolloClient.
     * Catches errors and responds to expired sid.
     * This is the exposed entry point that repositories talk to when they need to make a mutation
     * Safe to call in any coroutine (its main-safe)
     *
     * @return NetworkResult.Error of the cause or NetworkResult.Success with the correct data
     * @see NetworkResult */
    suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> safeMutation(
        mutation: Mutation<D, T, V>
    ): NetworkResult<T>

    /** Calls query on ApolloClient.
     * This function doesn't respond to errors and just passes them.
     *
     * If you wan't to leverage provided error handling, please call safeQuery instead
     * Only exposed for special needs, you shouldn't have to use this
     * Safe to call in any coroutine (its main-safe)
     *
     * @param cachePolicy specify which cache policy should the query be built with
     * @see HttpCachePolicy
     *
     * @param responseFetcher specify which responseFetcher should the query be built with
     * (usually the same as cachePolicy)
     * @see ApolloResponseFetchers
     *
     * @return NetworkResult.Error of the cause or NetworkResult.Success with the correct data
     * @see NetworkResult */
    suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> query(
        query: Query<D, T, V>,
        cachePolicy: HttpCachePolicy.Policy = HttpCachePolicy.NETWORK_ONLY,
        responseFetcher: ResponseFetcher = ApolloResponseFetchers.NETWORK_ONLY
    ): NetworkResult<T>


    /** Calls query on ApolloClient.
     * This function doesn't respond to errors and just passes them.
     *
     * If you wan't to leverage provided error handling, please call safeMutation instead
     * Only exposed for special needs, you shouldn't have to use this
     * Safe to call in any coroutine (its main-safe)
     *
     * @return NetworkResult.Error of the cause or NetworkResult.Success with the correct data
     * @see NetworkResult */
    suspend fun <D : Operation.Data, T : Operation.Data, V : Operation.Variables> mutate(
        mutation: Mutation<D, T, V>
    ): NetworkResult<T>

    /**
     * Clears the normalized cache (on disk cache).
     */
    fun clearData()

    /**
     * Calls refresh token directly, if provided
     */
    suspend fun refreshToken()
}
