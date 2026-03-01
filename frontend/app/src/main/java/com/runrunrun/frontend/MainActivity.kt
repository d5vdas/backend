package com.runrunrun.frontend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runrunrun.frontend.data.ActivityResponse

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val vm: MainViewModel = viewModel(factory = MainViewModel.Factory(applicationContext))
                AppScreen(vm)
            }
        }
    }
}

@Composable
private fun AppScreen(vm: MainViewModel) {
    val state by vm.state.collectAsState()

    if (state.token.isNullOrBlank()) {
        AuthScreen(
            isLoading = state.loading,
            message = state.message,
            onRegister = vm::register,
            onLogin = vm::login
        )
    } else {
        HomeScreen(
            email = state.email ?: "",
            activities = state.activities,
            message = state.message,
            onLoad = vm::loadActivities,
            onLogout = vm::logout
        )
    }
}

@Composable
private fun AuthScreen(
    isLoading: Boolean,
    message: String?,
    onRegister: (String, String, String) -> Unit,
    onLogin: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("Dev") }
    var email by remember { mutableStateOf("dev@example.com") }
    var password by remember { mutableStateOf("secret123") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("RunRunRun", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onRegister(name, email, password) }, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) { Text("Register") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onLogin(email, password) }, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) { Text("Login") }
        Spacer(Modifier.height(12.dp))
        if (!message.isNullOrBlank()) Text(message)
    }
}

@Composable
private fun HomeScreen(
    email: String,
    activities: List<ActivityResponse>,
    message: String?,
    onLoad: () -> Unit,
    onLogout: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Logged in as $email")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLoad, modifier = Modifier.fillMaxWidth()) { Text("Load My Activities") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
        Spacer(Modifier.height(8.dp))
        if (!message.isNullOrBlank()) Text(message)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(activities) { a ->
                Text("#${a.id} ${a.type} ${a.status} dist=${a.distanceMeters ?: 0.0}")
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
