package com.runrunrun.frontend.data

data class RegisterRequest(val name: String, val email: String, val password: String)
data class LoginRequest(val email: String, val password: String)
data class AuthResponse(val token: String, val tokenType: String)

data class ActivityResponse(
    val id: Long,
    val type: String,
    val status: String,
    val distanceMeters: Double?
)
