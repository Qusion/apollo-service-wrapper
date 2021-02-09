package com.qusion.apolloservice.exceptions

import java.lang.Exception

class BusinessException(override val message: String?): Exception(message)

class ExpiredSidException(override val message: String?): Exception(message)
