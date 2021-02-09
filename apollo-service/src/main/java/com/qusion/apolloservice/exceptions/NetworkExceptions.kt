package com.qusion.apolloservice.exceptions

/** Added as a cause when there are "business" errors from the response of the endpoint
 * (e.g. wrong password or username) */
class BusinessException(override val message: String?) : Exception(message)

/** Thrown by the http interceptor when there is 401 from the server */
open class ExpiredSidException(override val message: String?) : Exception(message)

/** Thrown by ApolloService when refresh token mutation fails */
class ForceLogoutException(code: String) : ExpiredSidException(code)