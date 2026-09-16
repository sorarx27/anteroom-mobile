package com.zayedmd.anteroom.model

import kotlinx.serialization.Serializable

@Serializable
enum class Relationship(val label: String, val icon: String) {
    self("You", "person"),
    partner("Partner", "heart"),
    child("Child", "happy"),
    parent("Parent", "people"),
    other("Other", "person_add")
}

@Serializable
enum class BiologicalSex(val label: String) {
    female("Female"),
    male("Male"),
    other("Other")
}

@Serializable
data class Profile(
    val profile_id: String,
    val user_id: String,
    val name: String,
    val relationship: Relationship = Relationship.self,
    val dob: String? = null,
    val sex: BiologicalSex? = null,
    val is_self: Boolean = false,
    val created_at: String = "",
    val updated_at: String = ""
) {
    fun initials(): String {
        val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
        }
    }
}
