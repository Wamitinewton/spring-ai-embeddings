package com.spring.kotlin_ai_chatbot.controller;

import com.spring.kotlin_ai_chatbot.dto.*;
import com.spring.kotlin_ai_chatbot.service.ProgrammingChatbotService;
import com.spring.kotlin_ai_chatbot.service.RandomFactsService;
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

@RestController
@RequestMapping("/api/chatbot")
@CrossOrigin(origins = "*")
public class ChatbotController {

    private static final Logger logger = LoggerFactory.getLogger(ChatbotController.class);

    private final ProgrammingChatbotService chatbotService;
    private final RandomFactsService randomFactsService;
    private final InitializationService initializationService;

    public ChatbotController(ProgrammingChatbotService chatbotService,
                           RandomFactsService randomFactsService,
                           InitializationService initializationService,
                           PdfProcessingService pdfProcessingService) {
        this.chatbotService = chatbotService;
        this.randomFactsService = randomFactsService;
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

    @GetMapping("/random-fact")
    public ResponseEntity<RandomFactResponse> getRandomFact(
            @RequestParam(required = false) String language) {
        
        logger.info("Generating random fact for language: {}", language != null ? language : "random");

        try {
            RandomFactsService.FactResult result = randomFactsService.generateRandomFact(language);

            if (result.isSuccessful()) {
                RandomFactResponse response = new RandomFactResponse(
                    result.getFact(),
                    result.getLanguage(),
                    result.getCategory(),
                    result.getSource(),
                    result.getResponseTimeMs(),
                    true,
                    null
                );
                return ResponseEntity.ok(response);
            } else {
                RandomFactResponse errorResponse = new RandomFactResponse(
                    null, null, null, null, 0, false, result.getErrorMessage()
                );
                return ResponseEntity.badRequest().body(errorResponse);
            }

        } catch (Exception e) {
            logger.error("Error generating random fact", e);
            RandomFactResponse errorResponse = new RandomFactResponse(
                null, null, null, null, 0, false, 
                "Failed to generate random fact. Please try again."
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
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
                "3.1.0",
                "AI-powered assistant for programming questions across multiple languages with document-enhanced responses",
                "Ask me about programming concepts, best practices, or specific implementation details in any supported language!",
                new String[]{"kotlin", "java", "python", "javascript", "typescript", "csharp", "cpp", "rust", "go", "swift", "general"}
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

    // Data classes
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

    @Data
    @AllArgsConstructor
    public static class RandomFactResponse {
        private final String fact;
        private final String language;
        private final String category;
        private final String source;
        private final long responseTimeMs;
        private final boolean successful;
        private final String errorMessage;
    }
}