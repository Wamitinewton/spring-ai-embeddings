package com.spring.kotlin_ai_chatbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.kotlin_ai_chatbot.data.QuizSession;
import com.spring.kotlin_ai_chatbot.dto.*;
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

import java.util.*;

@Service
public class MultiLanguageQuizService {

    private static final Logger logger = LoggerFactory.getLogger(MultiLanguageQuizService.class);

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final QuizSessionService sessionService;
    private final ObjectMapper objectMapper;
    private final Random random;
    private final int maxContextDocuments;

    private static final Map<String, Map<String, Double>> LANGUAGE_TOPICS = Map.of(
        "python", Map.of(
            "functions and decorators", 0.9,
            "list comprehensions", 0.8,
            "classes and inheritance", 0.7,
            "async/await", 0.9,
            "data structures", 0.8,
            "exception handling", 0.6,
            "modules and packages", 0.7,
            "lambda functions", 0.9,
            "generators", 0.8,
            "context managers", 0.9
        ),
        "java", Map.of(
            "object-oriented programming", 0.8,
            "collections framework", 0.8,
            "stream API", 0.9,
            "exception handling", 0.7,
            "generics", 0.8,
            "lambda expressions", 0.9,
            "multithreading", 0.8,
            "interfaces and abstract classes", 0.7,
            "design patterns", 0.6,
            "JVM concepts", 0.5
        ),
        "kotlin", Map.of(
            "functions and lambdas", 0.9,
            "classes and objects", 0.7,
            "coroutines", 0.9,
            "collections", 0.8,
            "null safety", 0.7,
            "extension functions", 0.9,
            "sealed classes", 0.8,
            "data classes", 0.7,
            "scope functions", 0.9,
            "delegation", 0.8
        ),
        "javascript", Map.of(
            "promises and async/await", 0.9,
            "closures", 0.8,
            "prototypes and inheritance", 0.7,
            "event handling", 0.8,
            "array methods", 0.9,
            "destructuring", 0.8,
            "modules", 0.7,
            "arrow functions", 0.9,
            "DOM manipulation", 0.8,
            "error handling", 0.6
        ),
        "typescript", Map.of(
            "type annotations", 0.8,
            "interfaces", 0.7,
            "generics", 0.8,
            "decorators", 0.9,
            "union and intersection types", 0.8,
            "modules and namespaces", 0.7,
            "advanced types", 0.8,
            "type guards", 0.9,
            "enums", 0.7,
            "conditional types", 0.8
        ),
        "csharp", Map.of(
            "LINQ", 0.9,
            "async/await", 0.9,
            "properties and indexers", 0.7,
            "delegates and events", 0.8,
            "generics", 0.8,
            "nullable reference types", 0.7,
            "pattern matching", 0.8,
            "attributes", 0.7,
            "records", 0.8,
            "dependency injection", 0.6
        ),
        "cpp", Map.of(
            "pointers and references", 0.9,
            "templates", 0.8,
            "smart pointers", 0.9,
            "STL containers", 0.8,
            "RAII", 0.7,
            "virtual functions", 0.8,
            "move semantics", 0.9,
            "lambda expressions", 0.8,
            "operator overloading", 0.7,
            "memory management", 0.8
        ),
        "rust", Map.of(
            "ownership and borrowing", 0.9,
            "pattern matching", 0.8,
            "error handling", 0.8,
            "traits", 0.8,
            "lifetimes", 0.9,
            "iterators", 0.8,
            "async/await", 0.9,
            "macros", 0.8,
            "cargo and modules", 0.6,
            "unsafe code", 0.7
        ),
        "go", Map.of(
            "goroutines", 0.9,
            "channels", 0.9,
            "interfaces", 0.8,
            "error handling", 0.7,
            "slices and maps", 0.8,
            "pointers", 0.8,
            "structs and methods", 0.7,
            "packages", 0.6,
            "defer statement", 0.8,
            "context package", 0.8
        ),
        "swift", Map.of(
            "optionals", 0.8,
            "closures", 0.9,
            "protocols", 0.8,
            "generics", 0.8,
            "property wrappers", 0.9,
            "async/await", 0.9,
            "error handling", 0.7,
            "extensions", 0.8,
            "enums with associated values", 0.8,
            "memory management", 0.7
        )
    );

    private static final String QUIZ_PROMPT = """
            Create a {language} programming quiz question focused on: {topic}
            Difficulty: {difficulty}
            {codeInstruction}
            
            {context}
            
            Generate a clear, educational question with:
            1. One focused question about {language} programming
            2. Exactly 4 options (A, B, C, D) - only ONE correct
            3. Practical, realistic scenarios relevant to {language}
            4. Clear explanation for learning
            
            Use {language}-specific syntax and concepts. Make it challenging but fair for {difficulty} level.
            
            Respond with valid JSON:
            {{
                "question": "Your {language} question text",
                "codeSnippet": "{codeSnippet}",
                "options": {{
                    "A": "Option A text",
                    "B": "Option B text", 
                    "C": "Option C text",
                    "D": "Option D text"
                }},
                "correctAnswer": "{correctLetter}",
                "explanation": "Educational explanation about {language}"
            }}
            """;

    public MultiLanguageQuizService(ChatModel chatModel,
                                  VectorStore vectorStore,
                                  QuizSessionService sessionService,
                                  @Value("${app.chatbot.max-context-documents:2}") int maxContextDocuments) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.sessionService = sessionService;
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
        this.maxContextDocuments = maxContextDocuments;
        
        logger.info("MultiLanguageQuizService initialized with Redis session management");
    }


    public QuizSessionResponse startQuizSession(String language, String difficulty) {
        try {
            String normalizedLanguage = normalizeLanguage(language);
            
            QuizSession session = sessionService.createSession(normalizedLanguage, difficulty);
            
            QuizQuestion firstQuestion = generateQuestion(normalizedLanguage, difficulty, 1);
            session.addQuestion(firstQuestion);
            sessionService.updateSession(session);
            
            logger.info("Started quiz session {} - Language: {}, Difficulty: {}", 
                       session.getSessionId(), normalizedLanguage, difficulty);
            
            return QuizSessionResponse.success(
                session.getSessionId(), 
                normalizedLanguage,
                difficulty, 
                firstQuestion, 
                1, 
                0, 
                false
            );
            
        } catch (Exception e) {
            logger.error("Error starting quiz session: {}", e.getMessage(), e);
            return QuizSessionResponse.error("Failed to start quiz. Please try again.");
        }
    }

    /**
     * Submits an answer and returns feedback with next question or summary
     */
    public QuizAnswerResponse submitAnswer(String sessionId, String userAnswer) {
        try {
            Optional<QuizSession> sessionOpt = sessionService.getSession(sessionId);
            if (sessionOpt.isEmpty()) {
                return QuizAnswerResponse.error("Quiz session not found or expired. Please start a new quiz.");
            }

            QuizSession session = sessionOpt.get();
            QuizQuestion currentQuestion = session.getCurrentQuestion();
            
            if (currentQuestion == null) {
                return QuizAnswerResponse.error("No current question found. Session may be corrupted.");
            }

            boolean isCorrect = session.submitAnswer(userAnswer);
            
            String message = isCorrect 
                ? "🎉 Correct! " + currentQuestion.getExplanation()
                : "❌ Incorrect. " + currentQuestion.getExplanation();

            if (!session.hasNextQuestion()) {
                session.complete();
                sessionService.updateSession(session);
                
                QuizSessionSummary summary = createSessionSummary(session);
                
                return QuizAnswerResponse.success(
                    isCorrect, 
                    message, 
                    currentQuestion.getCorrectAnswer(),
                    currentQuestion.getExplanation(), 
                    session.getScore(),
                    false, 
                    null, 
                    summary
                );
            }

            // Generate next question
            QuizQuestion nextQuestion = generateQuestion(
                session.getLanguage(), 
                session.getDifficulty(), 
                session.getCurrentQuestionNumber()
            );
            session.addQuestion(nextQuestion);
            sessionService.updateSession(session);

            return QuizAnswerResponse.success(
                isCorrect, 
                message, 
                currentQuestion.getCorrectAnswer(),
                currentQuestion.getExplanation(), 
                session.getScore(),
                true, 
                nextQuestion, 
                null
            );

        } catch (Exception e) {
            logger.error("Error submitting quiz answer: {}", e.getMessage(), e);
            return QuizAnswerResponse.error("Failed to process answer. Please try again.");
        }
    }

    /**
     * Gets current session status
     */
    public Optional<QuizSession> getSessionStatus(String sessionId) {
        return sessionService.getSession(sessionId);
    }

    /**
     * Extends session timeout when user is active
     */
    public void keepSessionAlive(String sessionId) {
        sessionService.extendSession(sessionId);
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            return "python";
        }
        
        String normalized = language.toLowerCase().trim();
        
        // Validate against supported languages
        Set<String> supportedLanguages = Set.of(
            "kotlin", "java", "python", "javascript", "typescript", 
            "csharp", "cpp", "rust", "go", "swift"
        );
        
        return supportedLanguages.contains(normalized) ? normalized : "python";
    }

    private QuizQuestion generateQuestion(String language, String difficulty, int questionNumber) {
        try {
            Map<String, Double> topicsForLanguage = LANGUAGE_TOPICS.get(language);
            if (topicsForLanguage == null) {
                logger.warn("No topics found for language: {}, using Python topics", language);
                topicsForLanguage = LANGUAGE_TOPICS.get("python");
            }
            
            List<String> topics = new ArrayList<>(topicsForLanguage.keySet());
            String topic = topics.get(random.nextInt(topics.size()));
            double codeProb = topicsForLanguage.get(topic);
            
            boolean includeCode = random.nextDouble() < codeProb;
            
            logger.debug("Generating {} question {} for topic: {} with code: {}", 
                        language, questionNumber, topic, includeCode);

            String context = getTopicContext(topic, language);
            
            String questionJson = callAiForQuestion(language, topic, difficulty, includeCode, context);
            
            return parseQuestionJson(questionJson, questionNumber);

        } catch (Exception e) {
            logger.error("Error generating {} question: {}", language, e.getMessage(), e);
            return createFallbackQuestion(language, questionNumber);
        }
    }

    private String getTopicContext(String topic, String language) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(topic + " " + language + " programming")
                    .topK(maxContextDocuments)
                    .similarityThreshold(0.5)
                    .build();

            List<Document> docs = vectorStore.similaritySearch(request);
            
            if (docs.isEmpty()) {
                return "";
            }

            return docs.stream()
                    .map(Document::getText)
                    .findFirst()
                    .map(text -> "Reference material:\n" + text.substring(0, Math.min(text.length(), 800)))
                    .orElse("");

        } catch (Exception e) {
            logger.warn("Could not get context for topic {} in {}: {}", topic, language, e.getMessage());
            return "";
        }
    }

    private String callAiForQuestion(String language, String topic, String difficulty, 
                                   boolean includeCode, String context) {
        String codeInstruction = includeCode 
            ? String.format("Include a relevant %s code snippet", getLanguageDisplayName(language))
            : "Make it a conceptual question without code";
        
        String codeSnippet = includeCode ? "code snippet here" : "";

        PromptTemplate template = new PromptTemplate(QUIZ_PROMPT);
        Map<String, Object> variables = Map.of(
                "language", getLanguageDisplayName(language),
                "topic", topic,
                "difficulty", difficulty,
                "codeInstruction", codeInstruction,
                "codeSnippet", codeSnippet,
                "correctLetter", "A",
                "context", context
        );

        Prompt prompt = template.create(variables);
        return chatModel.call(prompt).getResult().getOutput().getText().trim();
    }

    private QuizQuestion parseQuestionJson(String jsonResponse, int questionNumber) throws JsonProcessingException {
        String cleanJson = jsonResponse
                .replaceFirst("^```json\\s*", "")
                .replaceFirst("```\\s*$", "")
                .trim();

        JsonNode json = objectMapper.readTree(cleanJson);

        String question = json.get("question").asText();
        String codeSnippet = json.has("codeSnippet") ? json.get("codeSnippet").asText() : "";
        String correctAnswer = json.get("correctAnswer").asText();
        String explanation = json.get("explanation").asText();

        JsonNode optionsNode = json.get("options");
        List<QuizOption> options = List.of(
            new QuizOption("A", optionsNode.get("A").asText()),
            new QuizOption("B", optionsNode.get("B").asText()),
            new QuizOption("C", optionsNode.get("C").asText()),
            new QuizOption("D", optionsNode.get("D").asText())
        );

        return new QuizQuestion(questionNumber, question, codeSnippet, options, correctAnswer, explanation);
    }

    private QuizQuestion createFallbackQuestion(String language, int questionNumber) {
        String languageDisplay = getLanguageDisplayName(language);
        
        return switch (language.toLowerCase()) {
            case "python" -> new QuizQuestion(
                questionNumber,
                "Which keyword is used to define a function in Python?",
                "",
                List.of(
                    new QuizOption("A", "function"),
                    new QuizOption("B", "def"),
                    new QuizOption("C", "func"),
                    new QuizOption("D", "define")
                ),
                "B",
                "In Python, 'def' is the keyword used to define functions."
            );
            case "java" -> new QuizQuestion(
                questionNumber,
                "Which access modifier makes a method accessible from anywhere?",
                "",
                List.of(
                    new QuizOption("A", "private"),
                    new QuizOption("B", "protected"),
                    new QuizOption("C", "public"),
                    new QuizOption("D", "default")
                ),
                "C",
                "The 'public' access modifier makes methods accessible from any class."
            );
            default -> new QuizQuestion(
                questionNumber,
                String.format("What is %s primarily used for?", languageDisplay),
                "",
                List.of(
                    new QuizOption("A", "Web development"),
                    new QuizOption("B", "Mobile development"),
                    new QuizOption("C", "System programming"),
                    new QuizOption("D", "General programming")
                ),
                "D",
                String.format("%s is a versatile programming language used for various applications.", languageDisplay)
            );
        };
    }

    private QuizSessionSummary createSessionSummary(QuizSession session) {
        int correctAnswers = session.getScore();
        long completionTimeMs = session.getSessionDurationMinutes() * 60 * 1000;
        
        return QuizSessionSummary.create(
            session.getSessionId(),
            session.getLanguage(),
            session.getDifficulty(),
            correctAnswers,
            completionTimeMs
        );
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
}