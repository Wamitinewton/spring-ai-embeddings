package com.spring.kotlin_ai_chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizAnswerRequest {

    @NotBlank(message = "Session ID cannot be empty")
    private String sessionId;

    @NotBlank(message = "Answer cannot be empty")
    @Pattern(regexp = "^[ABCD]$", message = "Answer must be A, B, C, or D")
    private String answer;
}
