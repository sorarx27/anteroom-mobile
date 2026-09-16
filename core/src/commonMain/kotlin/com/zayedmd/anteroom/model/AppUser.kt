package com.zayedmd.anteroom.model

import kotlinx.serialization.Serializable

@Serializable
data class AppUser(
    val user_id: String,
    val email: String,
    val name: String? = null,
    val picture: String? = null,
    val dob: String? = null,
    val language: String? = null,
    val country: String? = null,
    val profile_completed: Boolean = false,
    val provider: String = "email"
)
