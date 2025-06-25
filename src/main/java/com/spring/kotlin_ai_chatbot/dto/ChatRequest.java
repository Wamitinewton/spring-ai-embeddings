package com.spring.kotlin_ai_chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Question cannot be empty")
    @Size(max = 1000, message = "Question must be less than 1000 characters")
    private String question;

}
