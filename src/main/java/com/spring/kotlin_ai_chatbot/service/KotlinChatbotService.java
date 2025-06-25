package com.spring.kotlin_ai_chatbot.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Service
public class KotlinChatbotService {

    private static final Logger logger = LoggerFactory.getLogger(KotlinChatbotService.class);

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final int maxContextDocuments;

    private static final String KOTLIN_EXPERT_PROMPT = """
            You are a highly knowledgeable Kotlin expert and teacher. Your goal is to provide accurate,
            comprehensive, and educational answers about Kotlin programming.

            Based on the provided context from official Kotlin documentation, answer the user's question.

            Guidelines:
            1. Provide clear, accurate, and well-structured answers
            2. Include code examples when relevant
            3. Explain concepts in a teaching manner
            4. If the context doesn't contain enough information, say so clearly
            5. Focus specifically on Kotlin-related topics
            6. Structure your response in a clear, educational format

            Context from Kotlin Documentation:
            {context}

            User Question: {question}

            Answer:
            """;

    public KotlinChatbotService(ChatModel chatModel,
            VectorStore vectorStore,
            @Value("${app.chatbot.max-context-documents:5}") int maxContextDocuments) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.maxContextDocuments = maxContextDocuments;
        
        logger.info("KotlinChatbotService initialized with max context documents: {}", maxContextDocuments);
    }

    public ChatbotResponse askQuestion(String question) {
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Processing question: {}", question);

            if (question == null || question.trim().isEmpty()) {
                return ChatbotResponse.error("Question cannot be empty");
            }

            List<Document> relevantDocs = findRelevantDocuments(question);
            logger.info("Found {} relevant documents", relevantDocs.size());

            if (relevantDocs.isEmpty()) {
                return ChatbotResponse.error("I couldn't find relevant information in my Kotlin knowledge base. " +
                        "Please ask questions related to Kotlin programming, or ensure the knowledge base is properly loaded.");
            }

            String context = createContextFromDocuments(relevantDocs);
            logger.debug("Created context with {} characters", context.length());

            String answer = generateAnswer(question, context);

            String confidence = calculateConfidence(relevantDocs, answer);

            long responseTime = System.currentTimeMillis() - startTime;

            logger.info("Generated response in {}ms with {} context documents, confidence: {}", 
                       responseTime, relevantDocs.size(), confidence);

            return ChatbotResponse.success(answer, confidence, relevantDocs.size(), responseTime);

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            logger.error("Error processing question after {}ms: {}", responseTime, e.getMessage(), e);
            return ChatbotResponse.error("I encountered an error while processing your question. Please try again.");
        }
    }

    private List<Document> findRelevantDocuments(String question) {
        try {
            SearchRequest searchRequest = SearchRequest
                    .builder()
                    .query(question)
                    .topK(maxContextDocuments)
                    .similarityThreshold(0.7)
                    .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            logger.debug("Vector search returned {} documents for query: {}", documents.size(), question);
            
            return documents;
        } catch (Exception e) {
            logger.error("Error searching vector store: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search knowledge base", e);
        }
    }

    private String createContextFromDocuments(List<Document> documents) {
        return documents.stream()
                .map(doc -> {
                    String content = doc.getText();
                    Map<String, Object> metadata = doc.getMetadata();

                    StringBuilder contextEntry = new StringBuilder();
                    if (metadata.containsKey("source")) {
                        contextEntry.append("Source: ").append(metadata.get("source")).append("\n");
                    }
                    if (metadata.containsKey("chunk_index")) {
                        contextEntry.append("Section: ").append(metadata.get("chunk_index")).append("\n");
                    }
                    contextEntry.append(content).append("\n\n");

                    return contextEntry.toString();
                })
                .collect(Collectors.joining("\n" + "=".repeat(50) + "\n"));
    }

    private String generateAnswer(String question, String context) {
        try {
            PromptTemplate promptTemplate = new PromptTemplate(KOTLIN_EXPERT_PROMPT);
            Map<String, Object> promptVariables = Map.of(
                    "context", context,
                    "question", question);

            Prompt prompt = promptTemplate.create(promptVariables);
            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);

            String answer = response.getResult().getOutput().getText();
            logger.debug("Generated answer with {} characters", answer.length());
            
            return answer;
        } catch (Exception e) {
            logger.error("Error generating answer with chat model: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate response", e);
        }
    }

    private String calculateConfidence(List<Document> documents, String answer) {
        if (documents.isEmpty()) {
            return "LOW";
        }

        int docCount = documents.size();
        int answerLength = answer != null ? answer.length() : 0;

        if (docCount >= 4 && answerLength > 300) {
            return "HIGH";
        } else if (docCount >= 2 && answerLength > 150) {
            return "MEDIUM";
        } else if (docCount >= 1 && answerLength > 50) {
            return "LOW";
        } else {
            return "VERY_LOW";
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ChatbotResponse {
        private final boolean successful;
        private final String answer;
        private final String confidence;
        private final int contextDocumentsCount;
        private final long responseTimeMs;
        private final String errorMessage;

        public static ChatbotResponse success(String answer, String confidence,
                int contextDocumentsCount, long responseTimeMs) {
            return new ChatbotResponse(true, answer, confidence, contextDocumentsCount, responseTimeMs, null);
        }

        public static ChatbotResponse error(String errorMessage) {
            return new ChatbotResponse(false, null, null, 0, 0, errorMessage);
        }
    }
}