package com.spring.kotlin_ai_chatbot.controller;

import com.spring.kotlin_ai_chatbot.data.QuizSession;
import com.spring.kotlin_ai_chatbot.dto.*;
import com.spring.kotlin_ai_chatbot.service.MultiLanguageQuizService;
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
@RequestMapping("/api/quiz")
@CrossOrigin(origins = "*")
public class QuizController {

    private static final Logger logger = LoggerFactory.getLogger(QuizController.class);

    private final MultiLanguageQuizService quizService;
    private final QuizSessionService sessionService;

    public QuizController(MultiLanguageQuizService quizService, QuizSessionService sessionService) {
        this.quizService = quizService;
        this.sessionService = sessionService;
    }

    @PostMapping("/start")
    public ResponseEntity<QuizSessionResponse> startQuizSession(@Valid @RequestBody QuizSessionRequest request) {
        logger.info("Starting quiz session - Language: {}, Difficulty: {}", 
                   request.getLanguage(), request.getDifficulty());

        try {
            QuizSessionResponse response = quizService.startQuizSession(
                request.getLanguage(), request.getDifficulty());

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

    @GetMapping("/start")
    public ResponseEntity<QuizSessionResponse> startQuizSessionGet(
            @RequestParam(defaultValue = "python") String language,
            @RequestParam(defaultValue = "beginner") String difficulty) {
        
        logger.info("GET request for quiz session - Language: {}, Difficulty: {}", language, difficulty);

        if (!isValidLanguage(language)) {
            QuizSessionResponse errorResponse = QuizSessionResponse
                    .error("Unsupported language. Supported: kotlin, java, python, javascript, typescript, csharp, cpp, rust, go, swift");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        if (!difficulty.matches("^(beginner|intermediate|advanced)$")) {
            QuizSessionResponse errorResponse = QuizSessionResponse
                    .error("Difficulty must be: beginner, intermediate, or advanced");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        QuizSessionRequest request = new QuizSessionRequest(language, difficulty);
        return startQuizSession(request);
    }

    @PostMapping("/answer")
    public ResponseEntity<QuizAnswerResponse> submitQuizAnswer(@Valid @RequestBody QuizAnswerRequest request) {
        logger.info("Submitting answer for session: {} with answer: {}", 
                   request.getSessionId(), request.getAnswer());

        try {
            quizService.keepSessionAlive(request.getSessionId());

            QuizAnswerResponse response = quizService.submitAnswer(
                request.getSessionId(), request.getAnswer());

            if (response.isSuccessful()) {
                return ResponseEntity.ok(response.forUser());
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            logger.error("Error submitting quiz answer", e);
            QuizAnswerResponse errorResponse = QuizAnswerResponse
                    .error("Failed to process answer. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/session/{sessionId}")
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
                    session.getLanguage(),
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

    @PostMapping("/session/{sessionId}/extend")
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

    @GetMapping("/stats")
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
    public ResponseEntity<QuizGameInfo> getQuizInfo() {
        QuizGameInfo info = new QuizGameInfo(
                "Multi-Language Programming Quiz - 5 Questions Challenge",
                "Test your programming knowledge across multiple languages with AI-generated questions. Sessions are managed with Redis for reliability.",
                new String[] { "beginner", "intermediate", "advanced" },
                new String[] { "kotlin", "java", "python", "javascript", "typescript", "csharp", "cpp", "rust", "go", "swift" },
                "python",
                "Answer 5 multiple-choice questions to complete a quiz session. Get immediate feedback and explanations!"
        );
        return ResponseEntity.ok(info);
    }

    @GetMapping("/supported-languages")
    public ResponseEntity<SupportedLanguages> getSupportedLanguages() {
        SupportedLanguages languages = new SupportedLanguages(
                new String[]{"kotlin", "java", "python", "javascript", "typescript", "csharp", "cpp", "rust", "go", "swift"},
                "python",
                "The quiz supports programming questions in these languages"
        );
        return ResponseEntity.ok(languages);
    }

    private boolean isValidLanguage(String language) {
        return language != null && language.matches("^(kotlin|java|python|javascript|typescript|csharp|cpp|rust|go|swift)$");
    }

    @Data
    @AllArgsConstructor
    public static class QuizGameInfo {
        private final String name;
        private final String description;
        private final String[] difficulties;
        private final String[] supportedLanguages;
        private final String defaultLanguage;
        private final String instructions;
    }

    @Data
    @AllArgsConstructor
    public static class QuizSessionStatus {
        private final String sessionId;
        private final String language;
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

    @Data
    @AllArgsConstructor
    public static class SupportedLanguages {
        private final String[] languages;
        private final String defaultLanguage;
        private final String description;
    }
}
