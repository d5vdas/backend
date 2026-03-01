package com.runrunrun.frontend.data

data class RegisterRequest(val name: String, val email: String, val password: String)
data class LoginRequest(val email: String, val password: String)
data class AuthResponse(val token: String, val tokenType: String)

data class StartActivityRequest(val type: String)
data class StopActivityRequest(val endedAt: String)
data class PointRequest(val latitude: Double, val longitude: Double, val recordedAt: String, val sequenceNo: Int)
data class AddPointsRequest(val points: List<PointRequest>)
data class CommentRequest(val text: String)

data class ActivityResponse(
    val id: Long,
    val type: String,
    val status: String,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val durationSeconds: Long? = null,
    val distanceMeters: Double? = null,
    val averagePaceSecondsPerKm: Double? = null
)

data class SavePointsResponse(val savedPoints: Int)
data class StatusResponse(val status: String)

