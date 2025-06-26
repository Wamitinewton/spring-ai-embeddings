package com.spring.kotlin_ai_chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizSessionSummary {
    private String sessionId;
    private String language;
    private String languageDisplayName;
    private String difficulty;
    private int totalQuestions;
    private int correctAnswers;
    private int score;
    private String performance;
    private String message;
    private long completionTimeMs;

    public static QuizSessionSummary create(String sessionId, String language, String difficulty,
            int correctAnswers, long completionTimeMs) {
        int totalQuestions = 5;
        int score = (correctAnswers * 100) / totalQuestions;

        String performance;
        String message;
        String languageDisplayName = getLanguageDisplayName(language);

        if (score >= 80) {
            performance = "Excellent";
            message = String.format("🎉 Outstanding! You have a strong grasp of %s concepts!", languageDisplayName);
        } else if (score >= 60) {
            performance = "Good";
            message = String.format("👏 Well done! You're making good progress with %s!", languageDisplayName);
        } else if (score >= 40) {
            performance = "Fair";
            message = String.format("📚 Keep practicing! You're on the right track with %s!", languageDisplayName);
        } else {
            performance = "Needs Improvement";
            message = String.format("💪 Don't give up! Practice makes perfect in %s!", languageDisplayName);
        }

        return new QuizSessionSummary(sessionId, language, languageDisplayName, difficulty, totalQuestions,
                correctAnswers, score, performance, message, completionTimeMs);
    }

    private static String getLanguageDisplayName(String language) {
        if (language == null)
            return "Programming";

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