package com.qusion.apolloservice.exceptions

import java.lang.Exception

class NonExistentDataException: Exception(MESSAGE) {
    companion object {
        private const val MESSAGE = "Data received is null"
    }
}