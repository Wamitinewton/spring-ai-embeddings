# Multi-stage build for Railway deployment - FIXED for temp directory issue
FROM eclipse-temurin:17-jdk-alpine AS builder

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY .mvn .mvn
COPY mvnw mvnw.cmd pom.xml ./

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Production stage
FROM eclipse-temurin:17-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Set working directory
WORKDIR /app

# Create writable temp directory for Tomcat - THIS FIXES THE PERMISSION ISSUE
RUN mkdir -p /app/temp && chmod 755 /app/temp

# Copy the built jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose port
EXPOSE $PORT

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:$PORT/api/health || exit 1

# Run with custom temp directory - THIS IS THE KEY FIX
CMD ["sh", "-c", "java -Dserver.port=$PORT -Dspring.profiles.active=prod -Djava.io.tmpdir=/app/temp -Xmx512m -Xms256m -XX:+UseG1GC -jar app.jar"]