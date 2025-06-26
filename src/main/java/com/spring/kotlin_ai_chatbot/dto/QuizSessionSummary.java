package com.spring.kotlin_ai_chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizSessionSummary {
    private String sessionId;
    private String difficulty;
    private int totalQuestions;
    private int correctAnswers;
    private int score;
    private String performance;
    private String message;
    private long completionTimeMs;

    public static QuizSessionSummary create(String sessionId, String difficulty, 
                                          int correctAnswers, long completionTimeMs) {
        int totalQuestions = 5;
        int score = (correctAnswers * 100) / totalQuestions;
        
        String performance;
        String message;
        
        if (score >= 80) {
            performance = "Excellent";
            message = "🎉 Outstanding! You have a strong grasp of Kotlin concepts!";
        } else if (score >= 60) {
            performance = "Good";
            message = "👏 Well done! You're making good progress with Kotlin!";
        } else if (score >= 40) {
            performance = "Fair";
            message = "📚 Keep practicing! You're on the right track!";
        } else {
            performance = "Needs Improvement";
            message = "💪 Don't give up! Practice makes perfect in Kotlin!";
        }
        
        return new QuizSessionSummary(sessionId, difficulty, totalQuestions, correctAnswers, 
                                    score, performance, message, completionTimeMs);
    }
}
