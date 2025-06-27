FROM openjdk:17-jdk-slim

RUN apt-get update && apt-get install -y \
    libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x ./mvnw

RUN ./mvnw dependency:go-offline -B

COPY src/ src/

# RUN find src/main/resources -name "*.pdf" -type f -delete || true

RUN ./mvnw clean package -DskipTests -Dmaven.compiler.fork=false \
    -Dspring.profiles.active=prod

RUN cp target/*.jar app.jar

RUN rm -rf .mvn mvnw pom.xml src target

EXPOSE 8080


ENV JAVA_OPTS="-Xmx350m -Xms128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom -XX:+UnlockExperimentalVMOptions -XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -Dai.djl.offline=true -Dai.djl.repository.zoo.location=file:///tmp/empty"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-8080} -Dspring.profiles.active=prod -jar app.jar"]