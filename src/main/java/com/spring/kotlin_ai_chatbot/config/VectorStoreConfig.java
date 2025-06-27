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
import java.util.concurrent.TimeUnit;

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
            QdrantGrpcClient.Builder clientBuilder = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, useTls)
                    .withApiKey(qdrantApiKey);

            QdrantGrpcClient grpcClient = clientBuilder.build();
            
            QdrantClient client = new QdrantClient(grpcClient);

            logger.info("Testing Qdrant connection...");
            try {
                List<String> collections = client.listCollectionsAsync(Duration.ofSeconds(30)).get(30, TimeUnit.SECONDS);
                logger.info("Successfully connected to Qdrant. Available collections: {}", collections);
            } catch (Exception e) {
                logger.warn("Could not list collections during startup (this may be normal): {}", e.getMessage());
            }
            
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
            QdrantVectorStore vectorStore = QdrantVectorStore.builder(qdrantClient, embeddingModel)
                    .collectionName(collectionName)
                    .initializeSchema(true)
                    .build();
                    
            logger.info("Successfully initialized Qdrant vector store");
            return vectorStore;
            
        } catch (Exception e) {
            logger.error("Failed to initialize vector store: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize vector store: " + e.getMessage(), e);
        }
    }
}