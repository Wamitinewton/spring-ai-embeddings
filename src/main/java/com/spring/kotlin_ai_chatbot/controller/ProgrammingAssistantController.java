package com.spring.kotlin_ai_chatbot.controller;

import com.spring.kotlin_ai_chatbot.data.QuizSession;
import com.spring.kotlin_ai_chatbot.dto.*;
import com.spring.kotlin_ai_chatbot.service.ProgrammingChatbotService;
import com.spring.kotlin_ai_chatbot.service.KotlinQuizService;
import com.spring.kotlin_ai_chatbot.service.QuizSessionService;
import com.spring.kotlin_ai_chatbot.service.InitializationService;
import com.spring.kotlin_ai_chatbot.service.PdfProcessingService;
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
@RequestMapping("/api/programming-assistant")
@CrossOrigin(origins = "*")
public class ProgrammingAssistantController {

    private static final Logger logger = LoggerFactory.getLogger(ProgrammingAssistantController.class);

    private final ProgrammingChatbotService chatbotService;
    private final KotlinQuizService quizService;
    private final QuizSessionService sessionService;
    private final InitializationService initializationService;

    public ProgrammingAssistantController(ProgrammingChatbotService chatbotService,
            KotlinQuizService quizService,
            QuizSessionService sessionService,
            InitializationService initializationService,
            PdfProcessingService pdfProcessingService) {
        this.chatbotService = chatbotService;
        this.quizService = quizService;
        this.sessionService = sessionService;
        this.initializationService = initializationService;
    }


    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> askQuestion(@Valid @RequestBody ChatRequest request) {
        logger.info("Received programming question: {}", request.getQuestion());

        try {
            ProgrammingChatbotService.ChatbotResponse response = chatbotService.askQuestion(request.getQuestion());

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
        logger.info("Received GET programming question: {}", question);

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


    @PostMapping("/admin/reload-documents")
    public ResponseEntity<DocumentReloadResponse> reloadDocuments() {
        logger.info("Admin request to reload all documents");

        try {
            PdfProcessingService.BulkProcessingResult result = initializationService.reloadAllDocuments();
            
            DocumentReloadResponse response = new DocumentReloadResponse(
                result.isCompletelySuccessful(),
                result.getSuccessfulCount(),
                result.getFailedCount(),
                result.getTotalChunks(),
                result.getTotalProcessingTimeMs(),
                result.getSuccessfulDocuments().keySet().toArray(new String[0]),
                result.getFailedDocuments()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            logger.warn("Cannot reload documents: {}", e.getMessage());
            DocumentReloadResponse errorResponse = new DocumentReloadResponse(
                false, 0, 0, 0, 0, new String[0], 
                java.util.Map.of("error", e.getMessage())
            );
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("Error reloading documents", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/admin/initialization-status")
    public ResponseEntity<InitializationService.InitializationStatus> getInitializationStatus() {
        return ResponseEntity.ok(initializationService.getStatus());
    }

    @GetMapping("/admin/knowledge-base-stats")
    public ResponseEntity<ProgrammingChatbotService.KnowledgeBaseStats> getKnowledgeBaseStats() {
        try {
            ProgrammingChatbotService.KnowledgeBaseStats stats = chatbotService.getKnowledgeBaseStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error getting knowledge base stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("/info")
    public ResponseEntity<AssistantInfo> getAssistantInfo() {
        AssistantInfo info = new AssistantInfo(
                "Multi-Language Programming Assistant",
                "3.0.0",
                "AI-powered assistant for programming questions across multiple languages with document-enhanced responses and interactive quiz sessions",
                "Ask me about programming concepts, best practices, or specific implementation details in any supported language!",
                new String[]{"kotlin", "java", "python", "javascript", "typescript", "csharp", "cpp", "rust", "go", "swift", "general"}
        );
        return ResponseEntity.ok(info);
    }

    @GetMapping("/quiz/info")
    public ResponseEntity<QuizGameInfo> getQuizInfo() {
        QuizGameInfo info = new QuizGameInfo(
                "Programming Quiz Game - 5 Questions Challenge",
                "Test your programming knowledge with AI-generated questions. Sessions are managed with Redis for reliability.",
                new String[] { "beginner", "intermediate", "advanced" },
                "Answer 5 multiple-choice questions to complete a quiz session. Get immediate feedback and explanations!"
        );
        return ResponseEntity.ok(info);
    }

    @GetMapping("/supported-languages")
    public ResponseEntity<SupportedLanguages> getSupportedLanguages() {
        SupportedLanguages languages = new SupportedLanguages(
                new String[]{"kotlin", "java", "python", "javascript", "typescript", "csharp", "cpp", "rust", "go", "swift"},
                "The assistant can help with programming questions in these languages and more"
        );
        return ResponseEntity.ok(languages);
    }


    @Data
    @AllArgsConstructor
    public static class AssistantInfo {
        private final String name;
        private final String version;
        private final String description;
        private final String usage;
        private final String[] supportedLanguages;
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

    @Data
    @AllArgsConstructor
    public static class DocumentReloadResponse {
        private final boolean successful;
        private final int successfulDocuments;
        private final int failedDocuments;
        private final int totalChunks;
        private final long processingTimeMs;
        private final String[] processedFiles;
        private final java.util.Map<String, String> errors;
    }

    @Data
    @AllArgsConstructor
    public static class SupportedLanguages {
        private final String[] languages;
        private final String description;
    }
}