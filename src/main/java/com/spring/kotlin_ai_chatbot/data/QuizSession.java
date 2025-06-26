package com.spring.kotlin_ai_chatbot.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.spring.kotlin_ai_chatbot.dto.QuizQuestion;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class QuizSession {
    
    private String sessionId;
    private String difficulty;
    private List<QuizQuestion> questions = new ArrayList<>();
    private List<String> userAnswers = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private int score = 0;
    private LocalDateTime startTime;
    private LocalDateTime lastActivity;
    private boolean completed = false;
    
    // Constants
    public static final int TOTAL_QUESTIONS = 5;
    public static final int SESSION_TIMEOUT_MINUTES = 30;

    // Constructor for Jackson deserialization
    @JsonCreator
    public QuizSession(@JsonProperty("sessionId") String sessionId, 
                       @JsonProperty("difficulty") String difficulty,
                       @JsonProperty("questions") List<QuizQuestion> questions,
                       @JsonProperty("userAnswers") List<String> userAnswers,
                       @JsonProperty("currentQuestionIndex") int currentQuestionIndex,
                       @JsonProperty("score") int score,
                       @JsonProperty("startTime") LocalDateTime startTime,
                       @JsonProperty("lastActivity") LocalDateTime lastActivity,
                       @JsonProperty("completed") boolean completed) {
        this.sessionId = sessionId;
        this.difficulty = difficulty;
        this.questions = questions != null ? questions : new ArrayList<>();
        this.userAnswers = userAnswers != null ? userAnswers : new ArrayList<>();
        this.currentQuestionIndex = currentQuestionIndex;
        this.score = score;
        this.startTime = startTime != null ? startTime : LocalDateTime.now();
        this.lastActivity = lastActivity != null ? lastActivity : LocalDateTime.now();
        this.completed = completed;
    }

    public QuizSession(String sessionId, String difficulty) {
        this.sessionId = sessionId;
        this.difficulty = difficulty;
        this.questions = new ArrayList<>();
        this.userAnswers = new ArrayList<>();
        this.currentQuestionIndex = 0;
        this.score = 0;
        this.startTime = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
        this.completed = false;
    }

    /**
     * Adds a new question to the session
     */
    public void addQuestion(QuizQuestion question) {
        questions.add(question);
        updateActivity();
    }

    /**
     * Gets the current question user should answer
     */
    @JsonIgnore
    public QuizQuestion getCurrentQuestion() {
        if (currentQuestionIndex < questions.size()) {
            return questions.get(currentQuestionIndex);
        }
        return null;
    }

    /**
     * Submits an answer and moves to next question
     */
    public boolean submitAnswer(String userAnswer) {
        if (currentQuestionIndex >= questions.size()) {
            return false;
        }

        userAnswers.add(userAnswer);
        
        QuizQuestion currentQuestion = questions.get(currentQuestionIndex);
        boolean isCorrect = userAnswer.equalsIgnoreCase(currentQuestion.getCorrectAnswer());
        
        if (isCorrect) {
            score++;
        }
        
        currentQuestionIndex++;
        updateActivity();
        
        // Mark as completed if this was the last question
        if (currentQuestionIndex >= TOTAL_QUESTIONS) {
            completed = true;
        }
        
        return isCorrect;
    }

    /**
     * Checks if there are more questions to answer
     */
    @JsonIgnore
    public boolean hasNextQuestion() {
        return currentQuestionIndex < TOTAL_QUESTIONS && !completed;
    }

    /**
     * Gets the current question number (1-based)
     */
    @JsonIgnore
    public int getCurrentQuestionNumber() {
        return currentQuestionIndex + 1;
    }

    /**
     * Calculates completion percentage
     */
    @JsonIgnore
    public int getCompletionPercentage() {
        return (currentQuestionIndex * 100) / TOTAL_QUESTIONS;
    }

    /**
     * Gets total session duration in minutes
     */
    @JsonIgnore
    public long getSessionDurationMinutes() {
        if (startTime == null) return 0;
        LocalDateTime endTime = completed ? lastActivity : LocalDateTime.now();
        return java.time.Duration.between(startTime, endTime).toMinutes();
    }

    /**
     * Checks if session has expired
     */
    @JsonIgnore
    public boolean isExpired() {
        if (lastActivity == null) return true;
        return java.time.Duration.between(lastActivity, LocalDateTime.now()).toMinutes() > SESSION_TIMEOUT_MINUTES;
    }

    /**
     * Updates last activity timestamp
     */
    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }

    /**
     * Marks session as completed
     */
    public void complete() {
        this.completed = true;
        updateActivity();
    }

    @JsonIgnore
    public String getStatus() {
        if (completed) {
            return "COMPLETED";
        } else if (isExpired()) {
            return "EXPIRED";
        } else {
            return "ACTIVE";
        }
    }
}