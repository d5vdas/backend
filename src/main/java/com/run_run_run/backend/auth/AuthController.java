package com.run_run_run.backend.auth;

import com.run_run_run.backend.auth.dto.AuthResponse;
import com.run_run_run.backend.auth.dto.FirebaseAuthRequest;
import com.run_run_run.backend.auth.dto.LoginRequest;
import com.run_run_run.backend.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final FirebaseAuthService firebaseAuthService;

    public AuthController(AuthService authService,
                          FirebaseAuthService firebaseAuthService) {
        this.authService = authService;
        this.firebaseAuthService = firebaseAuthService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/firebase")
    public ResponseEntity<AuthResponse> firebase(@Valid @RequestBody FirebaseAuthRequest request) {
        return ResponseEntity.ok(firebaseAuthService.authenticate(request));
    }
}
