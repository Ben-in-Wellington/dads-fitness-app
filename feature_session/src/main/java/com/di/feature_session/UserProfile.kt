package com.di.feature_session

/**
 * Simple in-memory representation of an app user that the
 * ViewModel / UI layer can use without depending on Room entities.
 */
data class UserProfile(
    val id: Long,
    val name: String,
    val isActive: Boolean
)