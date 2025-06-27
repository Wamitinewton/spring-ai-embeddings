FROM openjdk:17-jdk-slim

# Install minimal required system libraries
RUN apt-get update && apt-get install -y \
    libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml first for better caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make mvnw executable
RUN chmod +x ./mvnw

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the application with minimal memory usage
RUN ./mvnw clean package -DskipTests -Dmaven.compiler.fork=false

# Copy the built JAR file
RUN cp target/*.jar app.jar

# Clean up to reduce image size
RUN rm -rf .mvn mvnw pom.xml src target

# Expose port
EXPOSE 8080

# CRITICAL: Memory-optimized JVM settings for 512MB container limit
ENV JAVA_OPTS="-Xmx350m -Xms128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom -XX:+UnlockExperimentalVMOptions -XX:+UseContainerSupport -XX:MaxRAMPercentage=70"

# Run the application - CRITICAL: Bind to 0.0.0.0 and use PORT env var
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-8080} -jar app.jar"]