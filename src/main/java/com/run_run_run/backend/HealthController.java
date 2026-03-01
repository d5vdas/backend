package com.run_run_run.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/")
    public String home() {
        return "RunRunRun backend is live. Use /health or the Vercel frontend URL.";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}