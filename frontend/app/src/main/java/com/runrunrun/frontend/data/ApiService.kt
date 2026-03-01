package com.runrunrun.frontend.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("/users/me")
    suspend fun me(@Header("Authorization") bearer: String): Map<String, String>

    @GET("/activities/me")
    suspend fun myActivities(@Header("Authorization") bearer: String): List<ActivityResponse>

    @POST("/activities/start")
    suspend fun startActivity(@Header("Authorization") bearer: String, @Body body: StartActivityRequest): ActivityResponse

    @POST("/activities/{id}/points")
    suspend fun addPoints(
        @Header("Authorization") bearer: String,
        @Path("id") activityId: Long,
        @Body body: AddPointsRequest
    ): SavePointsResponse

    @POST("/activities/{id}/stop")
    suspend fun stopActivity(
        @Header("Authorization") bearer: String,
        @Path("id") activityId: Long,
        @Body body: StopActivityRequest
    ): ActivityResponse

    @GET("/activities/{id}")
    suspend fun activityDetail(@Header("Authorization") bearer: String, @Path("id") activityId: Long): ActivityResponse

    @POST("/social/activities/{activityId}/like")
    suspend fun like(@Header("Authorization") bearer: String, @Path("activityId") activityId: Long): StatusResponse

    @DELETE("/social/activities/{activityId}/like")
    suspend fun unlike(@Header("Authorization") bearer: String, @Path("activityId") activityId: Long): StatusResponse

    @POST("/social/activities/{activityId}/comment")
    suspend fun comment(
        @Header("Authorization") bearer: String,
        @Path("activityId") activityId: Long,
        @Body body: CommentRequest
    ): StatusResponse
}
