package com.qusion.apolloservice

import com.qusion.kotlin.lib.extensions.network.NetworkResult

interface IRefreshToken {

    abstract fun refreshToken(): NetworkResult<Any>
}