package com.spring.kotlin_ai_chatbot.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.spring.kotlin_ai_chatbot.dto.ChatRequest;
import com.spring.kotlin_ai_chatbot.dto.ChatResponse;
import com.spring.kotlin_ai_chatbot.service.KotlinChatbotService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.Data;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/kotlin-chatbot")
@CrossOrigin(origins = "*")
public class KotlinChatbotController {

    private static final Logger logger = LoggerFactory.getLogger(KotlinChatbotController.class);

    private final KotlinChatbotService chatbotService;

    public KotlinChatbotController(KotlinChatbotService chatbotService) {
        this.chatbotService = chatbotService;
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
                    response.getResponseTimeMs()
                );
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

    @GetMapping("/info")
    public ResponseEntity<ChatbotInfo> getChatbotInfo() {
        ChatbotInfo info = new ChatbotInfo(
            "Kotlin Expert Chatbot",
            "1.0.0",
            "A specialized AI chatbot for answering Kotlin programming questions using RAG (Retrieval-Augmented Generation)",
            "Ask me anything about Kotlin programming - syntax, concepts, best practices, and more!"
        );
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
}