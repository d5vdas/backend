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

data class UiState(
    val loading: Boolean = false,
    val token: String? = null,
    val email: String? = null,
    val activities: List<ActivityResponse> = emptyList(),
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
