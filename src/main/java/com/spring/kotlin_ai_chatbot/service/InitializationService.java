package com.spring.kotlin_ai_chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class InitializationService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(InitializationService.class);

    private final PdfProcessingService pdfProcessingService;
    private final boolean autoLoadPdfs;
    private final boolean enableEmbeddingGeneration;
    private final boolean isProductionProfile;

    public InitializationService(PdfProcessingService pdfProcessingService,
                                @Value("${app.initialization.auto-load-pdfs:false}") boolean autoLoadPdfs,
                                @Value("${app.initialization.enable-embedding-generation:false}") boolean enableEmbeddingGeneration,
                                @Value("${spring.profiles.active:dev}") String activeProfile) {
        this.pdfProcessingService = pdfProcessingService;
        this.autoLoadPdfs = autoLoadPdfs;
        this.enableEmbeddingGeneration = enableEmbeddingGeneration;
        this.isProductionProfile = "prod".equals(activeProfile);
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("🚀 Initializing Multi-Language Programming Assistant");
        logger.info("📊 Environment: {}", isProductionProfile ? "PRODUCTION" : "DEVELOPMENT");
        logger.info("📄 Auto-load PDFs: {}", autoLoadPdfs);
        logger.info("🤖 Enable Embedding Generation: {}", enableEmbeddingGeneration);
        
        if (isProductionProfile) {
            logger.info("🔒 PRODUCTION MODE: Initialization services are DISABLED");
            logger.info("✅ Application ready - using existing embeddings and vector store");
            return;
        }
        
        if (!enableEmbeddingGeneration) {
            logger.info("⏸️ Embedding generation is disabled. The application will use existing embeddings or work without document context.");
            logger.info("💡 To enable embedding generation, set app.initialization.enable-embedding-generation=true");
            return;
        }

        if (autoLoadPdfs) {
            loadAllDocuments();
        } else {
            logger.info("📋 Auto-loading of PDFs is disabled. Documents can be loaded manually via the admin endpoints.");
            logger.info("💡 To enable auto-loading, set app.initialization.auto-load-pdfs=true");
        }
    }

    /**
     * Load all documents - only available in non-production environments
     */
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "prod", matchIfMissing = false)
    public void loadAllDocuments() {
        if (isProductionProfile) {
            logger.warn("🚫 Document loading is disabled in production environment");
            return;
        }
        
        try {
            logger.info("🔍 Scanning for PDF documents in resources folder...");
            
            PdfProcessingService.BulkProcessingResult result = pdfProcessingService.processAllPdfDocuments();
            
            if (result.getSuccessfulCount() == 0 && result.getFailedCount() == 0) {
                logger.warn("📁 No PDF documents found in resources folder.");
                logger.info("💡 Place your programming documentation PDFs in src/main/resources/ to automatically load them");
                return;
            }

            logProcessingResults(result);
            
        } catch (Exception e) {
            logger.error("❌ Error during document initialization", e);
        }
    }

    /**
     * Reload all documents - only available in non-production environments
     */
    public PdfProcessingService.BulkProcessingResult reloadAllDocuments() {
        if (isProductionProfile) {
            logger.warn("🚫 Document reloading is disabled in production environment");
            throw new IllegalStateException("Document reloading is disabled in production environment");
        }
        
        logger.info("🔄 Manually reloading all PDF documents...");
        
        if (!enableEmbeddingGeneration) {
            logger.warn("⚠️ Cannot reload documents - embedding generation is disabled");
            throw new IllegalStateException("Embedding generation is disabled. Enable it to reload documents.");
        }
        
        PdfProcessingService.BulkProcessingResult result = pdfProcessingService.processAllPdfDocuments();
        logProcessingResults(result);
        return result;
    }

    private void logProcessingResults(PdfProcessingService.BulkProcessingResult result) {
        logger.info("📊 === DOCUMENT PROCESSING RESULTS ===");
        logger.info("⏱️ Total Processing Time: {}ms", result.getTotalProcessingTimeMs());
        logger.info("✅ Successfully Processed: {} documents", result.getSuccessfulCount());
        logger.info("❌ Failed: {} documents", result.getFailedCount());
        logger.info("📄 Total Chunks Created: {}", result.getTotalChunks());
        
        if (result.getSuccessfulCount() > 0) {
            logger.info("📋 === SUCCESSFUL DOCUMENTS ===");
            result.getSuccessfulDocuments().forEach((filename, processingResult) -> {
                logger.info("✅ {} - {} pages, {} chunks, {}ms", 
                           filename, 
                           processingResult.getDocumentsProcessed(),
                           processingResult.getChunksCreated(),
                           processingResult.getProcessingTimeMs());
            });
        }
        
        if (result.hasFailures()) {
            logger.warn("⚠️ === FAILED DOCUMENTS ===");
            result.getFailedDocuments().forEach((filename, error) -> {
                logger.error("❌ {} - {}", filename, error);
            });
        }
        
        if (result.isCompletelySuccessful()) {
            logger.info("🎉 All documents processed successfully! The knowledge base is ready.");
        } else if (result.getSuccessfulCount() > 0) {
            logger.info("⚠️ Partial success. Some documents were processed, but {} failed.", result.getFailedCount());
        } else {
            logger.error("💥 All document processing failed. Check the error messages above.");
        }
        
        logger.info("🤖 Multi-Language Programming Assistant is ready to help with:");
        if (result.getSuccessfulCount() > 0) {
            logger.info("   • Document-enhanced responses using loaded PDFs");
        }
        logger.info("   • General programming knowledge across multiple languages");
        logger.info("   • Interactive quizzes and random programming facts");
    }

    /**
     * Get initialization status
     */
    public InitializationStatus getStatus() {
        return new InitializationStatus(
            autoLoadPdfs && !isProductionProfile,
            enableEmbeddingGeneration && !isProductionProfile,
            (!isProductionProfile && enableEmbeddingGeneration && autoLoadPdfs) || isProductionProfile,
            isProductionProfile
        );
    }

    public record InitializationStatus(
        boolean autoLoadEnabled,
        boolean embeddingGenerationEnabled,
        boolean fullyInitialized,
        boolean productionMode
    ) {}
}