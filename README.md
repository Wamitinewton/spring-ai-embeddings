# 🔧 API Endpoints

## 📱 Application Info
```http
# Get application information and available features
GET /api/info

# Check application health status
GET /api/health
```

## 🤖 Chatbot Endpoints
All chatbot-related functionality for programming assistance.

### Ask Programming Questions
```http
POST /api/chatbot/ask
Content-Type: application/json

{
  "question": "How do I implement async/await in Python vs JavaScript?"
}

# Alternative GET method
GET /api/chatbot/ask?question=How do decorators work in Python?
```

### Random Programming Facts
```http
# Get a random fact about any supported language
GET /api/chatbot/random-fact

# Get a fact about a specific language
GET /api/chatbot/random-fact?language=python
GET /api/chatbot/random-fact?language=rust
GET /api/chatbot/random-fact?language=kotlin
```

**Example Response:**
```json
{
  "fact": "Python was named after the British comedy group Monty Python, not the snake. Guido van Rossum was reading Monty Python scripts while implementing Python in 1989.",
  "language": "python",
  "category": "history",
  "source": "Documentation + AI Analysis",
  "responseTimeMs": 1250,
  "successful": true
}
```

### Admin Operations
```http
# Reload all documents (useful when adding new PDFs)
POST /api/chatbot/admin/reload-documents

# Check initialization status
GET /api/chatbot/admin/initialization-status

# Get knowledge base statistics
GET /api/chatbot/admin/knowledge-base-stats
```

### Information Endpoints
```http
# Get chatbot information
GET /api/chatbot/info

# Get supported languages for chatbot
GET /api/chatbot/supported-languages
```

## 🎯 Quiz Endpoints
Multi-language programming quiz system with Redis session management.

### Start Quiz Session
```http
POST /api/quiz/start
Content-Type: application/json

{
  "language": "python",
  "difficulty": "intermediate"
}

# Alternative GET method (defaults to Python if language not specified)
GET /api/quiz/start?language=kotlin&difficulty=advanced
GET /api/quiz/start?difficulty=beginner  # Uses Python by default
```

**Supported Languages:**
- `kotlin`, `java`, `python`, `javascript`, `typescript`
- `csharp`, `cpp`, `rust`, `go`, `swift`

**Supported Difficulties:**
- `beginner`, `intermediate`, `advanced`

**Example Response:**
```json
{
  "sessionId": "abc123def456",
  "language": "python",
  "languageDisplayName": "Python",
  "difficulty": "intermediate",
  "currentQuestion": {
    "questionNumber": 1,
    "question": "Which method is used to add an element to the end of a list in Python?",
    "codeSnippet": "my_list = [1, 2, 3]\n# Add element 4 to the end",
    "options": [
      {"letter": "A", "text": "my_list.add(4)"},
      {"letter": "B", "text": "my_list.append(4)"},
      {"letter": "C", "text": "my_list.insert(4)"},
      {"letter": "D", "text": "my_list.push(4)"}
    ]
  },
  "totalQuestions": 5,
  "currentQuestionNumber": 1,
  "score": 0,
  "successful": true
}
```

### Submit Quiz Answer
```http
POST /api/quiz/answer
Content-Type: application/json

{
  "sessionId": "abc123def456",
  "answer": "B"
}
```

**Example Response:**
```json
{
  "correct": true,
  "message": "🎉 Correct! The append() method adds an element to the end of a list in Python.",
  "correctAnswer": "B",
  "explanation": "The append() method is used to add a single element to the end of a list. Unlike extend(), it adds the entire object as a single element.",
  "currentScore": 1,
  "hasNextQuestion": true,
  "nextQuestion": {
    "questionNumber": 2,
    "question": "What is the output of len([1, 2, [3, 4]])?",
    "options": [...]
  },
  "successful": true
}
```

### Quiz Session Management
```http
# Get session status
GET /api/quiz/session/{sessionId}

# Extend session timeout (keep alive)
POST /api/quiz/session/{sessionId}/extend

# Get quiz statistics
GET /api/quiz/stats
```

### Quiz Information
```http
# Get quiz game information
GET /api/quiz/info

# Get supported languages for quizzes
GET /api/quiz/supported-languages
```

**Example Quiz Info Response:**
```json
{
  "name": "Multi-Language Programming Quiz - 5 Questions Challenge",
  "description": "Test your programming knowledge across multiple languages with AI-generated questions.",
  "difficulties": ["beginner", "intermediate", "advanced"],
  "supportedLanguages": ["kotlin", "java", "python", "javascript", "typescript", "csharp", "cpp", "rust", "go", "swift"],
  "defaultLanguage": "python",
  "instructions": "Answer 5 multiple-choice questions to complete a quiz session. Get immediate feedback and explanations!"
}
```

## 🎯 Quiz Session Completion

When you complete all 5 questions, you'll receive a summary:

```json
{
  "correct": true,
  "message": "🎉 Correct! Final explanation here.",
  "currentScore": 4,
  "hasNextQuestion": false,
  "sessionSummary": {
    "sessionId": "abc123def456",
    "language": "python",
    "languageDisplayName": "Python",
    "difficulty": "intermediate",
    "totalQuestions": 5,
    "correctAnswers": 4,
    "score": 80,
    "performance": "Excellent",
    "message": "🎉 Outstanding! You have a strong grasp of Python concepts!",
    "completionTimeMs": 180000
  },
  "successful": true
}
```

## 🌟 Key Features

### Multi-Language Support
- **Chatbot**: Ask questions about any programming language
- **Quiz**: Take language-specific quizzes with tailored questions
- **Random Facts**: Discover interesting trivia about different languages

### Intelligent Question Generation
- AI-powered questions based on real documentation
- Language-specific topics and difficulty levels
- Code examples and conceptual questions
- Immediate feedback with explanations

### Session Management
- Redis-powered session persistence
- 30-minute session timeout with keep-alive
- Automatic cleanup of expired sessions
- Session statistics and monitoring

### Random Facts Engine
- Context-aware fact generation using embeddings
- Categories: history, technical, features, trivia, comparison
- Combines documentation knowledge with AI insights
- Surprising and lesser-known programming trivia

## 🔄 Workflow Examples

### Complete Quiz Workflow
1. `GET /api/quiz/start?language=kotlin&difficulty=beginner`
2. `POST /api/quiz/answer` (repeat 5 times)
3. Receive final summary with score and performance

### Chatbot + Facts Workflow
1. `POST /api/chatbot/ask` - Ask a programming question
2. `GET /api/chatbot/random-fact?language=python` - Learn something new
3. `POST /api/chatbot/ask` - Ask a follow-up question

### Admin Maintenance
1. `POST /api/chatbot/admin/reload-documents` - Update knowledge base
2. `GET /api/chatbot/admin/knowledge-base-stats` - Check system status
3. `GET /api/quiz/stats` - Monitor quiz usage