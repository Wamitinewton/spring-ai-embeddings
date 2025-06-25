package com.spring.kotlin_ai_chatbot.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.qdrant.host}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port}")
    private int qdrantPort;

    @Value("${spring.ai.vectorstore.qdrant.api-key}")
    private String qdrantApiKey;

    @Value("${spring.ai.vectorstore.qdrant.collection-name}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.qdrant.use-tls}")
    private boolean useTls;

    @Bean
    @Primary
    public QdrantClient qdrantClient() {
        return new QdrantClient(
            QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, useTls)
                .withApiKey(qdrantApiKey)
                .build()
        );
    }

    @Bean
    @Primary
    public VectorStore vectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(true)
                .build();
    }
}