# Kotlin AI Chatbot with Spring AI & RAG

A Spring Boot application demonstrating Retrieval-Augmented Generation (RAG) using Spring AI, OpenAI embeddings, and Qdrant vector database for intelligent Kotlin programming assistance.

![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-blue)
![OpenAI](https://img.shields.io/badge/OpenAI-Embeddings-purple)
![Qdrant](https://img.shields.io/badge/Qdrant-Vector%20DB-red)

## Core Concepts

This application implements the RAG (Retrieval-Augmented Generation) pattern:

1. **Document Ingestion**: PDF documents are processed and converted to embeddings
2. **Vector Storage**: Embeddings stored in Qdrant vector database
3. **Similarity Search**: User queries are embedded and matched against stored vectors
4. **Context Retrieval**: Relevant document chunks are retrieved based on similarity
5. **LLM Generation**: Retrieved context is used to generate accurate responses

## Architecture

```
User Query → Embedding → Vector Search → Context Retrieval → LLM → Response
     ↓           ↓            ↓              ↓           ↓        ↓
  "What is     Vector    Qdrant DB     Document      GPT-4o   Expert
   val vs      [0.1,     Similarity    Chunks        with     Answer
   var?"       0.3...]   Search        Retrieved     Context
```

## Spring AI Configuration

### Vector Store Setup

The core configuration establishes connection to Qdrant vector database:

```java
@Configuration
public class VectorStoreConfig {

    @Bean
    @Primary
    public QdrantClient qdrantClient() {
        QdrantClient client = new QdrantClient(
            QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, useTls)
                .withApiKey(qdrantApiKey)
                .build()
        );
        
        // Test connection and log available collections
        List<String> collections = client.listCollectionsAsync(Duration.ofSeconds(10)).get();
        logger.info("Connected to Qdrant. Collections: {}", collections);
        
        return client;
    }

    @Bean
    @Primary
    public VectorStore vectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(true)  // Auto-create collection if not exists
                .build();
    }
}
```

### Embedding Model Configuration

OpenAI embeddings are configured via application properties:

```properties
# OpenAI Configuration
spring.ai.openai.api-key=${openai.api.key}
spring.ai.openai.embedding.options.model=text-embedding-3-small

# Chat Model for Response Generation
spring.ai.openai.chat.options.model=gpt-4o
spring.ai.openai.chat.options.temperature=0.7
```

## Document Processing Pipeline

### PDF Ingestion and Chunking

The `PdfProcessingService` handles document ingestion:

```java
@Service
public class PdfProcessingService {

    public ProcessingResult processPdfResource(Resource pdfResource) {
        // 1. Read PDF using Spring AI PDF Document Reader
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
        List<Document> documents = pdfReader.get();

        // 2. Split documents into chunks for optimal retrieval
        TokenTextSplitter textSplitter = new TokenTextSplitter(
            chunkSize,      // 800 tokens per chunk
            chunkOverlap,   // 100 token overlap between chunks
            5,              // minimum chunk size
            10000,          // maximum chunk size
            true            // keep separator
        );
        List<Document> chunks = textSplitter.apply(documents);

        // 3. Enhance with metadata for better context
        enhanceChunksWithMetadata(chunks, pdfResource.getFilename());

        // 4. Store in vector database (batch processing)
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<Document> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            vectorStore.add(batch);  // Automatically generates embeddings
        }
    }

    private void enhanceChunksWithMetadata(List<Document> chunks, String filename) {
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = chunk.getMetadata();
            
            metadata.put("source", filename);
            metadata.put("chunk_index", i);
            metadata.put("content_type", "kotlin_documentation");
            metadata.put("language", "kotlin");
        }
    }
}
```

### Key Chunking Strategy

- **Chunk Size**: 800 tokens (optimal for embedding models)
- **Overlap**: 100 tokens (preserves context between chunks)
- **Metadata Enhancement**: Adds source tracking and indexing

## RAG Implementation

### Core RAG Service

The `KotlinChatbotService` implements the complete RAG pipeline:

```java
@Service
public class KotlinChatbotService {

    public ChatbotResponse askQuestion(String question) {
        // Step 1: Vector Similarity Search
        List<Document> relevantDocs = findRelevantDocuments(question);
        
        // Step 2: Context Preparation
        String context = createContextFromDocuments(relevantDocs);
        
        // Step 3: LLM Generation with Context
        String answer = generateAnswer(question, context);
        
        // Step 4: Confidence Assessment
        String confidence = calculateConfidence(relevantDocs, answer);
        
        return ChatbotResponse.success(answer, confidence, relevantDocs.size(), responseTime);
    }

    private List<Document> findRelevantDocuments(String question) {
        SearchRequest searchRequest = SearchRequest
                .builder()
                .query(question)                    // User's question
                .topK(maxContextDocuments)          // Maximum documents to retrieve (5)
                .similarityThreshold(0.7)          // Minimum similarity score
                .build();

        return vectorStore.similaritySearch(searchRequest);
    }
}
```

### Vector Search Configuration

```java
SearchRequest searchRequest = SearchRequest
    .builder()
    .query(question)                    // Automatically embedded by Spring AI
    .topK(5)                           // Top 5 most similar documents
    .similarityThreshold(0.7)          // 70% similarity minimum
    .build();

List<Document> results = vectorStore.similaritySearch(searchRequest);
```

### Context Construction

Retrieved documents are formatted into context for the LLM:

```java
private String createContextFromDocuments(List<Document> documents) {
    return documents.stream()
            .map(doc -> {
                String content = doc.getText();
                Map<String, Object> metadata = doc.getMetadata();

                StringBuilder contextEntry = new StringBuilder();
                if (metadata.containsKey("source")) {
                    contextEntry.append("Source: ").append(metadata.get("source")).append("\n");
                }
                if (metadata.containsKey("chunk_index")) {
                    contextEntry.append("Section: ").append(metadata.get("chunk_index")).append("\n");
                }
                contextEntry.append(content).append("\n\n");

                return contextEntry.toString();
            })
            .collect(Collectors.joining("\n" + "=".repeat(50) + "\n"));
}
```

## Prompt Engineering

### Kotlin Expert Prompt Template

```java
private static final String KOTLIN_EXPERT_PROMPT = """
        You are a highly knowledgeable Kotlin expert and teacher. Your goal is to provide accurate,
        comprehensive, and educational answers about Kotlin programming.

        Based on the provided context from official Kotlin documentation, answer the user's question.

        Guidelines:
        1. Provide clear, accurate, and well-structured answers
        2. Include code examples when relevant
        3. Explain concepts in a teaching manner
        4. If the context doesn't contain enough information, say so clearly
        5. Focus specifically on Kotlin-related topics
        6. Structure your response in a clear, educational format

        Context from Kotlin Documentation:
        {context}

        User Question: {question}

        Answer:
        """;
```

### Response Generation

```java
private String generateAnswer(String question, String context) {
    PromptTemplate promptTemplate = new PromptTemplate(KOTLIN_EXPERT_PROMPT);
    Map<String, Object> promptVariables = Map.of(
            "context", context,
            "question", question);

    Prompt prompt = promptTemplate.create(promptVariables);
    ChatResponse response = chatModel.call(prompt);

    return response.getResult().getOutput().getText();
}
```

## Embedding Strategy

### Text Embedding Process

1. **Document Embedding**: When documents are added to vector store
   ```java
   vectorStore.add(documents);  // Spring AI automatically:
   // 1. Converts text to embeddings using OpenAI API
   // 2. Stores vectors in Qdrant with metadata
   ```

2. **Query Embedding**: When searching for similar content
   ```java
   vectorStore.similaritySearch(searchRequest);  // Spring AI automatically:
   // 1. Embeds the query using same model
   // 2. Performs cosine similarity search
   // 3. Returns ranked results
   ```

### Embedding Configuration

```properties
# Embedding Model Configuration
spring.ai.openai.embedding.options.model=text-embedding-3-small
spring.ai.openai.embedding.options.dimensions=1536  # Auto-configured

# Vector Store Configuration
spring.ai.vectorstore.qdrant.collection-name=kotlin-knowledge-base
app.chatbot.max-context-documents=5
```

## API Endpoints

### Ask Question

**POST** `/api/kotlin-chatbot/ask`

```json
{
    "question": "What is the difference between val and var in Kotlin?"
}
```

**Response:**
```json
{
    "answer": "In Kotlin, `val` and `var` are used to declare variables with different mutability characteristics...",
    "confidence": "HIGH",
    "contextDocumentCount": 3,
    "responseTimeMs": 1250,
    "successful": true
}
```

### Response Confidence Calculation

```java
private String calculateConfidence(List<Document> documents, String answer) {
    int docCount = documents.size();
    int answerLength = answer.length();

    if (docCount >= 4 && answerLength > 300) {
        return "HIGH";      // Multiple relevant docs, comprehensive answer
    } else if (docCount >= 2 && answerLength > 150) {
        return "MEDIUM";    // Some context, decent answer
    } else if (docCount >= 1 && answerLength > 50) {
        return "LOW";       // Limited context
    } else {
        return "VERY_LOW";  // Minimal or no relevant context
    }
}
```

## Setup & Configuration

### 1. API Keys Configuration

Create `keys.properties` in project root:

```properties
openai.api.key=sk-your-openai-api-key
qdrant.api.key=your-qdrant-api-key
```

### 2. Vector Database Setup

Update `application.properties` with your Qdrant cluster:

```properties
spring.ai.vectorstore.qdrant.host=your-cluster.gcp.cloud.qdrant.io
spring.ai.vectorstore.qdrant.port=6334
spring.ai.vectorstore.qdrant.api-key=${qdrant.api.key}
spring.ai.vectorstore.qdrant.use-tls=true
```

### 3. Run Application

```bash
./mvnw spring-boot:run
```

## Testing with Postman

### 1. Health Check
```
GET http://localhost:8080/api/kotlin-chatbot/health
```

### 2. Ask Question
```
POST http://localhost:8080/api/kotlin-chatbot/ask
Content-Type: application/json

{
    "question": "How do I create a data class in Kotlin?"
}
```

### 3. Test Vector Search Quality
```json
{
    "question": "What are coroutines in Kotlin and how do I use them?"
}
```

### 4. Test Edge Cases
```json
{
    "question": "How do I implement dependency injection in Android?"
}
```

## Key Spring AI Features Demonstrated

1. **Automatic Embedding Generation**: Spring AI handles OpenAI API calls for embeddings
2. **Vector Store Abstraction**: Consistent interface across different vector databases
3. **Document Processing**: Built-in PDF readers and text splitters
4. **Similarity Search**: Configurable similarity thresholds and result limits
5. **Prompt Templates**: Structured prompt engineering with variable substitution
6. **Error Handling**: Comprehensive exception handling for AI operations

This implementation showcases production-ready RAG patterns using Spring AI's powerful abstractions for vector operations and LLM integration.