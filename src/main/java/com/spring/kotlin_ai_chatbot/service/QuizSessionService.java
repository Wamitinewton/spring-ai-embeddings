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
        try {
            redisTemplate.opsForValue().set(key, session, SESSION_TTL);
            logger.info("Created new quiz session {} with difficulty: {}", sessionId, difficulty);
            
            // Verify the session was saved correctly
            Object saved = redisTemplate.opsForValue().get(key);
            if (saved == null) {
                logger.error("Failed to save session {} to Redis", sessionId);
                throw new RuntimeException("Failed to create session in Redis");
            }
            
            return session;
        } catch (Exception e) {
            logger.error("Error creating session {}: {}", sessionId, e.getMessage(), e);
            throw new RuntimeException("Failed to create quiz session", e);
        }
    }

    /**
     * Retrieves a session from Redis
     */
    public Optional<QuizSession> getSession(String sessionId) {
        String key = getSessionKey(sessionId);
        
        try {
            Object sessionObj = redisTemplate.opsForValue().get(key);
            logger.debug("Retrieved object from Redis for key {}: {}", key, sessionObj != null ? sessionObj.getClass().getSimpleName() : "null");
            
            if (sessionObj == null) {
                logger.info("Session {} not found in Redis", sessionId);
                return Optional.empty();
            }
            
            if (sessionObj instanceof QuizSession session) {
                // Check if session has expired
                if (session.isExpired()) {
                    logger.info("Session {} has expired, removing from Redis", sessionId);
                    deleteSession(sessionId);
                    return Optional.empty();
                }
                
                logger.debug("Retrieved valid session {} from Redis", sessionId);
                return Optional.of(session);
            } else {
                logger.warn("Object in Redis for key {} is not a QuizSession: {}", key, sessionObj.getClass());
                return Optional.empty();
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving session {}: {}", sessionId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Updates an existing session in Redis
     */
    public void updateSession(QuizSession session) {
        if (session == null || session.getSessionId() == null) {
            throw new IllegalArgumentException("Session and session ID cannot be null");
        }
        
        try {
            session.updateActivity();
            String key = getSessionKey(session.getSessionId());
            
            // If session is completed, store with longer TTL for final summary
            Duration ttl = session.isCompleted() ? Duration.ofHours(2) : SESSION_TTL;
            redisTemplate.opsForValue().set(key, session, ttl);
            
            logger.debug("Updated session {} in Redis", session.getSessionId());
            
            // Verify the update
            Object updated = redisTemplate.opsForValue().get(key);
            if (updated == null) {
                logger.error("Failed to update session {} in Redis", session.getSessionId());
                throw new RuntimeException("Failed to update session in Redis");
            }
            
        } catch (Exception e) {
            logger.error("Error updating session {}: {}", session.getSessionId(), e.getMessage(), e);
            throw new RuntimeException("Failed to update quiz session", e);
        }
    }

    /**
     * Deletes a session from Redis
     */
    public void deleteSession(String sessionId) {
        String key = getSessionKey(sessionId);
        try {
            Boolean deleted = redisTemplate.delete(key);
            
            if (Boolean.TRUE.equals(deleted)) {
                logger.info("Deleted session {} from Redis", sessionId);
            } else {
                logger.warn("Failed to delete session {} from Redis", sessionId);
            }
        } catch (Exception e) {
            logger.error("Error deleting session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Extends session TTL when user is active
     */
    public void extendSession(String sessionId) {
        String key = getSessionKey(sessionId);
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                redisTemplate.expire(key, SESSION_TTL);
                logger.debug("Extended TTL for session {}", sessionId);
            }
        } catch (Exception e) {
            logger.error("Error extending session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    public long getActiveSessionCount() {
        try {
            Set<String> keys = redisTemplate.keys(SESSION_KEY_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            logger.error("Error getting active session count: {}", e.getMessage(), e);
            return 0;
        }
    }

    public void cleanupExpiredSessions() {
        try {
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
        } catch (Exception e) {
            logger.error("Error during session cleanup: {}", e.getMessage(), e);
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
        try {
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
        } catch (Exception e) {
            logger.error("Error getting session stats: {}", e.getMessage(), e);
            return new SessionStats(0, 0, 0);
        }
    }

    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String getSessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    public record SessionStats(int total, int active, int completed) {}
}