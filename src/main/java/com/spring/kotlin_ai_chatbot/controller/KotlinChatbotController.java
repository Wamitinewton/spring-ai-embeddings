package com.spring.kotlin_ai_chatbot.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.spring.kotlin_ai_chatbot.dto.*;
import com.spring.kotlin_ai_chatbot.service.KotlinChatbotService;
import com.spring.kotlin_ai_chatbot.service.KotlinQuizService;

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
    private final KotlinQuizService quizService;

    public KotlinChatbotController(KotlinChatbotService chatbotService, KotlinQuizService quizService) {
        this.chatbotService = chatbotService;
        this.quizService = quizService;
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
            QuizSessionResponse errorResponse = QuizSessionResponse.error("Failed to start quiz session. Please try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/quiz/start")
    public ResponseEntity<QuizSessionResponse> startQuizSessionGet(@RequestParam(defaultValue = "beginner") String difficulty) {
        logger.info("Received GET request for quiz session with difficulty: {}", difficulty);
        
        if (!difficulty.matches("^(beginner|intermediate|advanced)$")) {
            QuizSessionResponse errorResponse = QuizSessionResponse.error("Difficulty must be: beginner, intermediate, or advanced");
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        QuizSessionRequest request = new QuizSessionRequest(difficulty);
        return startQuizSession(request);
    }

    @PostMapping("/quiz/answer")
    public ResponseEntity<QuizAnswerResponse> submitQuizAnswer(@Valid @RequestBody QuizAnswerRequest request) {
        logger.info("Submitting answer for session: {} with answer: {}", request.getSessionId(), request.getAnswer());
        
        try {
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


    @GetMapping("/info")
    public ResponseEntity<ChatbotInfo> getChatbotInfo() {
        ChatbotInfo info = new ChatbotInfo(
            "Kotlin Expert Chatbot & Quiz Game",
            "2.0.0",
            "A specialized AI chatbot for answering Kotlin programming questions and playing interactive quiz games using RAG",
            "Ask me anything about Kotlin programming or play the quiz game to test your knowledge!"
        );
        return ResponseEntity.ok(info);
    }

    @GetMapping("/quiz/info")
    public ResponseEntity<QuizGameInfo> getQuizInfo() {
        QuizGameInfo info = new QuizGameInfo(
            "Kotlin Quiz Game - 5 Questions Challenge",
            "Test your Kotlin knowledge with AI-generated questions including code snippets. Complete 5 questions to get your score!",
            new String[]{"beginner", "intermediate", "advanced"},
            "Answer 5 multiple-choice questions to complete a quiz session. Get immediate feedback and explanations!"
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

    @Data
    @AllArgsConstructor
    public static class QuizGameInfo {
        private final String name;
        private final String description;
        private final String[] difficulties;
        private final String instructions;
    }
}