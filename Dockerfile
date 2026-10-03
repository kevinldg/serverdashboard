# Builds the complete dashboard: the frontend (Vite) is embedded into the Spring Boot application.
# Used by docker-compose.yml; see docs/deployment.md.

FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk AS backend
WORKDIR /build/backend
# Dependencies first, so they are cached as long as pom.xml does not change
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY backend/src/ src/
COPY --from=frontend /build/frontend/dist/ src/main/resources/static/
# Tests run before deploying, not in the image build
RUN ./mvnw -q -B -DskipTests package && cp target/backend-*.jar /build/app.jar

FROM eclipse-temurin:25-jre
ARG GIT_COMMIT=unknown
ENV GIT_COMMIT=${GIT_COMMIT}
RUN useradd --system --uid 10001 --no-create-home --home-dir /app serverdashboard
WORKDIR /app
COPY --from=backend /build/app.jar app.jar
# Not root; access to the Docker socket comes from the docker group (group_add in docker-compose.yml)
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
