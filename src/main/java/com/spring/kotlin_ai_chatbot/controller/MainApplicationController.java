package com.spring.kotlin_ai_chatbot.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MainApplicationController {

    @GetMapping("/info")
    public ResponseEntity<ApplicationInfo> getApplicationInfo() {
        ApplicationInfo info = new ApplicationInfo(
                "Multi-Language Programming Assistant & Quiz Platform",
                "3.1.0",
                "Comprehensive AI-powered platform for programming assistance, quizzes, and random facts across multiple languages",
                new String[]{
                    "/api/chatbot/ask - Ask programming questions",
                    "/api/chatbot/random-fact - Get random programming facts",
                    "/api/quiz/start - Start a programming quiz",
                    "/api/quiz/answer - Submit quiz answers"
                },
                new String[]{"kotlin", "java", "python", "javascript", "typescript", "csharp", "cpp", "rust", "go", "swift"},
                new FeatureInfo[]{
                    new FeatureInfo("AI Chatbot", "Ask programming questions with document-enhanced responses", "/api/chatbot"),
                    new FeatureInfo("Multi-Language Quiz", "Test your knowledge with AI-generated questions", "/api/quiz"),
                    new FeatureInfo("Random Facts", "Discover interesting programming trivia", "/api/chatbot/random-fact"),
                    new FeatureInfo("Admin Tools", "Document management and system monitoring", "/api/chatbot/admin")
                }
        );
        return ResponseEntity.ok(info);
    }

    @GetMapping("/health")
    public ResponseEntity<HealthStatus> getHealthStatus() {
        HealthStatus status = new HealthStatus(
                "UP",
                "Multi-Language Programming Assistant is running",
                System.currentTimeMillis(),
                new String[]{"OpenAI GPT-4o", "Qdrant Vector Store", "Redis Session Management"}
        );
        return ResponseEntity.ok(status);
    }

    @Data
    @AllArgsConstructor
    public static class ApplicationInfo {
        private final String name;
        private final String version;
        private final String description;
        private final String[] endpoints;
        private final String[] supportedLanguages;
        private final FeatureInfo[] features;
    }

    @Data
    @AllArgsConstructor
    public static class FeatureInfo {
        private final String name;
        private final String description;
        private final String endpoint;
    }

    @Data
    @AllArgsConstructor
    public static class HealthStatus {
        private final String status;
        private final String message;
        private final long timestamp;
        private final String[] services;
    }
}