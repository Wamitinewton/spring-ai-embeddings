package com.spring.kotlin_ai_chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.spring.kotlin_ai_chatbot.data.QuizSession;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class QuizSessionService {

    private static final Logger logger = LoggerFactory.getLogger(QuizSessionService.class);
    private static final String SESSION_KEY_PREFIX = "quiz:session:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(QuizSession.SESSION_TIMEOUT_MINUTES);

    private final RedisTemplate<String, Object> redisTemplate;

    public QuizSessionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Creates a new quiz session and stores it in Redis
     */
    public QuizSession createSession(String difficulty) {
        String sessionId = generateSessionId();
        QuizSession session = new QuizSession(sessionId, difficulty);
        
        String key = getSessionKey(sessionId);
        redisTemplate.opsForValue().set(key, session, SESSION_TTL);
        
        logger.info("Created new quiz session {} with difficulty: {}", sessionId, difficulty);
        return session;
    }

    /**
     * Retrieves a session from Redis
     */
    public Optional<QuizSession> getSession(String sessionId) {
        String key = getSessionKey(sessionId);
        Object sessionObj = redisTemplate.opsForValue().get(key);
        
        if (sessionObj instanceof QuizSession session) {
            // Check if session has expired
            if (session.isExpired()) {
                logger.info("Session {} has expired, removing from Redis", sessionId);
                deleteSession(sessionId);
                return Optional.empty();
            }
            
            logger.debug("Retrieved session {} from Redis", sessionId);
            return Optional.of(session);
        }
        
        logger.debug("Session {} not found in Redis", sessionId);
        return Optional.empty();
    }

    /**
     * Updates an existing session in Redis
     */
    public void updateSession(QuizSession session) {
        if (session == null || session.getSessionId() == null) {
            throw new IllegalArgumentException("Session and session ID cannot be null");
        }
        
        session.updateActivity();
        String key = getSessionKey(session.getSessionId());
        
        // If session is completed, store with longer TTL for final summary
        Duration ttl = session.isCompleted() ? Duration.ofHours(2) : SESSION_TTL;
        redisTemplate.opsForValue().set(key, session, ttl);
        
        logger.debug("Updated session {} in Redis", session.getSessionId());
    }

    /**
     * Deletes a session from Redis
     */
    public void deleteSession(String sessionId) {
        String key = getSessionKey(sessionId);
        Boolean deleted = redisTemplate.delete(key);
        
        if (Boolean.TRUE.equals(deleted)) {
            logger.info("Deleted session {} from Redis", sessionId);
        } else {
            logger.warn("Failed to delete session {} from Redis", sessionId);
        }
    }

    /**
     * Extends session TTL when user is active
     */
    public void extendSession(String sessionId) {
        String key = getSessionKey(sessionId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.expire(key, SESSION_TTL);
            logger.debug("Extended TTL for session {}", sessionId);
        }
    }


    public long getActiveSessionCount() {
        Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }

    public void cleanupExpiredSessions() {
        Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        int deletedCount = 0;
        for (String key : keys) {
            Object sessionObj = redisTemplate.opsForValue().get(key);
            if (sessionObj instanceof QuizSession session && session.isExpired()) {
                redisTemplate.delete(key);
                deletedCount++;
            }
        }

        if (deletedCount > 0) {
            logger.info("Cleaned up {} expired quiz sessions", deletedCount);
        }
    }

    /**
     * Checks if a session exists and is active
     */
    public boolean sessionExists(String sessionId) {
        return getSession(sessionId).isPresent();
    }

    /**
     * Gets session statistics for monitoring
     */
    public SessionStats getSessionStats() {
        Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return new SessionStats(0, 0, 0);
        }

        int total = 0;
        int active = 0;
        int completed = 0;

        for (String key : keys) {
            Object sessionObj = redisTemplate.opsForValue().get(key);
            if (sessionObj instanceof QuizSession session) {
                total++;
                if (session.isCompleted()) {
                    completed++;
                } else if (!session.isExpired()) {
                    active++;
                }
            }
        }

        return new SessionStats(total, active, completed);
    }

    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String getSessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    public record SessionStats(int total, int active, int completed) {}
}
