package com.spring.kotlin_ai_chatbot.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizSessionRequest {

    @Pattern(regexp = "^(beginner|intermediate|advanced)$", 
             message = "Difficulty must be: beginner, intermediate, or advanced")
    private String difficulty = "beginner";
}
