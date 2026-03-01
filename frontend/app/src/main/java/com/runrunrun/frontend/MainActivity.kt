package com.runrunrun.frontend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runrunrun.frontend.data.ActivityResponse
import com.runrunrun.frontend.data.PointRequest

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
    } else if (state.selectedActivity == null) {
        HomeScreen(
            email = state.email ?: "",
            activities = state.activities,
            message = state.message,
            onStartRun = vm::quickStartRun,
            onOpen = vm::openActivity,
            onLoad = vm::loadActivities,
            onLogout = vm::logout
        )
    } else {
        ActivityDetailScreen(
            activity = state.selectedActivity,
            route = state.lastRoutePoints,
            message = state.message,
            onBack = vm::closeActivity,
            onStop = { vm.quickStopWithDemoPoints(it) },
            onLike = vm::likeSelected,
            onComment = vm::commentSelected
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
    onStartRun: () -> Unit,
    onOpen: (ActivityResponse) -> Unit,
    onLoad: () -> Unit,
    onLogout: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Logged in as $email")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onStartRun, modifier = Modifier.fillMaxWidth()) { Text("Start RUN") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLoad, modifier = Modifier.fillMaxWidth()) { Text("Load My Activities") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
        Spacer(Modifier.height(8.dp))
        if (!message.isNullOrBlank()) Text(message)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(activities) { a ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("#${a.id} ${a.type} ${a.status} dist=${a.distanceMeters ?: 0.0}", modifier = Modifier.weight(1f))
                    Button(onClick = { onOpen(a) }) { Text("Open") }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ActivityDetailScreen(
    activity: ActivityResponse?,
    route: List<PointRequest>,
    message: String?,
    onBack: () -> Unit,
    onStop: (ActivityResponse) -> Unit,
    onLike: () -> Unit,
    onComment: (String) -> Unit
) {
    var comment by remember { mutableStateOf("Great run!") }
    if (activity == null) return

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Activity #${activity.id}", style = MaterialTheme.typography.headlineSmall)
        Text("Type: ${activity.type} | Status: ${activity.status}")
        Text("Distance: ${activity.distanceMeters ?: 0.0} m")
        Spacer(Modifier.height(8.dp))

        Text("Basic Route Display")
        RouteSketch(route)

        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = onBack) { Text("Back") }
            Spacer(Modifier.width(8.dp))
            if (activity.status == "IN_PROGRESS") {
                Button(onClick = { onStop(activity) }) { Text("Stop + Add Demo Points") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = onLike) { Text("Like") }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onComment(comment) }, modifier = Modifier.fillMaxWidth()) { Text("Post Comment") }

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message)
        }
    }
}

@Composable
private fun RouteSketch(points: List<PointRequest>) {
    val bg = Color(0xFFEFF5FF)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(bg)
    ) {
        if (points.size < 2) return@Canvas

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }

        fun normX(lng: Double): Float {
            val d = (maxLng - minLng).takeIf { it != 0.0 } ?: 1.0
            return ((lng - minLng) / d).toFloat() * (size.width - 40f) + 20f
        }

        fun normY(lat: Double): Float {
            val d = (maxLat - minLat).takeIf { it != 0.0 } ?: 1.0
            return size.height - (((lat - minLat) / d).toFloat() * (size.height - 40f) + 20f)
        }

        for (i in 1 until points.size) {
            val p1 = points[i - 1]
            val p2 = points[i]
            drawLine(
                color = Color(0xFF1565C0),
                start = Offset(normX(p1.longitude), normY(p1.latitude)),
                end = Offset(normX(p2.longitude), normY(p2.latitude)),
                strokeWidth = 6f
            )
        }

        val first = points.first()
        val last = points.last()
        drawCircle(Color(0xFF2E7D32), 8f, Offset(normX(first.longitude), normY(first.latitude)), style = Stroke(width = 4f))
        drawCircle(Color(0xFFC62828), 8f, Offset(normX(last.longitude), normY(last.latitude)), style = Stroke(width = 4f))
    }
}
