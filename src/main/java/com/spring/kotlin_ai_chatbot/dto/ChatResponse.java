package com.spring.kotlin_ai_chatbot.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String summary;
    private String fullAnswer;
    private List<ContentSection> sections;
    private List<CodeExample> codeExamples;
    private String confidence;
    private int contextDocumentCount;
    private long responseTimeMs;
    private boolean successful;
    private String errorMessage;

    public static ChatResponse success(String rawAnswer, String confidence, 
                                     int contextDocumentsCount, long responseTimeMs) {
        ChatResponse response = new ChatResponse();
        
        ParsedContent parsed = parseAnswer(rawAnswer);
        
        response.summary = parsed.summary;
        response.fullAnswer = rawAnswer;
        response.sections = parsed.sections;
        response.codeExamples = parsed.codeExamples;
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

    private static ParsedContent parseAnswer(String rawAnswer) {
        AnswerParser parser = new AnswerParser();
        return parser.parse(rawAnswer);
    }

    @Data
    @AllArgsConstructor
    public static class ContentSection {
        private String title;
        private String content;
        private String cleanContent;
        private String type; 
        private boolean hasCode;
    }

    @Data
    @AllArgsConstructor
    public static class CodeExample {
        private String language;
        private String code;
        private String description;
        private String filename;
    }

    @Data
    @AllArgsConstructor
    public static class ParsedContent {
        private String summary;
        private List<ContentSection> sections;
        private List<CodeExample> codeExamples;
    }
}