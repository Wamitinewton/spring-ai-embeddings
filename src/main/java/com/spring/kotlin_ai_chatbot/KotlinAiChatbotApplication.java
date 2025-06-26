package com.spring.kotlin_ai_chatbot;

import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {QdrantVectorStoreAutoConfiguration.class})
@EnableScheduling
public class KotlinAiChatbotApplication {

	public static void main(String[] args) {
		SpringApplication.run(KotlinAiChatbotApplication.class, args);
	}

}
