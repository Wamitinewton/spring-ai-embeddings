package com.spring.kotlin_ai_chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String answer;
    private String confidence;
    private int contextDocumentCount;
    private long responseTimeMs;
    private boolean successful;
    private String errorMessage;

    public static ChatResponse success(String answer, String confidence, 
    int contextDocumentsCount, long responseTimeMs) {
        ChatResponse response = new ChatResponse();
        response.answer = answer;
        response.confidence = confidence;
        response.contextDocumentCount = contextDocumentsCount;
        response.responseTimeMs = responseTimeMs;
        response.successful = true;
        return response;
    }

    public static ChatResponse error(String errorMessage) {
        ChatResponse response = new ChatResponse();
        response.errorMessage = errorMessage;
        response.successful = false;
        return response;
    }
}
