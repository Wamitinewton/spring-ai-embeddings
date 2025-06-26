package com.spring.kotlin_ai_chatbot.scheduler;

import com.spring.kotlin_ai_chatbot.service.QuizSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
public class SessionCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    private final QuizSessionService sessionService;

    public SessionCleanupScheduler(QuizSessionService sessionService) {
        this.sessionService = sessionService;
    }


    @Scheduled(fixedRate = 900000) 
    public void cleanupExpiredSessions() {
        try {
            logger.debug("Starting scheduled cleanup of expired quiz sessions");
            
            long activeBefore = sessionService.getActiveSessionCount();
            sessionService.cleanupExpiredSessions();
            long activeAfter = sessionService.getActiveSessionCount();
            
            if (activeBefore != activeAfter) {
                logger.info("Session cleanup completed. Active sessions: {} -> {}", activeBefore, activeAfter);
            }
            
        } catch (Exception e) {
            logger.error("Error during scheduled session cleanup: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void logDailyStats() {
        try {
            QuizSessionService.SessionStats stats = sessionService.getSessionStats();
            logger.info("Daily quiz session stats - Total: {}, Active: {}, Completed: {}", 
                       stats.total(), stats.active(), stats.completed());
        } catch (Exception e) {
            logger.error("Error logging daily stats: {}", e.getMessage(), e);
        }
    }
}
