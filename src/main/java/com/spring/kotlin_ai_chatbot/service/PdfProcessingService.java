package com.spring.kotlin_ai_chatbot.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Service
public class PdfProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(PdfProcessingService.class);

    private final VectorStore vectorStore;
    private final ResourceLoader resourceLoader;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int batchSize;

    private static final Map<String, Pattern> LANGUAGE_PATTERNS = Map.of(
        "kotlin", Pattern.compile("\\b(fun|val|var|class|object|interface|when|sealed|data class|suspend)\\b", Pattern.CASE_INSENSITIVE),
        "java", Pattern.compile("\\b(public|private|protected|static|final|class|interface|extends|implements|package)\\b", Pattern.CASE_INSENSITIVE),
        "python", Pattern.compile("\\b(def|class|import|from|if __name__|self|print|lambda|yield)\\b", Pattern.CASE_INSENSITIVE),
        "javascript", Pattern.compile("\\b(function|const|let|var|=>|async|await|console\\.log|require|module\\.exports)\\b", Pattern.CASE_INSENSITIVE),
        "typescript", Pattern.compile("\\b(interface|type|enum|namespace|declare|readonly|public|private|protected)\\b", Pattern.CASE_INSENSITIVE),
        "csharp", Pattern.compile("\\b(public|private|protected|static|readonly|namespace|using|class|interface|struct)\\b", Pattern.CASE_INSENSITIVE),
        "cpp", Pattern.compile("\\b(#include|namespace|std::|public:|private:|protected:|class|struct|template|virtual)\\b", Pattern.CASE_INSENSITIVE),
        "rust", Pattern.compile("\\b(fn|let|mut|pub|struct|enum|impl|trait|use|mod|match)\\b", Pattern.CASE_INSENSITIVE),
        "go", Pattern.compile("\\b(func|package|import|var|const|type|struct|interface|go|defer|chan)\\b", Pattern.CASE_INSENSITIVE),
        "swift", Pattern.compile("\\b(func|var|let|class|struct|enum|protocol|extension|import|public|private)\\b", Pattern.CASE_INSENSITIVE)
    );

    public PdfProcessingService(VectorStore vectorStore,
            ResourceLoader resourceLoader,
            @Value("${app.pdf.processing.chunk-size:800}") int chunkSize,
            @Value("${app.pdf.processing.chunk-overlap:100}") int chunkOverlap,
            @Value("${app.pdf.processing.batch-size:50}") int batchSize) {
        this.vectorStore = vectorStore;
        this.resourceLoader = resourceLoader;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.batchSize = batchSize;
    }

    /**
     * Processes all PDF documents found in the resources folder
     */
    public BulkProcessingResult processAllPdfDocuments() {
        long startTime = System.currentTimeMillis();
        BulkProcessingResult result = new BulkProcessingResult();

        try {
            // Scan for PDF files in resources folder
            Resource[] pdfResources = findAllPdfResources();
            logger.info("Found {} PDF documents to process", pdfResources.length);

            if (pdfResources.length == 0) {
                logger.warn("No PDF documents found in resources folder");
                return result;
            }

            for (Resource pdfResource : pdfResources) {
                try {
                    logger.info("Processing document: {}", pdfResource.getFilename());
                    ProcessingResult docResult = processPdfResource(pdfResource);
                    
                    if (docResult.isSuccessful()) {
                        result.addSuccessfulDocument(pdfResource.getFilename(), docResult);
                        logger.info("✅ Successfully processed: {} ({} chunks)", 
                                   pdfResource.getFilename(), docResult.getChunksCreated());
                    } else {
                        result.addFailedDocument(pdfResource.getFilename(), docResult.getErrorMessage());
                        logger.error("❌ Failed to process: {} - {}", 
                                    pdfResource.getFilename(), docResult.getErrorMessage());
                    }
                } catch (Exception e) {
                    result.addFailedDocument(pdfResource.getFilename(), e.getMessage());
                    logger.error("❌ Exception processing: {} - {}", pdfResource.getFilename(), e.getMessage(), e);
                }
            }

            long totalTime = System.currentTimeMillis() - startTime;
            result.setTotalProcessingTimeMs(totalTime);
            
            logger.info("📊 Bulk processing completed in {}ms. Success: {}, Failed: {}, Total chunks: {}", 
                       totalTime, result.getSuccessfulCount(), result.getFailedCount(), result.getTotalChunks());

        } catch (Exception e) {
            logger.error("Error during bulk PDF processing", e);
            result.addFailedDocument("BULK_PROCESSING", e.getMessage());
        }

        return result;
    }

    /**
     * Processes a single PDF resource
     */
    public ProcessingResult processPdfResource(Resource pdfResource) {
        long startTime = System.currentTimeMillis();

        try {
            logger.info("Starting PDF processing for resource: {}", pdfResource.getFilename());

            // Read the PDF
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
            List<Document> documents = pdfReader.get();
            logger.info("Read {} pages from PDF", documents.size());

            // Split the documents into smaller chunks
            TokenTextSplitter textSplitter = new TokenTextSplitter(chunkSize, chunkOverlap, 5, 10000, true);
            List<Document> chunks = textSplitter.apply(documents);
            logger.info("Created {} chunks from PDF content", chunks.size());

            enhanceChunksWithMetadata(chunks, pdfResource.getFilename());

            int totalChunks = chunks.size();
            int processedChunks = 0;

            for (int i = 0; i < totalChunks; i += batchSize) {
                int endIndex = Math.min(i + batchSize, totalChunks);
                List<Document> batch = chunks.subList(i, endIndex);

                logger.debug("Processing batch {}-{} of {} chunks", i + 1, endIndex, totalChunks);
                vectorStore.add(batch);
                processedChunks += batch.size();
            }

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Successfully processed PDF. Total chunks: {}, Processing time: {}ms", 
                       processedChunks, processingTime);

            return new ProcessingResult(true, documents.size(), processedChunks, processingTime, null);
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.error("Error processing PDF: {}", e.getMessage(), e);
            return new ProcessingResult(false, 0, 0, processingTime, e.getMessage());
        }
    }

    /**
     * Finds all PDF resources in the classpath
     */
    private Resource[] findAllPdfResources() throws IOException {
        if (resourceLoader instanceof ResourcePatternResolver resolver) {
            return resolver.getResources("classpath*:*.pdf");
        } else {
            logger.warn("ResourceLoader does not support pattern resolution, falling back to empty array");
            return new Resource[0];
        }
    }

    /**
     * Enhances document chunks with comprehensive metadata
     */
    private void enhanceChunksWithMetadata(List<Document> chunks, String filename) {
        String detectedLanguage = detectPrimaryLanguageFromFilename(filename);
        
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = chunk.getMetadata();
            String content = chunk.getText();

            // Basic metadata
            metadata.put("source", filename);
            metadata.put("chunk_index", i);
            metadata.put("total_chunks", chunks.size());
            
            // Language detection
            String contentLanguage = detectLanguageFromContent(content);
            metadata.put("primary_language", detectedLanguage);
            metadata.put("detected_language", contentLanguage);
            metadata.put("content_type", "programming_documentation");
            
            // Content analysis
            metadata.put("content_length", content.length());
            metadata.put("has_code_examples", containsCodeExamples(content));
            metadata.put("content_preview", content.length() > 150 ? 
                        content.substring(0, 150) + "..." : content);
                        
            // Document categorization
            metadata.put("document_category", categorizeDocument(filename, content));
            
            logger.debug("Enhanced chunk {} with language: {} (detected: {})", 
                        i, detectedLanguage, contentLanguage);
        }
    }

    /**
     * Detects programming language from filename
     */
    private String detectPrimaryLanguageFromFilename(String filename) {
        if (filename == null) return "general";
        
        String lower = filename.toLowerCase();
        
        // Check for explicit language mentions in filename
        for (String language : LANGUAGE_PATTERNS.keySet()) {
            if (lower.contains(language)) {
                return language;
            }
        }
        
        if (lower.contains("android") || lower.contains("kotlin")) return "kotlin";
        if (lower.contains("spring") || lower.contains("java")) return "java";
        if (lower.contains("react") || lower.contains("node")) return "javascript";
        if (lower.contains("python") || lower.contains("django")) return "python";
        if (lower.contains("dotnet") || lower.contains("csharp")) return "csharp";
        if (lower.contains("cpp") || lower.contains("c++")) return "cpp";
        if (lower.contains("typescript") || lower.contains("angular")) return "typescript";
        if (lower.contains("rust")) return "rust";
        if (lower.contains("golang") || lower.contains("go-")) return "go";
        if (lower.contains("swift") || lower.contains("ios")) return "swift";
        
        return "general";
    }

    private String detectLanguageFromContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "general";
        }

        Map<String, Integer> languageScores = new java.util.HashMap<>();
        
        for (Map.Entry<String, Pattern> entry : LANGUAGE_PATTERNS.entrySet()) {
            String language = entry.getKey();
            Pattern pattern = entry.getValue();
            
            java.util.regex.Matcher matcher = pattern.matcher(content);
            int matches = 0;
            while (matcher.find()) {
                matches++;
            }
            languageScores.put(language, matches);
        }

        return languageScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .orElse("general");
    }


    private boolean containsCodeExamples(String content) {
        return content.contains("```") || 
               content.contains("    ") || 
               content.matches(".*\\{[\\s\\S]*\\}.*") ||
               content.matches(".*\\([\\s\\S]*\\)\\s*\\{.*") || 
               content.contains("public static void main") ||
               content.contains("function ") ||
               content.contains("def ") ||
               content.contains("fun ");
    }

    /**
     * Categorizes document based on filename and content
     */
    private String categorizeDocument(String filename, String content) {
        if (filename == null) return "unknown";
        
        String lower = filename.toLowerCase();
        
        if (lower.contains("tutorial") || lower.contains("guide")) return "tutorial";
        if (lower.contains("reference") || lower.contains("api")) return "reference";
        if (lower.contains("getting") && lower.contains("started")) return "getting_started";
        if (lower.contains("advanced") || lower.contains("expert")) return "advanced";
        if (lower.contains("cookbook") || lower.contains("examples")) return "examples";
        if (lower.contains("best") && lower.contains("practices")) return "best_practices";
        
        if (content.toLowerCase().contains("introduction") || 
            content.toLowerCase().contains("getting started")) return "getting_started";
        if (content.toLowerCase().contains("advanced") || 
            content.toLowerCase().contains("expert")) return "advanced";
            
        return "general";
    }

    @Getter
    @AllArgsConstructor
    public static class ProcessingResult {
        private final boolean successful;
        private final int documentsProcessed;
        private final int chunksCreated;
        private final long processingTimeMs;
        private final String errorMessage;
    }

    @Getter
    public static class BulkProcessingResult {
        private final Map<String, ProcessingResult> successfulDocuments = new java.util.HashMap<>();
        private final Map<String, String> failedDocuments = new java.util.HashMap<>();
        private long totalProcessingTimeMs;

        public void addSuccessfulDocument(String filename, ProcessingResult result) {
            successfulDocuments.put(filename, result);
        }

        public void addFailedDocument(String filename, String error) {
            failedDocuments.put(filename, error);
        }

        public void setTotalProcessingTimeMs(long timeMs) {
            this.totalProcessingTimeMs = timeMs;
        }

        public int getSuccessfulCount() {
            return successfulDocuments.size();
        }

        public int getFailedCount() {
            return failedDocuments.size();
        }

        public int getTotalChunks() {
            return successfulDocuments.values().stream()
                    .mapToInt(ProcessingResult::getChunksCreated)
                    .sum();
        }

        public boolean hasFailures() {
            return !failedDocuments.isEmpty();
        }

        public boolean isCompletelySuccessful() {
            return failedDocuments.isEmpty() && !successfulDocuments.isEmpty();
        }
    }
}