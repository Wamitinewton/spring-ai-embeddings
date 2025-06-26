package com.spring.kotlin_ai_chatbot.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestion {
    private int questionNumber; 
    private String question;
    private String codeSnippet;
    private List<QuizOption> options;
    private String correctAnswer;
    private String explanation; 
}
