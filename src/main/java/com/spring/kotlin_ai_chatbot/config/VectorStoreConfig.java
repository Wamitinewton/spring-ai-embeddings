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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

@Configuration
public class VectorStoreConfig {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreConfig.class);

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
        logger.info("Initializing Qdrant client with host: {}, port: {}, TLS: {}", 
                   qdrantHost, qdrantPort, useTls);
        
        try {
            QdrantClient client = new QdrantClient(
                QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, useTls)
                    .withApiKey(qdrantApiKey)
                    .build()
            );

            logger.info("Testing Qdrant connection...");
            List<String> collections = client.listCollectionsAsync(Duration.ofSeconds(10)).get();
            logger.info("Successfully connected to Qdrant. Available collections: {}", collections);
            
            return client;
        } catch (Exception e) {
            logger.error("Failed to initialize Qdrant client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to connect to Qdrant: " + e.getMessage(), e);
        }
    }

    @Bean
    @Primary
    public VectorStore vectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        logger.info("Initializing Qdrant vector store with collection: {}", collectionName);
        
        try {
            return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                    .collectionName(collectionName)
                    .initializeSchema(true)
                    .build();
        } catch (Exception e) {
            logger.error("Failed to initialize vector store: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize vector store: " + e.getMessage(), e);
        }
    }
}