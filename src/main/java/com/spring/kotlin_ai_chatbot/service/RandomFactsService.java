package com.spring.kotlin_ai_chatbot.service;

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

import java.util.*;

@Service
public class RandomFactsService {

    private static final Logger logger = LoggerFactory.getLogger(RandomFactsService.class);

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final Random random;
    private final int maxContextDocuments;

    private static final List<String> SUPPORTED_LANGUAGES = List.of(
        "kotlin", "java", "python", "javascript", "typescript", 
        "csharp", "cpp", "rust", "go", "swift"
    );

    private static final Map<String, List<String>> FACT_CATEGORIES = Map.of(
        "history", List.of("language origins", "creator stories", "evolution", "milestones"),
        "technical", List.of("performance", "memory management", "compilation", "runtime"),
        "features", List.of("unique syntax", "paradigms", "libraries", "frameworks"),
        "trivia", List.of("naming", "easter eggs", "community", "adoption"),
        "comparison", List.of("vs other languages", "pros and cons", "use cases")
    );

    private static final String FACT_PROMPT = """
            Generate an interesting and lesser-known fact about {language} programming language.
            
            Category focus: {category}
            
            {context}
            
            Requirements:
            1. The fact should be genuinely interesting and not commonly known
            2. It should be accurate and verifiable
            3. Keep it concise but informative (2-3 sentences max)
            4. Make it engaging and educational
            5. Focus on {categoryDescription}
            
            Examples of good facts:
            - Historical anecdotes about the language's creation
            - Surprising technical details or optimizations
            - Unique features that set it apart from other languages
            - Interesting naming origins or design decisions
            - Performance characteristics that might surprise developers
            
            Respond with just the fact, no additional formatting or explanation.
            Start directly with the interesting information.
            """;

    public RandomFactsService(ChatModel chatModel,
                            VectorStore vectorStore,
                            @Value("${app.chatbot.max-context-documents:3}") int maxContextDocuments) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.random = new Random();
        this.maxContextDocuments = maxContextDocuments;
        
        logger.info("RandomFactsService initialized for {} languages", SUPPORTED_LANGUAGES.size());
    }

    /**
     * Generates a random fact about a programming language
     * @param language The programming language (null for random)
     * @return FactResult containing the generated fact
     */
    public FactResult generateRandomFact(String language) {
        long startTime = System.currentTimeMillis();

        try {
            // Select language (random if not specified or invalid)
            String selectedLanguage = selectLanguage(language);
            
            // Select random category
            String category = selectRandomCategory();
            
            logger.info("Generating random fact for language: {}, category: {}", selectedLanguage, category);

            // Get context from embeddings
            String context = getLanguageContext(selectedLanguage, category);
            
            // Generate the fact
            String fact = generateFact(selectedLanguage, category, context);
            
            // Determine source
            String source = context.isEmpty() ? "AI Knowledge Base" : "Documentation + AI Analysis";
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            logger.info("Generated random fact for {} in {}ms", selectedLanguage, responseTime);
            
            return FactResult.success(fact, selectedLanguage, category, source, responseTime);

        } catch (Exception e) {
            logger.error("Error generating random fact: {}", e.getMessage(), e);
            return FactResult.error("Failed to generate random fact. Please try again.");
        }
    }

    private String selectLanguage(String requestedLanguage) {
        if (requestedLanguage != null && SUPPORTED_LANGUAGES.contains(requestedLanguage.toLowerCase())) {
            return requestedLanguage.toLowerCase();
        }
        
        return SUPPORTED_LANGUAGES.get(random.nextInt(SUPPORTED_LANGUAGES.size()));
    }

    private String selectRandomCategory() {
        List<String> categories = new ArrayList<>(FACT_CATEGORIES.keySet());
        return categories.get(random.nextInt(categories.size()));
    }

    private String getLanguageContext(String language, String category) {
        try {
            List<String> categoryTerms = FACT_CATEGORIES.get(category);
            String categoryTerm = categoryTerms.get(random.nextInt(categoryTerms.size()));
            
            String searchQuery = String.format("%s programming language %s", 
                                             getLanguageDisplayName(language), categoryTerm);
            
            SearchRequest request = SearchRequest.builder()
                    .query(searchQuery)
                    .topK(maxContextDocuments)
                    .similarityThreshold(0.4) 
                    .build();

            List<Document> docs = vectorStore.similaritySearch(request);
            
            if (docs.isEmpty()) {
                logger.debug("No context found for {} - {}, proceeding with general knowledge", 
                           language, category);
                return "";
            }

            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("Reference information:\n");
            
            for (Document doc : docs) {
                String content = doc.getText();
                String chunk = content.length() > 500 ? content.substring(0, 500) + "..." : content;
                contextBuilder.append("- ").append(chunk).append("\n");
            }
            
            logger.debug("Found {} documents for context", docs.size());
            return contextBuilder.toString();

        } catch (Exception e) {
            logger.warn("Error getting context for {} - {}: {}", language, category, e.getMessage());
            return "";
        }
    }

    private String generateFact(String language, String category, String context) {
        try {
            String categoryDescription = getCategoryDescription(category);
            String languageDisplay = getLanguageDisplayName(language);

            PromptTemplate template = new PromptTemplate(FACT_PROMPT);
            Map<String, Object> variables = Map.of(
                    "language", languageDisplay,
                    "category", category,
                    "categoryDescription", categoryDescription,
                    "context", context
            );

            Prompt prompt = template.create(variables);
            String response = chatModel.call(prompt).getResult().getOutput().getText().trim();

            String fact = cleanupFact(response);
            
            logger.debug("Generated fact for {} ({}): {}", languageDisplay, category, 
                        fact.length() > 100 ? fact.substring(0, 100) + "..." : fact);
            
            return fact;

        } catch (Exception e) {
            logger.error("Error generating fact with AI: {}", e.getMessage(), e);
            return getFallbackFact(language, category);
        }
    }

    private String cleanupFact(String fact) {
        return fact
                .replaceAll("^(Here's an interesting fact:|Did you know that|Interesting fact:)", "")
                .replaceAll("^[\\s:\\-]+", "")
                .trim();
    }

    private String getCategoryDescription(String category) {
        return switch (category) {
            case "history" -> "historical background, origins, evolution, and key milestones";
            case "technical" -> "technical details, performance characteristics, and implementation aspects";
            case "features" -> "unique language features, syntax, and capabilities";
            case "trivia" -> "interesting trivia, naming origins, and community aspects";
            case "comparison" -> "comparisons with other languages and distinctive characteristics";
            default -> "general interesting aspects";
        };
    }

    private String getFallbackFact(String language, String category) {
        String languageDisplay = getLanguageDisplayName(language);
        
        Map<String, String> fallbackFacts = Map.of(
            "python", "Python was named after the British comedy group Monty Python, not the snake. Guido van Rossum was reading Monty Python scripts while implementing Python in 1989.",
            "java", "Java was originally called 'Oak' after an oak tree outside James Gosling's office, but had to be renamed due to trademark issues. The name 'Java' came from Java coffee.",
            "kotlin", "Kotlin is named after Kotlin Island near St. Petersburg, Russia, following Java's tradition of being named after an island (Java Island in Indonesia).",
            "javascript", "JavaScript was created in just 10 days by Brendan Eich at Netscape in 1995. Despite its name, it has no direct relation to Java.",
            "rust", "Rust is named after rust fungi, which are highly parallel organisms. The language emphasizes safe concurrency and parallelism.",
            "go", "Go was created at Google to address the company's specific software development challenges, particularly slow build times and complexity in large codebases.",
            "swift", "Swift was designed to be 'Objective-C without the C' and can run Objective-C, C, and C++ code within the same program.",
            "typescript", "TypeScript was created by Anders Hejlsberg, who also created Turbo Pascal, Delphi, and C#. It adds static typing to JavaScript while maintaining full compatibility.",
            "csharp", "C# was designed by Anders Hejlsberg as Microsoft's response to Java. The '#' symbol represents four '+' symbols arranged in a 2x2 grid, suggesting C++++.",
            "cpp", "C++ was originally called 'C with Classes.' The '++' operator in C means 'increment,' so C++ literally means 'an incremented version of C.'"
        );
        
        return fallbackFacts.getOrDefault(language, 
            String.format("%s is a powerful programming language with many interesting features and use cases.", languageDisplay));
    }

    private String getLanguageDisplayName(String language) {
        if (language == null) return "Programming";
        
        return switch (language.toLowerCase()) {
            case "kotlin" -> "Kotlin";
            case "java" -> "Java";
            case "python" -> "Python";
            case "javascript" -> "JavaScript";
            case "typescript" -> "TypeScript";
            case "csharp" -> "C#";
            case "cpp" -> "C++";
            case "rust" -> "Rust";
            case "go" -> "Go";
            case "swift" -> "Swift";
            default -> "Programming";
        };
    }

    @Getter
    @AllArgsConstructor
    public static class FactResult {
        private final boolean successful;
        private final String fact;
        private final String language;
        private final String category;
        private final String source;
        private final long responseTimeMs;
        private final String errorMessage;

        public static FactResult success(String fact, String language, String category, 
                                       String source, long responseTimeMs) {
            return new FactResult(true, fact, language, category, source, responseTimeMs, null);
        }

        public static FactResult error(String errorMessage) {
            return new FactResult(false, null, null, null, null, 0, errorMessage);
        }
    }
}