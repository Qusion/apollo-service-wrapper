package com.qusion.apolloservice.api

/**
 * Used for session persistance.
 * Implement using EncryptedSharedPreferences. (ideally)
 * */
interface ISessionProvider {
    fun getSID(): String

    fun setSID()

    fun clear()
}