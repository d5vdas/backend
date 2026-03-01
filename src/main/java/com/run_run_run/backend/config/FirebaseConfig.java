package com.run_run_run.backend.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

@Configuration
public class FirebaseConfig {

    public FirebaseConfig(@Value("${firebase.project-id:}") String projectId,
                          @Value("${firebase.service-account-json:}") String serviceAccountJson,
                          @Value("${firebase.service-account-path:}") String serviceAccountPath) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        boolean hasJson = serviceAccountJson != null && !serviceAccountJson.isBlank();
        boolean hasPath = serviceAccountPath != null && !serviceAccountPath.isBlank();

        if (!hasJson && !hasPath) {
            // Keep app booting even when Firebase Admin credentials are not set.
            // /auth/firebase will return a clear validation error until configured.
            return;
        }

        FirebaseOptions.Builder builder = FirebaseOptions.builder();
        if (projectId != null && !projectId.isBlank()) {
            builder.setProjectId(projectId);
        }

        try (InputStream credentialsStream = hasPath
                ? Files.newInputStream(Path.of(serviceAccountPath))
                : new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8))) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
            builder.setCredentials(credentials);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid Firebase Admin credentials. Configure FIREBASE_SERVICE_ACCOUNT_JSON or FIREBASE_SERVICE_ACCOUNT_PATH",
                    e
            );
        }

        FirebaseApp.initializeApp(builder.build());
    }
}
