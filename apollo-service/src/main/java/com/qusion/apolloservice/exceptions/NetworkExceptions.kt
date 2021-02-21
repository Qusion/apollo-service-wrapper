package com.qusion.apolloservice.exceptions

import java.io.IOException

/** Added as a cause when there are "business" errors from the response of the endpoint
 * (e.g. wrong password or username) */
open class BusinessException(override val message: String?) : IOException(message)

/** Thrown by the http interceptor when there is 401 from the server */
open class ExpiredSidException(override val message: String?) : IOException(message)