FROM openjdk:17-jdk-slim

# Install required system libraries
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

RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the application
RUN ./mvnw clean package -DskipTests

# Copy the built JAR file
RUN cp target/*.jar app.jar

# Expose the port that your app runs on
EXPOSE 8080

# Set JVM options for better cloud performance
ENV JAVA_OPTS="-Xmx512m -Xms256m -Djava.awt.headless=true -Dfile.encoding=UTF-8"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]