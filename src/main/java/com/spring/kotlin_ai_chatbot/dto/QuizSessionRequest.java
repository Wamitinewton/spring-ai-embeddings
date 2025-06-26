package com.spring.kotlin_ai_chatbot.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizSessionRequest {

    @Pattern(regexp = "^(kotlin|java|python|javascript|typescript|csharp|cpp|rust|go|swift)$", 
             message = "Language must be one of: kotlin, java, python, javascript, typescript, csharp, cpp, rust, go, swift")
    private String language = "python";

    @Pattern(regexp = "^(beginner|intermediate|advanced)$", 
             message = "Difficulty must be: beginner, intermediate, or advanced")
    private String difficulty = "beginner";

    public QuizSessionRequest(String difficulty) {
        this.language = "python";
        this.difficulty = difficulty != null ? difficulty : "beginner";
    }
}