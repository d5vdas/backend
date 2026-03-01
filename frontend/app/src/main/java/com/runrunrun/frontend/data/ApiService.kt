package com.runrunrun.frontend.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    @POST("/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("/users/me")
    suspend fun me(@Header("Authorization") bearer: String): Map<String, String>

    @GET("/activities/me")
    suspend fun myActivities(@Header("Authorization") bearer: String): List<ActivityResponse>
}
