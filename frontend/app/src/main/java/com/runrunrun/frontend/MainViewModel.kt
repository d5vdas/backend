package com.runrunrun.frontend

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.runrunrun.frontend.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class UiState(
    val loading: Boolean = false,
    val token: String? = null,
    val email: String? = null,
    val activities: List<ActivityResponse> = emptyList(),
    val selectedActivity: ActivityResponse? = null,
    val lastRoutePoints: List<PointRequest> = emptyList(),
    val message: String? = null
)

class MainViewModel(
    private val api: ApiService,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            tokenStore.tokenFlow.collect { token ->
                _state.value = _state.value.copy(token = token)
                if (!token.isNullOrBlank()) {
                    runCatching { api.me("Bearer $token") }
                        .onSuccess { _state.value = _state.value.copy(email = it["email"]) }
                }
            }
        }
    }

    fun register(name: String, email: String, password: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, message = null)
        runCatching { api.register(RegisterRequest(name, email, password)) }
            .onSuccess {
                tokenStore.saveToken(it.token)
                _state.value = _state.value.copy(loading = false, message = "Registered")
            }
            .onFailure {
                _state.value = _state.value.copy(loading = false, message = it.message)
            }
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, message = null)
        runCatching { api.login(LoginRequest(email, password)) }
            .onSuccess {
                tokenStore.saveToken(it.token)
                _state.value = _state.value.copy(loading = false, message = "Logged in")
            }
            .onFailure {
                _state.value = _state.value.copy(loading = false, message = it.message)
            }
    }

    fun loadActivities() = viewModelScope.launch {
        val token = state.value.token ?: return@launch
        _state.value = _state.value.copy(loading = true, message = null)
        runCatching { api.myActivities("Bearer $token") }
            .onSuccess { _state.value = _state.value.copy(loading = false, activities = it, message = "Loaded ${it.size} activities") }
            .onFailure { _state.value = _state.value.copy(loading = false, message = it.message) }
    }

    fun openActivity(activity: ActivityResponse) {
        _state.value = _state.value.copy(selectedActivity = activity)
    }

    fun closeActivity() {
        _state.value = _state.value.copy(selectedActivity = null)
    }

    fun quickStartRun() = viewModelScope.launch {
        val token = state.value.token ?: return@launch
        _state.value = _state.value.copy(loading = true, message = null)
        runCatching { api.startActivity("Bearer $token", StartActivityRequest("RUN")) }
            .onSuccess {
                _state.value = _state.value.copy(loading = false, message = "Started activity #${it.id}")
                loadActivities()
            }
            .onFailure { _state.value = _state.value.copy(loading = false, message = it.message) }
    }

    fun quickStopWithDemoPoints(activity: ActivityResponse) = viewModelScope.launch {
        val token = state.value.token ?: return@launch
        val now = LocalDateTime.now()
        val points = listOf(
            PointRequest(12.9716, 77.5946, now.minusMinutes(2).toString(), 1),
            PointRequest(12.9722, 77.5954, now.minusMinutes(1).toString(), 2),
            PointRequest(12.9730, 77.5960, now.toString(), 3)
        )
        _state.value = _state.value.copy(loading = true, message = null, lastRoutePoints = points)

        runCatching {
            api.addPoints("Bearer $token", activity.id, AddPointsRequest(points))
            api.stopActivity("Bearer $token", activity.id, StopActivityRequest(LocalDateTime.now().toString()))
        }.onSuccess {
            _state.value = _state.value.copy(loading = false, message = "Stopped activity #${it.id}", selectedActivity = it)
            loadActivities()
        }.onFailure {
            _state.value = _state.value.copy(loading = false, message = it.message)
        }
    }

    fun likeSelected() = viewModelScope.launch {
        val token = state.value.token ?: return@launch
        val id = state.value.selectedActivity?.id ?: return@launch
        runCatching { api.like("Bearer $token", id) }
            .onSuccess { _state.value = _state.value.copy(message = "Liked activity") }
            .onFailure { _state.value = _state.value.copy(message = it.message) }
    }

    fun commentSelected(text: String) = viewModelScope.launch {
        val token = state.value.token ?: return@launch
        val id = state.value.selectedActivity?.id ?: return@launch
        runCatching { api.comment("Bearer $token", id, CommentRequest(text)) }
            .onSuccess { _state.value = _state.value.copy(message = "Comment added") }
            .onFailure { _state.value = _state.value.copy(message = it.message) }
    }

    fun logout() = viewModelScope.launch {
        tokenStore.clear()
        _state.value = UiState(message = "Logged out")
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val vm = MainViewModel(ApiClient.service, TokenStore(context.applicationContext))
            @Suppress("UNCHECKED_CAST")
            return vm as T
        }
    }
}
