package com.spring.kotlin_ai_chatbot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.spring.kotlin_ai_chatbot.dto.ChatResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ChatResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        String errorMessage = "Validation failed: " + errors.toString();
        logger.warn("Validation error: {}", errorMessage);
        
        ChatResponse response = ChatResponse.error(errorMessage);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(VectorStoreException.class)
    public ResponseEntity<ChatResponse> handleVectorStoreException(VectorStoreException ex) {
        logger.error("Vector store error: {}", ex.getMessage(), ex);
        
        ChatResponse response = ChatResponse.error("There was an issue with the knowledge base. Please try again later.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(PdfProcessingException.class)
    public ResponseEntity<ChatResponse> handlePdfProcessingException(PdfProcessingException ex) {
        logger.error("PDF processing error: {}", ex.getMessage(), ex);
        
        ChatResponse response = ChatResponse.error("Error processing PDF file: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ChatResponse> handleGenericException(Exception ex) {
        logger.error("Unexpected error occurred", ex);
        
        ChatResponse response = ChatResponse.error("An unexpected error occurred. Please try again later.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

class VectorStoreException extends RuntimeException {
    public VectorStoreException(String message) {
        super(message);
    }
    
    public VectorStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}

class PdfProcessingException extends RuntimeException {
    public PdfProcessingException(String message) {
        super(message);
    }
    
    public PdfProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}