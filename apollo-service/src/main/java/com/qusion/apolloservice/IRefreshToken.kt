package com.qusion.apolloservice

import com.qusion.apolloservice.api.IApolloService
import com.qusion.kotlin.lib.extensions.network.NetworkResult

interface IRefreshToken {

    abstract suspend fun refreshToken(apolloService: IApolloService): NetworkResult<Any>
}