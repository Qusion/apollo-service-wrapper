package com.qusion.apolloservice

import com.qusion.kotlin.lib.extensions.network.NetworkResult

interface IRefreshToken {

    abstract suspend fun refreshToken(): NetworkResult<Any>
}