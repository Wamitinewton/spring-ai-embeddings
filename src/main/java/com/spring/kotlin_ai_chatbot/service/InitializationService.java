package com.spring.kotlin_ai_chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class InitializationService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(InitializationService.class);

    private final PdfProcessingService pdfProcessingService;
    private final ResourceLoader resourceLoader;
    private final String defaultPdfPath;
    private final boolean autoLoadDefaultPdf;

    public InitializationService(PdfProcessingService pdfProcessingService,
                                ResourceLoader resourceLoader,
                                @Value("${app.initialization.default-pdf-path:classpath:kotlin-docs.pdf}") String defaultPdfPath,
                                @Value("${app.initialization.auto-load-default-pdf:false}") boolean autoLoadDefaultPdf) {
        this.pdfProcessingService = pdfProcessingService;
        this.resourceLoader = resourceLoader;
        this.defaultPdfPath = defaultPdfPath;
        this.autoLoadDefaultPdf = autoLoadDefaultPdf;
    }

    @Override
    public void run(String... args) throws Exception {
        if (autoLoadDefaultPdf) {
            loadDefaultKotlinDocumentation();
        } else {
            logger.info("Auto-loading of default PDF is disabled. Use the upload endpoint to add Kotlin documentation.");
            logger.info("To enable auto-loading, set app.initialization.auto-load-default-pdf=true");
        }
    }

    private void loadDefaultKotlinDocumentation() {
        try {
            logger.info("Attempting to load default Kotlin documentation from: {}", defaultPdfPath);
            
            Resource pdfResource = resourceLoader.getResource(defaultPdfPath);
            
            if (!pdfResource.exists()) {
                logger.warn("Default Kotlin PDF not found at: {}. Please upload a Kotlin documentation PDF manually.", defaultPdfPath);
                return;
            }

            logger.info("Found default Kotlin PDF. Starting processing...");
            PdfProcessingService.ProcessingResult result = pdfProcessingService.processPdfResource(pdfResource);
            
            if (result.isSuccessful()) {
                logger.info("Successfully loaded default Kotlin documentation! " +
                           "Documents: {}, Chunks: {}, Time: {}ms", 
                           result.getDocumentsProcessed(), 
                           result.getChunksCreated(), 
                           result.getProcessingTimeMs());
            } else {
                logger.error("Failed to process default Kotlin documentation: {}", result.getErrorMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error loading default Kotlin documentation", e);
        }
    }

    public void reloadDefaultDocumentation() {
        logger.info("Manually reloading default Kotlin documentation...");
        loadDefaultKotlinDocumentation();
    }
}
