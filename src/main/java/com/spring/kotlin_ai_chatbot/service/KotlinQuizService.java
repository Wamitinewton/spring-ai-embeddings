package com.spring.kotlin_ai_chatbot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.kotlin_ai_chatbot.dto.*;

import lombok.AllArgsConstructor;
import lombok.Data;

@Service
public class KotlinQuizService {

    private static final Logger logger = LoggerFactory.getLogger(KotlinQuizService.class);

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    private final Random random;
    private final int maxContextDocuments;
    
    private final Map<String, QuizSession> activeSessions = new ConcurrentHashMap<>();

    private static final QuizTopic[] QUIZ_TOPICS = {
        new QuizTopic("variables and data types", 0.8),
        new QuizTopic("functions and lambdas", 0.9),
        new QuizTopic("classes and objects", 0.7),
        new QuizTopic("inheritance and interfaces", 0.6),
        new QuizTopic("coroutines", 0.9),
        new QuizTopic("collections", 0.8),
        new QuizTopic("null safety", 0.7),
        new QuizTopic("extension functions", 0.9),
        new QuizTopic("sealed classes", 0.8),
        new QuizTopic("data classes", 0.7),
        new QuizTopic("operators", 0.8),
        new QuizTopic("scope functions", 0.9),
        new QuizTopic("higher-order functions", 0.9),
        new QuizTopic("generics", 0.7),
        new QuizTopic("delegated properties", 0.8)
    };

    private static final String QUIZ_GENERATION_PROMPT = """
            You are a Kotlin expert creating a quiz question. Generate a single multiple-choice question about Kotlin programming.

            Topic focus: {topic}
            Difficulty: {difficulty}
            Include code snippet: {includeCode}

            {context_section}

            Requirements:
            1. Create one clear, focused question about Kotlin
            2. {codeInstruction}
            3. Provide exactly 4 multiple choice options (A, B, C, D)
            4. Make sure only ONE option is correct
            5. Make the incorrect options plausible but clearly wrong
            6. Keep the question practical and educational
            7. Provide a clear, educational explanation

            Respond ONLY with valid JSON. Use this structure:
            - question: Your question text
            - codeSnippet: Code if applicable or empty string
            - options: Object with A, B, C, D properties
            - correctAnswer: Single letter (A, B, C, or D)
            - explanation: Clear explanation text
            """;

    public KotlinQuizService(ChatModel chatModel,
                           VectorStore vectorStore,
                           @Value("${app.chatbot.max-context-documents:2}") int maxContextDocuments) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
        this.maxContextDocuments = maxContextDocuments;
        
        logger.info("KotlinQuizService initialized");
    }

    public QuizSessionResponse startQuizSession(String difficulty) {
        try {
            String sessionId = UUID.randomUUID().toString();
            QuizSession session = new QuizSession(sessionId, difficulty);
            
            QuizQuestion firstQuestion = generateQuestion(difficulty, 1);
            session.addQuestion(firstQuestion);
            
            activeSessions.put(sessionId, session);
            
            logger.info("Started quiz session {} with difficulty: {}", sessionId, difficulty);
            
            return QuizSessionResponse.success(sessionId, difficulty, firstQuestion, 1, 0, false);
            
        } catch (Exception e) {
            logger.error("Error starting quiz session: {}", e.getMessage(), e);
            return QuizSessionResponse.error("Failed to start quiz session. Please try again.");
        }
    }

    public QuizAnswerResponse submitAnswer(String sessionId, String userAnswer) {
        try {
            QuizSession session = activeSessions.get(sessionId);
            if (session == null) {
                return QuizAnswerResponse.error("Quiz session not found or expired");
            }

            QuizQuestion currentQuestion = session.getCurrentQuestion();
            boolean isCorrect = userAnswer.equalsIgnoreCase(currentQuestion.getCorrectAnswer());
            
            if (isCorrect) {
                session.incrementScore();
            }

            String message = isCorrect 
                ? "🎉 Correct! " + currentQuestion.getExplanation()
                : "❌ Incorrect. " + currentQuestion.getExplanation();

            boolean hasNextQuestion = session.getCurrentQuestionNumber() < 5;
            QuizQuestion nextQuestion = null;
            QuizSessionSummary summary = null;

            if (hasNextQuestion) {
                nextQuestion = generateQuestion(session.getDifficulty(), session.getCurrentQuestionNumber() + 1);
                session.addQuestion(nextQuestion);
            } else {
                session.complete();
                summary = QuizSessionSummary.create(sessionId, session.getDifficulty(), 
                                                  session.getScore(), session.getCompletionTime());
                activeSessions.remove(sessionId);
            }

            return QuizAnswerResponse.success(isCorrect, message, currentQuestion.getCorrectAnswer(),
                                            currentQuestion.getExplanation(), session.getScore(),
                                            hasNextQuestion, nextQuestion, summary);

        } catch (Exception e) {
            logger.error("Error submitting quiz answer: {}", e.getMessage(), e);
            return QuizAnswerResponse.error("Failed to process answer. Please try again.");
        }
    }

    private QuizQuestion generateQuestion(String difficulty, int questionNumber) {
        try {
            QuizTopic topic = QUIZ_TOPICS[random.nextInt(QUIZ_TOPICS.length)];
            boolean includeCode = random.nextDouble() < topic.codeSnippetProbability;
            
            logger.info("Generating question {} for topic: {} with code: {}", questionNumber, topic.name, includeCode);

            String contextSection = getContextForTopic(topic.name);

            String questionJson = generateQuestionJson(topic.name, difficulty, includeCode, contextSection);
            
            return parseQuestionFromJson(questionJson, questionNumber);

        } catch (Exception e) {
            logger.error("Error generating quiz question: {}", e.getMessage(), e);
            return createFallbackQuestion(questionNumber);
        }
    }

    private String getContextForTopic(String topic) {
        try {
            SearchRequest searchRequest = SearchRequest
                    .builder()
                    .query(topic + " Kotlin programming")
                    .topK(maxContextDocuments)
                    .similarityThreshold(0.5)
                    .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            
            if (documents.isEmpty()) {
                return "";
            }

            String context = documents.stream()
                    .map(Document::getText)
                    .limit(1) // Keep it simple
                    .reduce((a, b) -> a + "\n\n" + b)
                    .orElse("");

            return context.isEmpty() ? "" : "Reference material:\n" + context;

        } catch (Exception e) {
            logger.warn("Could not retrieve context for topic {}: {}", topic, e.getMessage());
            return "";
        }
    }

    private String generateQuestionJson(String topic, String difficulty, boolean includeCode, String contextSection) {
        String codeInstruction = includeCode 
            ? "Include a relevant Kotlin code snippet that the question is based on"
            : "Do not include a code snippet, make it a conceptual question";

        PromptTemplate promptTemplate = new PromptTemplate(QUIZ_GENERATION_PROMPT);
        Map<String, Object> promptVariables = Map.of(
                "topic", topic,
                "difficulty", difficulty,
                "includeCode", includeCode,
                "codeInstruction", codeInstruction,
                "context_section", contextSection);

        Prompt prompt = promptTemplate.create(promptVariables);
        org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);

        return response.getResult().getOutput().getText().trim();
    }

    private QuizQuestion parseQuestionFromJson(String jsonResponse, int questionNumber) throws JsonProcessingException {
        // Clean the JSON response
        String cleanJson = jsonResponse;
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7);
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
        }
        cleanJson = cleanJson.trim();

        JsonNode jsonNode = objectMapper.readTree(cleanJson);

        String question = jsonNode.get("question").asText();
        String codeSnippet = jsonNode.has("codeSnippet") ? jsonNode.get("codeSnippet").asText() : "";
        String correctAnswer = jsonNode.get("correctAnswer").asText();
        String explanation = jsonNode.get("explanation").asText();

        JsonNode optionsNode = jsonNode.get("options");
        List<QuizOption> options = new ArrayList<>();
        
        options.add(new QuizOption("A", optionsNode.get("A").asText()));
        options.add(new QuizOption("B", optionsNode.get("B").asText()));
        options.add(new QuizOption("C", optionsNode.get("C").asText()));
        options.add(new QuizOption("D", optionsNode.get("D").asText()));

        return new QuizQuestion(questionNumber, question, codeSnippet, options, correctAnswer, explanation);
    }

    private QuizQuestion createFallbackQuestion(int questionNumber) {
        List<QuizOption> options = List.of(
            new QuizOption("A", "var"),
            new QuizOption("B", "val"),
            new QuizOption("C", "const"),
            new QuizOption("D", "let")
        );

        return new QuizQuestion(questionNumber, 
            "Which keyword is used to declare an immutable variable in Kotlin?",
            "", options, "B",
            "val is used for immutable variables (read-only), while var is for mutable variables.");
    }

    @Data
    @AllArgsConstructor
    private static class QuizTopic {
        private String name;
        private double codeSnippetProbability;
    }

    @Data
    private static class QuizSession {
        private String sessionId;
        private String difficulty;
        private List<QuizQuestion> questions;
        private int currentQuestionIndex;
        private int score;
        private long startTime;
        private long completionTime;
        private boolean completed;

        public QuizSession(String sessionId, String difficulty) {
            this.sessionId = sessionId;
            this.difficulty = difficulty;
            this.questions = new ArrayList<>();
            this.currentQuestionIndex = 0;
            this.score = 0;
            this.startTime = System.currentTimeMillis();
            this.completed = false;
        }

        public void addQuestion(QuizQuestion question) {
            questions.add(question);
        }

        public QuizQuestion getCurrentQuestion() {
            if (currentQuestionIndex < questions.size()) {
                return questions.get(currentQuestionIndex);
            }
            return null;
        }

        public int getCurrentQuestionNumber() {
            return currentQuestionIndex + 1;
        }

        public void incrementScore() {
            score++;
            currentQuestionIndex++;
        }

        public void moveToNext() {
            currentQuestionIndex++;
        }

        public void complete() {
            completed = true;
            completionTime = System.currentTimeMillis() - startTime;
        }
    }
}