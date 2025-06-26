# Multi-Language Programming Assistant with Spring AI & RAG

A Spring Boot application that provides intelligent programming assistance across multiple languages using Retrieval-Augmented Generation (RAG) with Spring AI, OpenAI embeddings, and Qdrant vector database.

![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-blue)
![OpenAI](https://img.shields.io/badge/OpenAI-Embeddings-purple)
![Qdrant](https://img.shields.io/badge/Qdrant-Vector%20DB-red)
![Multi-Language](https://img.shields.io/badge/Languages-10+-green)

## 🌟 Features

### Multi-Language Programming Support
- **Kotlin**, **Java**, **Python**, **JavaScript**, **TypeScript**
- **C#**, **C++**, **Rust**, **Go**, **Swift**, and more
- Automatic language detection in documents
- Cross-language concept explanations

### Document Management
- **Automatic PDF Processing**: Place PDFs in `src/main/resources/` and they'll be automatically processed
- **Flexible Document Loading**: Control when embeddings are generated with configuration flags
- **Language-Aware Chunking**: Metadata enhancement with language detection
- **Batch Processing**: Efficient handling of multiple documents

### Intelligent Features
- **RAG-Enhanced Responses**: Combines document knowledge with AI reasoning
- **Interactive Quiz System**: Redis-powered quiz sessions with multiple difficulties
- **Admin Controls**: Document reloading and system status endpoints
- **Comprehensive Logging**: Detailed processing and performance metrics

## 🚀 Quick Start

### 1. Configuration Setup

Create `keys.properties` in project root:
```properties
open.ai.key=sk-your-openai-api-key
qdrant.host.url=your-cluster.gcp.cloud.qdrant.io
qdrant.api.key=your-qdrant-api-key
redis.host.url=your-redis-host
redis.port=6380
redis.password=your-redis-password
```

### 2. Document Management

#### Adding Documents
Simply place your programming documentation PDFs in `src/main/resources/`:
```
src/main/resources/
├── kotlin-reference.pdf
├── java-tutorial.pdf
├── python-guide.pdf
├── javascript-handbook.pdf
└── typescript-docs.pdf
```

#### Controlling Embedding Generation
Configure in `application.properties`:
```properties
# Enable/disable automatic document loading on startup
app.initialization.auto-load-pdfs=true

# Enable/disable embedding generation (important for performance)
app.initialization.enable-embedding-generation=true
```

**Important**: Set `enable-embedding-generation=false` to skip the time-consuming embedding process during development or when using existing embeddings.

### 3. Run the Application

```bash
./mvnw spring-boot:run
```

The application will:
1. ✅ Connect to external services (Qdrant, Redis, OpenAI)
2. 📚 Scan for PDF documents (if auto-load is enabled)
3. 🔢 Generate embeddings (if embedding generation is enabled)
4. 🚀 Start the web server on port 8080

## 📚 Document Processing Flow

### Automatic Language Detection
The system automatically detects programming languages in documents using:
- **Filename Analysis**: `kotlin-guide.pdf` → Kotlin
- **Content Patterns**: Code syntax and keywords
- **Metadata Enhancement**: Each chunk tagged with detected language

### Enhanced Metadata
Every document chunk includes:
```json
{
  "source": "python-advanced.pdf",
  "primary_language": "python",
  "detected_language": "python",
  "document_category": "advanced",
  "chunk_index": 15,
  "has_code_examples": true,
  "content_type": "programming_documentation"
}
```

## 🔧 API Endpoints

### Programming Questions
```http
POST /api/programming-assistant/ask
Content-Type: application/json

{
  "question": "How do I implement async/await in Python vs JavaScript?"
}
```

### Quiz System
```http
# Start a quiz session
POST /api/programming-assistant/quiz/start
{
  "difficulty": "intermediate"
}

# Submit an answer
POST /api/programming-assistant/quiz/answer
{
  "sessionId": "abc123",
  "answer": "B"
}
```

### Admin Controls
```http
# Reload all documents (useful when adding new PDFs)
POST /api/programming-assistant/admin/reload-documents

# Check initialization status
GET /api/programming-assistant/admin/initialization-status

# Get knowledge base statistics
GET /api/programming-assistant/admin/knowledge-base-stats
```

### Information Endpoints
```http
# Get assistant information
GET /api/programming-assistant/info

# Get supported languages
GET /api/programming-assistant/supported-languages

# Get quiz information
GET /api/programming-assistant/quiz/info
```

## ⚙️ Configuration Options

### Document Processing
```properties
# PDF chunk size (tokens)
app.pdf.processing.chunk-size=800

# Overlap between chunks (tokens)
app.pdf.processing.chunk-overlap=100

# Batch size for vector store operations
app.pdf.processing.batch-size=50
```

### Chatbot Behavior
```properties
# Maximum documents to use for context
app.chatbot.max-context-documents=5

# Similarity threshold for document retrieval
app.search.multi-language-threshold=0.55
```

### Initialization Control
```properties
# Auto-load PDFs on startup
app.initialization.auto-load-pdfs=false

# Enable embedding generation (set to false for faster startup)
app.initialization.enable-embedding-generation=false
```

## 🏗️ Architecture

### RAG Pipeline
```
User Query → Language Detection → Vector Search → Context Retrieval → LLM Generation → Response
     ↓              ↓                ↓              ↓                ↓            ↓
Multi-lang      Qdrant DB      Document        Enhanced         GPT-4o      Expert
Question        Similarity     Chunks          Prompt           Multi-lang   Answer
Processing      Search         Retrieved       Template         Context
```

### Enhanced Prompt Engineering
The system uses sophisticated prompts that:
- Handle multiple programming languages
- Provide cross-language comparisons
- Include relevant documentation context
- Maintain consistent formatting across languages

## 📖 Usage Examples

### Adding New Documents
1. **Place PDF in resources**: `src/main/resources/rust-programming.pdf`
2. **Restart application** (if auto-load is enabled) or call reload endpoint
3. **Document is automatically processed** with language detection and metadata enhancement

### Working Without Embeddings
Set `app.initialization.enable-embedding-generation=false` to:
- ✅ Skip time-consuming embedding generation
- ✅ Use existing embeddings if available
- ✅ Fall back to general programming knowledge
- ✅ Faster startup times during development

### Admin Operations
```bash
# Reload documents after adding new PDFs
curl -X POST http://localhost:8080/api/programming-assistant/admin/reload-documents

# Check if embeddings are enabled
curl http://localhost:8080/api/programming-assistant/admin/initialization-status
```

## 🔍 Monitoring and Logging

### Detailed Processing Logs
```
🔍 Scanning for PDF documents in resources folder...
📄 Found 5 PDF documents to process
✅ Successfully processed: kotlin-reference.pdf (127 chunks)
✅ Successfully processed: python-guide.pdf (89 chunks)
📊 Bulk processing completed in 45123ms. Success: 5, Failed: 0, Total chunks: 445
🚀 Multi-Language Programming Assistant is ready!
```

### Performance Metrics
- Document processing times
- Chunk creation statistics
- Vector search performance
- Response generation timing

## 🚀 Production Deployment

### Environment Variables
```bash
export OPENAI_API_KEY=your-key
export QDRANT_HOST=your-cluster.gcp.cloud.qdrant.io
export QDRANT_API_KEY=your-key
export REDIS_HOST=your-redis-host
export REDIS_PASSWORD=your-password
```

### Performance Tuning
```properties
# Optimize for production
app.pdf.processing.batch-size=100
app.chatbot.max-context-documents=7
app.initialization.auto-load-pdfs=true
app.initialization.enable-embedding-generation=true
```

## 🤝 Contributing

1. Add support for new programming languages by updating `LANGUAGE_PATTERNS`
2. Enhance document categorization in `PdfProcessingService`
3. Improve prompt templates for better multi-language responses
4. Add new admin endpoints for system management

## 📝 License

This project demonstrates Spring AI capabilities for multi-language programming assistance with production-ready features including document management, caching, and administrative controls.