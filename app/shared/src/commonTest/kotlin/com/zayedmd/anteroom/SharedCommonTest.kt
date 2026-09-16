package com.zayedmd.anteroom

import com.russhwolf.settings.Settings
import com.zayedmd.anteroom.auth.TokenStorage
import com.zayedmd.anteroom.model.AppUser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSettings : Settings {
    private val map = mutableMapOf<String, Any?>()
    override val keys: Set<String> get() = map.keys
    override val size: Int get() = map.size
    override fun clear() = map.clear()
    override fun remove(key: String) { map.remove(key) }
    override fun hasKey(key: String): Boolean = map.containsKey(key)
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String, defaultValue: String): String = map[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = map[key] as? String
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = map[key] as? Int
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = map[key] as? Long
    override fun putFloat(key: String, value: Float) { map[key] = value }
    override fun getFloat(key: String, defaultValue: Float): Float = map[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = map[key] as? Float
    override fun putDouble(key: String, value: Double) { map[key] = value }
    override fun getDouble(key: String, defaultValue: Double): Double = map[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = map[key] as? Double
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = map[key] as? Boolean
}

class SharedCommonTest {

    @Test
    fun testAppUserModelSerialization() {
        val user = AppUser(
            user_id = "test_user_123",
            email = "patient@example.com",
            name = "Jane Doe",
            dob = "1990-05-15",
            language = "en",
            country = "United States",
            profile_completed = true,
            provider = "email"
        )

        val json = Json { ignoreUnknownKeys = true }
        val serialized = json.encodeToString(user)
        val deserialized = json.decodeFromString<AppUser>(serialized)

        assertEquals("test_user_123", deserialized.user_id)
        assertEquals("patient@example.com", deserialized.email)
        assertEquals("Jane Doe", deserialized.name)
        assertEquals("1990-05-15", deserialized.dob)
        assertEquals("en", deserialized.language)
        assertEquals("United States", deserialized.country)
        assertTrue(deserialized.profile_completed)
        assertEquals("email", deserialized.provider)
    }

    @Test
    fun testAppUserDefaultValues() {
        val json = Json { ignoreUnknownKeys = true }
        val rawJson = """{"user_id":"u1","email":"test@test.com"}"""
        val deserialized = json.decodeFromString<AppUser>(rawJson)

        assertEquals("u1", deserialized.user_id)
        assertEquals("test@test.com", deserialized.email)
        assertNull(deserialized.name)
        assertFalse(deserialized.profile_completed)
        assertEquals("email", deserialized.provider)
    }

    @Test
    fun testTokenStorage() {
        val fakeSettings = FakeSettings()
        val tokenStorage = TokenStorage(fakeSettings)

        assertNull(tokenStorage.getToken())

        tokenStorage.saveToken("sample_jwt_token")
        assertEquals("sample_jwt_token", tokenStorage.getToken())

        tokenStorage.clearToken()
        assertNull(tokenStorage.getToken())
    }

    @Test
    fun testDateOfBirthValidationLogic() {
        fun isValidDob(yearStr: String, monthStr: String, dayStr: String): Boolean {
            val y = yearStr.toIntOrNull()
            val m = monthStr.toIntOrNull()
            val d = dayStr.toIntOrNull()
            if (yearStr.length != 4 || y == null || m == null || d == null) return false
            return y in 1900..2026 && m in 1..12 && d in 1..31
        }

        assertTrue(isValidDob("1995", "10", "25"))
        assertTrue(isValidDob("2000", "1", "1"))
        assertFalse(isValidDob("95", "10", "25")) // Year must be 4 digits
        assertFalse(isValidDob("1850", "10", "25")) // Year < 1900
        assertFalse(isValidDob("2030", "10", "25")) // Future year
        assertFalse(isValidDob("1995", "13", "25")) // Month > 12
        assertFalse(isValidDob("1995", "0", "25"))  // Month < 1
        assertFalse(isValidDob("1995", "10", "32")) // Day > 31
    }

    @Test
    fun testEmailValidationLogic() {
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
        fun isValidEmail(email: String): Boolean {
            return emailRegex.matches(email.trim())
        }

        assertTrue(isValidEmail("patient@example.com"))
        assertTrue(isValidEmail("user.name+tag@domain.co.uk"))
        assertFalse(isValidEmail("invalid-email"))
        assertFalse(isValidEmail("@no-user.com"))
        assertFalse(isValidEmail("no-domain@"))
    }
}
