package com.run_run_run.backend.auth;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.run_run_run.backend.auth.dto.AuthResponse;
import com.run_run_run.backend.auth.dto.FirebaseAuthRequest;
import com.run_run_run.backend.user.User;
import com.run_run_run.backend.user.UserRepository;

@Service
public class FirebaseAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public FirebaseAuthService(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse authenticate(FirebaseAuthRequest request) {
        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(request.idToken());
            String email = decoded.getEmail();
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Firebase token does not contain email");
            }
            String normalizedEmail = email.trim().toLowerCase();
            String name = resolveName(decoded, request.name(), normalizedEmail);

            User user = userRepository.findByEmail(normalizedEmail)
                    .orElseGet(() -> createFirebaseUser(name, normalizedEmail));

            if (user.getName() == null || user.getName().isBlank()) {
                user.setName(name);
                userRepository.save(user);
            }

            return new AuthResponse(jwtService.generateToken(normalizedEmail), "Bearer");
        } catch (Throwable t) {
            if (t instanceof IllegalArgumentException iae) {
                throw iae;
            }

            String reason = t.getMessage();
            if (reason == null || reason.isBlank()) {
                reason = t.getClass().getSimpleName();
            }
            throw new IllegalArgumentException(
                    "Invalid Firebase token: " + reason +
                    ". Ensure FIREBASE_PROJECT_ID and either FIREBASE_SERVICE_ACCOUNT_JSON or FIREBASE_SERVICE_ACCOUNT_PATH are configured."
            );
        }
    }

    private User createFirebaseUser(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("firebase_" + UUID.randomUUID()));
        return userRepository.save(user);
    }

    private String resolveName(FirebaseToken decoded, String requestName, String email) {
        if (decoded.getName() != null && !decoded.getName().isBlank()) return decoded.getName().trim();
        if (requestName != null && !requestName.isBlank()) return requestName.trim();
        return email.split("@")[0];
    }
}
