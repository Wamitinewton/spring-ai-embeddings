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
public class ProgrammingChatbotService {

    private static final Logger logger = LoggerFactory.getLogger(ProgrammingChatbotService.class);

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final int maxContextDocuments;

    private static final String PROGRAMMING_EXPERT_PROMPT = """
            You are a highly knowledgeable programming expert and teacher specializing in multiple programming languages 
            including Kotlin, Java, Python, JavaScript, TypeScript, C#, C++, Rust, Go, Swift, and others.

            Your goal is to provide accurate, comprehensive, and educational answers about programming concepts, 
            best practices, and implementation details across various languages.

            You have access to documentation and reference materials when available, but you should also use your 
            extensive programming knowledge to answer questions even when specific context isn't provided.

            Guidelines:
            1. Start with a brief, clear summary (1-2 sentences)
            2. Use clear section headings (e.g., ## Implementation, ## Key Concepts, ## Language Comparison)
            3. Include practical code examples in proper code blocks with language specified
            4. When discussing concepts, mention how they work in different languages when relevant
            5. Keep explanations concise but comprehensive
            6. Use numbered steps for implementation guides
            7. End with a brief conclusion or key takeaway
            8. Format code properly: ```language for each specific language
            9. Focus on practical, actionable advice
            10. If multiple languages are relevant, show examples in the most appropriate ones

            Structure your response like this:
            - Brief summary paragraph
            - Main content with clear headings
            - Code examples with descriptions (multiple languages when helpful)
            - Cross-language insights when applicable
            - Conclusion/key points

            {context_section}

            User Question: {question}

            Answer:
            """;

    private static final String CONTEXT_TEMPLATE = """
            Reference Documentation:
            {context}
            """;

    public ProgrammingChatbotService(ChatModel chatModel,
            VectorStore vectorStore,
            @Value("${app.chatbot.max-context-documents:5}") int maxContextDocuments) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.maxContextDocuments = maxContextDocuments;
        
        logger.info("ProgrammingChatbotService initialized with max context documents: {}", maxContextDocuments);
    }

    public ChatbotResponse askQuestion(String question) {
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Processing programming question: {}", question);

            if (question == null || question.trim().isEmpty()) {
                return ChatbotResponse.error("Question cannot be empty");
            }

            List<Document> relevantDocs = findRelevantDocuments(question);
            logger.info("Found {} relevant documents", relevantDocs.size());

            logFoundDocumentLanguages(relevantDocs);

            String contextSection = "";
            if (!relevantDocs.isEmpty()) {
                String context = createContextFromDocuments(relevantDocs);
                contextSection = CONTEXT_TEMPLATE.replace("{context}", context);
                logger.debug("Created context with {} characters", context.length());
            } else {
                logger.info("No relevant documents found, proceeding with general programming knowledge");
            }

            String answer = generateAnswer(question, contextSection);
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
                    .similarityThreshold(0.55)
                    .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            logger.debug("Vector search returned {} documents for query: {}", documents.size(), question);
            
            if (documents.size() < 2) {
                logger.debug("Few results found, trying broader search...");
                SearchRequest broaderRequest = SearchRequest
                        .builder()
                        .query(question)
                        .topK(maxContextDocuments + 2)
                        .similarityThreshold(0.4)
                        .build();
                documents = vectorStore.similaritySearch(broaderRequest);
                logger.debug("Broader search returned {} documents", documents.size());
            }
            
            return documents;
        } catch (Exception e) {
            logger.warn("Error searching vector store (continuing without context): {}", e.getMessage());
            return List.of();
        }
    }

    private void logFoundDocumentLanguages(List<Document> documents) {
        if (!documents.isEmpty() && logger.isDebugEnabled()) {
            Map<String, Long> languageCounts = documents.stream()
                    .map(doc -> doc.getMetadata().getOrDefault("primary_language", "unknown").toString())
                    .collect(Collectors.groupingBy(lang -> lang, Collectors.counting()));
            
            logger.debug("Found documents by language: {}", languageCounts);
        }
    }

    private String createContextFromDocuments(List<Document> documents) {
        return documents.stream()
                .map(doc -> {
                    String content = doc.getText();
                    Map<String, Object> metadata = doc.getMetadata();

                    StringBuilder contextEntry = new StringBuilder();
                    
                    if (metadata.containsKey("source")) {
                        contextEntry.append("📄 Source: ").append(metadata.get("source")).append("\n");
                    }
                    if (metadata.containsKey("primary_language")) {
                        contextEntry.append("🔤 Language: ").append(metadata.get("primary_language")).append("\n");
                    }
                    if (metadata.containsKey("document_category")) {
                        contextEntry.append("📂 Category: ").append(metadata.get("document_category")).append("\n");
                    }
                    if (metadata.containsKey("chunk_index")) {
                        contextEntry.append("📑 Section: ").append(metadata.get("chunk_index")).append("\n");
                    }
                    
                    contextEntry.append("📝 Content:\n").append(content).append("\n\n");

                    return contextEntry.toString();
                })
                .collect(Collectors.joining("\n" + "=".repeat(60) + "\n"));
    }

    private String generateAnswer(String question, String contextSection) {
        try {
            PromptTemplate promptTemplate = new PromptTemplate(PROGRAMMING_EXPERT_PROMPT);
            Map<String, Object> promptVariables = Map.of(
                    "context_section", contextSection,
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
        int docCount = documents.size();
        int answerLength = answer != null ? answer.length() : 0;

        if (docCount >= 3 && answerLength > 400) {
            return "HIGH";
        } else if (docCount >= 2 && answerLength > 250) {
            return "HIGH";
        } else if (docCount >= 1 && answerLength > 200) {
            return "MEDIUM";
        } else if (answerLength > 150) {
            return "MEDIUM";
        } else if (answerLength > 75) {
            return "LOW";
        } else {
            return "VERY_LOW";
        }
    }

    /**
     * Get statistics about the knowledge base languages
     */
    public KnowledgeBaseStats getKnowledgeBaseStats() {
        try {

            return new KnowledgeBaseStats(
                List.of("kotlin", "java", "python", "javascript", "typescript", "csharp", "cpp", "rust", "go", "swift"),
                "Multi-language programming assistant with document-enhanced responses"
            );
        } catch (Exception e) {
            logger.error("Error getting knowledge base stats: {}", e.getMessage(), e);
            return new KnowledgeBaseStats(List.of(), "Stats unavailable");
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

    @Getter
    @AllArgsConstructor
    public static class KnowledgeBaseStats {
        private final List<String> supportedLanguages;
        private final String description;
    }
}