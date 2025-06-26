package com.spring.kotlin_ai_chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizAnswerResponse {
    private boolean correct;
    private String message;
    private String correctAnswer;
    private String explanation;
    private int currentScore;
    private boolean hasNextQuestion;
    private QuizQuestion nextQuestion; 
    private QuizSessionSummary sessionSummary; 
    private boolean successful;
    private String errorMessage;

    public static QuizAnswerResponse success(boolean correct, String message, String correctAnswer,
                                           String explanation, int currentScore, boolean hasNextQuestion,
                                           QuizQuestion nextQuestion, QuizSessionSummary sessionSummary) {
        QuizAnswerResponse response = new QuizAnswerResponse();
        response.correct = correct;
        response.message = message;
        response.correctAnswer = correctAnswer;
        response.explanation = explanation;
        response.currentScore = currentScore;
        response.hasNextQuestion = hasNextQuestion;
        response.nextQuestion = nextQuestion;
        response.sessionSummary = sessionSummary;
        response.successful = true;
        return response;
    }

    public static QuizAnswerResponse error(String errorMessage) {
        QuizAnswerResponse response = new QuizAnswerResponse();
        response.errorMessage = errorMessage;
        response.successful = false;
        return response;
    }

    public QuizAnswerResponse forUser() {
        if (nextQuestion != null) {
            QuizQuestion userNextQuestion = new QuizQuestion(
                nextQuestion.getQuestionNumber(),
                nextQuestion.getQuestion(),
                nextQuestion.getCodeSnippet(),
                nextQuestion.getOptions(),
                null,
                null 
            );
            
            return new QuizAnswerResponse(correct, message, correctAnswer, explanation,
                                        currentScore, hasNextQuestion, userNextQuestion,
                                        sessionSummary, successful, errorMessage);
        }
        return this;
    }
}
