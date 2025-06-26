package com.spring.kotlin_ai_chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizSessionResponse {
    private String sessionId;
    private String difficulty;
    private QuizQuestion currentQuestion;
    private int totalQuestions;
    private int currentQuestionNumber;
    private int score;
    private boolean isComplete;
    private boolean successful;
    private String errorMessage;

    public static QuizSessionResponse success(String sessionId, String difficulty, 
                                            QuizQuestion currentQuestion, int currentQuestionNumber, 
                                            int score, boolean isComplete) {
        QuizSessionResponse response = new QuizSessionResponse();
        response.sessionId = sessionId;
        response.difficulty = difficulty;
        response.currentQuestion = currentQuestion;
        response.totalQuestions = 5;
        response.currentQuestionNumber = currentQuestionNumber;
        response.score = score;
        response.isComplete = isComplete;
        response.successful = true;
        return response;
    }

    public static QuizSessionResponse error(String errorMessage) {
        QuizSessionResponse response = new QuizSessionResponse();
        response.errorMessage = errorMessage;
        response.successful = false;
        return response;
    }

    public QuizSessionResponse forUser() {
        if (currentQuestion != null) {
            QuizQuestion userQuestion = new QuizQuestion(
                currentQuestion.getQuestionNumber(),
                currentQuestion.getQuestion(),
                currentQuestion.getCodeSnippet(),
                currentQuestion.getOptions(),
                null, 
                null  
            );
            
            return new QuizSessionResponse(sessionId, difficulty, userQuestion, 
                                         totalQuestions, currentQuestionNumber, score, 
                                         isComplete, successful, errorMessage);
        }
        return this;
    }
}

