package com.spring.kotlin_ai_chatbot.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Service
public class PdfProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(PdfProcessingService.class);

    private final VectorStore vectorStore;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int batchSize;

    public PdfProcessingService(VectorStore vectorStore,
            @Value("${app.pdf.processing.chunk-size:800}") int chunkSize,
            @Value("${app.pdf.processing.chunk-overlap:100}") int chunkOverlap,
            @Value("${app.pdf.processing.batch-size:50}") int batchSize) {
        this.vectorStore = vectorStore;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.batchSize = batchSize;
    }

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

            // Enhance chunks with metadata
            enhanceChunksWithMetadata(chunks, pdfResource.getFilename());

            // Process chunks in batches to avoid overwhelming the vector store
            int totalChunks = chunks.size();
            int processedChunks = 0;

            for (int i = 0; i < totalChunks; i += batchSize) {
                int endIndex = Math.min(i + batchSize, totalChunks);
                List<Document> batch = chunks.subList(i, endIndex);

                logger.info("Processing batch {}-{} of {} chunks", i + 1, endIndex, totalChunks);
                vectorStore.add(batch);

                processedChunks += batch.size();
                logger.debug("Processed {} chunks so far", processedChunks);
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

    private void enhanceChunksWithMetadata(List<Document> chunks, String filename) {
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = chunk.getMetadata();

            metadata.put("source", filename);
            metadata.put("chunk_index", i);
            metadata.put("content_type", "kotlin_documentation");
            metadata.put("language", "kotlin");

            String content = chunk.getText();
            metadata.put("content_preview", content.length() > 100 ? 
                        content.substring(0, 100) + "..." : content);
        }
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

}
