# Multi-stage build for smaller image
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy gradle wrapper and build files first for better caching
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./

# Grant permission to gradlew
RUN chmod +x ./gradlew

# Copy source code
COPY src src

# Build the application (skip tests for faster Docker build)
RUN ./gradlew bootJar -x test

# Runtime stage
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Create non-root user for security
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Copy the jar file from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Run the application - environment variables will be passed at runtime
ENTRYPOINT ["java", "-jar", "app.jar"]
