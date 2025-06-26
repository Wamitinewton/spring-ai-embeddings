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
public class KotlinQuizService {

    private static final Logger logger = LoggerFactory.getLogger(KotlinQuizService.class);

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final QuizSessionService sessionService;
    private final ObjectMapper objectMapper;
    private final Random random;
    private final int maxContextDocuments;

    private static final Map<String, Double> QUIZ_TOPICS = Map.of(
        "variables and data types", 0.8,
        "functions and lambdas", 0.9,
        "classes and objects", 0.7,
        "coroutines", 0.9,
        "collections", 0.8,
        "null safety", 0.7,
        "extension functions", 0.9,
        "sealed classes", 0.8,
        "data classes", 0.7,
        "scope functions", 0.9
    );

    private static final String QUIZ_PROMPT = """
            Create a Kotlin quiz question focused on: {topic}
            Difficulty: {difficulty}
            {codeInstruction}
            
            {context}
            
            Generate a clear, educational question with:
            1. One focused question about Kotlin
            2. Exactly 4 options (A, B, C, D) - only ONE correct
            3. Practical, realistic scenarios
            4. Clear explanation for learning
            
            Respond with valid JSON:
            {{
                "question": "Your question text",
                "codeSnippet": "{codeSnippet}",
                "options": {{
                    "A": "Option A text",
                    "B": "Option B text", 
                    "C": "Option C text",
                    "D": "Option D text"
                }},
                "correctAnswer": "{correctLetter}",
                "explanation": "Educational explanation"
            }}
            """;

    public KotlinQuizService(ChatModel chatModel,
                           VectorStore vectorStore,
                           QuizSessionService sessionService,
                           @Value("${app.chatbot.max-context-documents:2}") int maxContextDocuments) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.sessionService = sessionService;
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
        this.maxContextDocuments = maxContextDocuments;
        
        logger.info("KotlinQuizService initialized with Redis session management");
    }

    /**
     * Starts a new quiz session
     */
    public QuizSessionResponse startQuizSession(String difficulty) {
        try {
            QuizSession session = sessionService.createSession(difficulty);
            
            QuizQuestion firstQuestion = generateQuestion(difficulty, 1);
            session.addQuestion(firstQuestion);
            sessionService.updateSession(session);
            
            logger.info("Started quiz session {} with difficulty: {}", session.getSessionId(), difficulty);
            
            return QuizSessionResponse.success(
                session.getSessionId(), 
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

            // Submit the answer
            boolean isCorrect = session.submitAnswer(userAnswer);
            
            // Prepare response message
            String message = isCorrect 
                ? "🎉 Correct! " + currentQuestion.getExplanation()
                : "❌ Incorrect. " + currentQuestion.getExplanation();

            // Check if quiz is complete
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
            QuizQuestion nextQuestion = generateQuestion(session.getDifficulty(), session.getCurrentQuestionNumber());
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

    private QuizQuestion generateQuestion(String difficulty, int questionNumber) {
        try {
            List<String> topics = new ArrayList<>(QUIZ_TOPICS.keySet());
            String topic = topics.get(random.nextInt(topics.size()));
            double codeProb = QUIZ_TOPICS.get(topic);
            
            boolean includeCode = random.nextDouble() < codeProb;
            
            logger.debug("Generating question {} for topic: {} with code: {}", questionNumber, topic, includeCode);

            // Get context from vector store
            String context = getTopicContext(topic);
            
            // Generate question using AI
            String questionJson = callAiForQuestion(topic, difficulty, includeCode, context);
            
            return parseQuestionJson(questionJson, questionNumber);

        } catch (Exception e) {
            logger.error("Error generating question: {}", e.getMessage(), e);
            return createFallbackQuestion(questionNumber);
        }
    }

    private String getTopicContext(String topic) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(topic + " Kotlin programming")
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
            logger.warn("Could not get context for topic {}: {}", topic, e.getMessage());
            return "";
        }
    }

    private String callAiForQuestion(String topic, String difficulty, boolean includeCode, String context) {
        String codeInstruction = includeCode 
            ? "Include a relevant Kotlin code snippet"
            : "Make it a conceptual question without code";
        
        String codeSnippet = includeCode ? "code snippet here" : "";

        PromptTemplate template = new PromptTemplate(QUIZ_PROMPT);
        Map<String, Object> variables = Map.of(
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
        // Clean JSON response
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

    private QuizQuestion createFallbackQuestion(int questionNumber) {
        List<QuizOption> options = List.of(
            new QuizOption("A", "var"),
            new QuizOption("B", "val"),
            new QuizOption("C", "const"),
            new QuizOption("D", "let")
        );

        return new QuizQuestion(
            questionNumber, 
            "Which keyword is used to declare an immutable variable in Kotlin?",
            "", 
            options, 
            "B",
            "val is used for immutable variables (read-only), while var is for mutable variables."
        );
    }

    private QuizSessionSummary createSessionSummary(QuizSession session) {
        int correctAnswers = session.getScore();
        long completionTimeMs = session.getSessionDurationMinutes() * 60 * 1000;
        
        return QuizSessionSummary.create(
            session.getSessionId(),
            session.getDifficulty(),
            correctAnswers,
            completionTimeMs
        );
    }
}