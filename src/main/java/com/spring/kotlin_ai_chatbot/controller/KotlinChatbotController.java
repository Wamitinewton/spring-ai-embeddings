package com.spring.kotlin_ai_chatbot.controller;

import com.spring.kotlin_ai_chatbot.data.QuizSession;
import com.spring.kotlin_ai_chatbot.dto.*;
import com.spring.kotlin_ai_chatbot.service.KotlinChatbotService;
import com.spring.kotlin_ai_chatbot.service.KotlinQuizService;
import com.spring.kotlin_ai_chatbot.service.QuizSessionService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/kotlin-chatbot")
@CrossOrigin(origins = "*")
public class KotlinChatbotController {

    private static final Logger logger = LoggerFactory.getLogger(KotlinChatbotController.class);

    private final KotlinChatbotService chatbotService;
    private final KotlinQuizService quizService;
    private final QuizSessionService sessionService;

    public KotlinChatbotController(KotlinChatbotService chatbotService,
            KotlinQuizService quizService,
            QuizSessionService sessionService) {
        this.chatbotService = chatbotService;
        this.quizService = quizService;
        this.sessionService = sessionService;
    }

    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> askQuestion(@Valid @RequestBody ChatRequest request) {
        logger.info("Received question: {}", request.getQuestion());

        try {
            KotlinChatbotService.ChatbotResponse response = chatbotService.askQuestion(request.getQuestion());

            if (response.isSuccessful()) {
                ChatResponse chatResponse = ChatResponse.success(
                        response.getAnswer(),
                        response.getConfidence(),
                        response.getContextDocumentsCount(),
                        response.getResponseTimeMs());
                return ResponseEntity.ok(chatResponse);
            } else {
                ChatResponse errorResponse = ChatResponse.error(response.getErrorMessage());
                return ResponseEntity.badRequest().body(errorResponse);
            }

        } catch (Exception e) {
            logger.error("Unexpected error processing question", e);
            ChatResponse errorResponse = ChatResponse.error("An unexpected error occurred. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/ask")
    public ResponseEntity<ChatResponse> askQuestionGet(@RequestParam String question) {
        logger.info("Received GET question: {}", question);

        if (question == null || question.trim().isEmpty()) {
            ChatResponse errorResponse = ChatResponse.error("Question cannot be empty");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        if (question.length() > 1000) {
            ChatResponse errorResponse = ChatResponse.error("Question must be less than 1000 characters");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        ChatRequest request = new ChatRequest(question.trim());
        return askQuestion(request);
    }

    @PostMapping("/quiz/start")
    public ResponseEntity<QuizSessionResponse> startQuizSession(@Valid @RequestBody QuizSessionRequest request) {
        logger.info("Starting quiz session with difficulty: {}", request.getDifficulty());

        try {
            QuizSessionResponse response = quizService.startQuizSession(request.getDifficulty());

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.forUser());
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            logger.error("Error starting quiz session", e);
            QuizSessionResponse errorResponse = QuizSessionResponse
                    .error("Failed to start quiz session. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/quiz/start")
    public ResponseEntity<QuizSessionResponse> startQuizSessionGet(
            @RequestParam(defaultValue = "beginner") String difficulty) {
        logger.info("Received GET request for quiz session with difficulty: {}", difficulty);

        if (!difficulty.matches("^(beginner|intermediate|advanced)$")) {
            QuizSessionResponse errorResponse = QuizSessionResponse
                    .error("Difficulty must be: beginner, intermediate, or advanced");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        QuizSessionRequest request = new QuizSessionRequest(difficulty);
        return startQuizSession(request);
    }

    @PostMapping("/quiz/answer")
    public ResponseEntity<QuizAnswerResponse> submitQuizAnswer(@Valid @RequestBody QuizAnswerRequest request) {
        logger.info("Submitting answer for session: {} with answer: {}", request.getSessionId(), request.getAnswer());

        try {
            quizService.keepSessionAlive(request.getSessionId());

            QuizAnswerResponse response = quizService.submitAnswer(request.getSessionId(), request.getAnswer());

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.forUser());
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            logger.error("Error submitting quiz answer", e);
            QuizAnswerResponse errorResponse = QuizAnswerResponse.error("Failed to process answer. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/quiz/session/{sessionId}")
    public ResponseEntity<QuizSessionStatus> getSessionStatus(@PathVariable String sessionId) {
        logger.info("Getting status for session: {}", sessionId);

        try {
            Optional<QuizSession> sessionOpt = quizService.getSessionStatus(sessionId);

            if (sessionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            QuizSession session = sessionOpt.get();
            QuizSessionStatus status = new QuizSessionStatus(
                    session.getSessionId(),
                    session.getDifficulty(),
                    session.getCurrentQuestionNumber(),
                    QuizSession.TOTAL_QUESTIONS,
                    session.getScore(),
                    session.getCompletionPercentage(),
                    session.getStatus(),
                    session.getSessionDurationMinutes(),
                    session.isCompleted());

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            logger.error("Error getting session status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/quiz/session/{sessionId}/extend")
    public ResponseEntity<Void> extendSession(@PathVariable String sessionId) {
        logger.info("Extending session: {}", sessionId);

        try {
            quizService.keepSessionAlive(sessionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error extending session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/quiz/stats")
    public ResponseEntity<QuizStats> getQuizStats() {
        try {
            QuizSessionService.SessionStats stats = sessionService.getSessionStats();
            QuizStats quizStats = new QuizStats(
                    stats.total(),
                    stats.active(),
                    stats.completed());
            return ResponseEntity.ok(quizStats);
        } catch (Exception e) {
            logger.error("Error getting quiz stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/info")
    public ResponseEntity<ChatbotInfo> getChatbotInfo() {
        ChatbotInfo info = new ChatbotInfo(
                "Kotlin Expert Chatbot & Quiz Game",
                "2.1.0",
                "AI chatbot for Kotlin programming questions with Redis-powered interactive quiz sessions",
                "Ask me anything about Kotlin programming or play the quiz game to test your knowledge!");
        return ResponseEntity.ok(info);
    }

    @GetMapping("/quiz/info")
    public ResponseEntity<QuizGameInfo> getQuizInfo() {
        QuizGameInfo info = new QuizGameInfo(
                "Kotlin Quiz Game - 5 Questions Challenge",
                "Test your Kotlin knowledge with AI-generated questions. Sessions are managed with Redis for reliability.",
                new String[] { "beginner", "intermediate", "advanced" },
                "Answer 5 multiple-choice questions to complete a quiz session. Get immediate feedback and explanations!");
        return ResponseEntity.ok(info);
    }

    @Data
    @AllArgsConstructor
    public static class ChatbotInfo {
        private final String name;
        private final String version;
        private final String description;
        private final String usage;
    }

    @Data
    @AllArgsConstructor
    public static class QuizGameInfo {
        private final String name;
        private final String description;
        private final String[] difficulties;
        private final String instructions;
    }

    @Data
    @AllArgsConstructor
    public static class QuizSessionStatus {
        private final String sessionId;
        private final String difficulty;
        private final int currentQuestion;
        private final int totalQuestions;
        private final int score;
        private final int completionPercentage;
        private final String status;
        private final long sessionDurationMinutes;
        private final boolean completed;
    }

    @Data
    @AllArgsConstructor
    public static class QuizStats {
        private final int totalSessions;
        private final int activeSessions;
        private final int completedSessions;
    }
}