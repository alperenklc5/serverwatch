# Phase 1 — Project Bootstrap & Configuration

## Objective
Initialize the Spring Boot project with all required dependencies, configuration files, Docker Compose for development, and the base application structure. After this phase, the application starts successfully and connects to PostgreSQL.

## Prerequisites
- Java 21 SDK installed
- Maven or Maven Wrapper
- Docker & Docker Compose installed on the machine

## Step 1: Initialize Spring Boot Project

Create a Spring Boot 3.3+ project using Maven. The `groupId` is `com.serverwatch`, `artifactId` is `serverwatch`.

### Dependencies (pom.xml)
```xml
<!-- Core -->
spring-boot-starter-web
spring-boot-starter-websocket
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-mail
spring-boot-starter-validation
spring-boot-starter-actuator

<!-- Database -->
postgresql (runtime)
flyway-core
flyway-database-postgresql

<!-- Metric Collection -->
com.github.oshi:oshi-core:6.6.5

<!-- Docker -->
com.github.docker-java:docker-java-core:3.4.1
com.github.docker-java:docker-java-transport-httpclient5:3.4.1

<!-- Git -->
org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r

<!-- Caching -->
com.github.ben-manes.caffeine:caffeine

<!-- Utility -->
org.projectlombok:lombok (optional, annotationProcessor)
com.google.code.gson:gson

<!-- DevTools -->
spring-boot-devtools (runtime, optional)

<!-- Test -->
spring-boot-starter-test
```

## Step 2: Application Configuration

### application.yml
```yaml
server:
  port: 8090

spring:
  application:
    name: serverwatch
  profiles:
    active: dev

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
        jdbc:
          time_zone: UTC

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

serverwatch:
  collector:
    interval-ms: 2000
    history-retention-hours: 24
  docker:
    socket-path: unix:///var/run/docker.sock
  alert:
    cooldown-minutes: 5
    evaluation-interval-ms: 5000
  git:
    base-path: /opt/repos
  security:
    jwt-secret: ${JWT_SECRET:change-me-in-production-this-is-dev-only}
    jwt-expiration-ms: 86400000
```

### application-dev.yml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/serverwatch
    username: serverwatch
    password: serverwatch
  jpa:
    show-sql: true

logging:
  level:
    com.serverwatch: DEBUG
    org.springframework.web.socket: DEBUG
```

### application-prod.yml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:serverwatch}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

logging:
  level:
    com.serverwatch: INFO

serverwatch:
  security:
    jwt-secret: ${JWT_SECRET}
```

## Step 3: Docker Compose for Development

### docker-compose.dev.yml
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: serverwatch
      POSTGRES_USER: serverwatch
      POSTGRES_PASSWORD: serverwatch
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U serverwatch"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
```

### Dockerfile (for production deployment later)
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/serverwatch-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml (production)
```yaml
version: '3.8'
services:
  serverwatch:
    build: .
    ports:
      - "8090:8090"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=postgres
      - DB_USERNAME=serverwatch
      - DB_PASSWORD=${DB_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
      - MAIL_HOST=${MAIL_HOST}
      - MAIL_USERNAME=${MAIL_USERNAME}
      - MAIL_PASSWORD=${MAIL_PASSWORD}
    depends_on:
      postgres:
        condition: service_healthy

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: serverwatch
      POSTGRES_USER: serverwatch
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U serverwatch"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
```

## Step 4: Base Java Classes

### 4.1 Main Application
`ServerWatchApplication.java` — standard Spring Boot main class with `@EnableScheduling`.

### 4.2 Configuration Classes

**SchedulingConfig.java** — configure a `ThreadPoolTaskScheduler` with pool size 4, thread name prefix `sw-scheduler-`.

**SecurityConfig.java** — for now, permit all requests. Disable CSRF. Add CORS configuration allowing `http://localhost:3000` and `http://localhost:5173` (React dev servers). We will add JWT authentication in a later phase.

```java
// Minimal security config for Phase 1:
// - permitAll on all endpoints
// - disable CSRF (API-only, no browser forms)
// - enable CORS for frontend dev servers
// - stateless session management
```

**DockerConfig.java** — create a `DockerClient` bean using `DockerClientBuilder.getInstance()` with the socket path from config. Include a `@PostConstruct` method that calls `dockerClient.pingCmd().exec()` and logs success/failure. If Docker socket is not available, log a warning but do NOT crash the app — some dev machines won't have Docker.

### 4.3 Custom Configuration Properties

Create `ServerWatchProperties.java` annotated with `@ConfigurationProperties(prefix = "serverwatch")` containing nested static classes for `collector`, `docker`, `alert`, `git`, and `security` sections. Enable with `@EnableConfigurationProperties` on the main class.

## Step 5: Database Migration

### V1__initial_schema.sql (Flyway)
```sql
CREATE TABLE alert_rules (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    metric_type     VARCHAR(50) NOT NULL,
    operator        VARCHAR(10) NOT NULL,
    threshold       DOUBLE PRECISION NOT NULL,
    cooldown_minutes INTEGER NOT NULL DEFAULT 5,
    notify_email    BOOLEAN NOT NULL DEFAULT false,
    notify_webhook  BOOLEAN NOT NULL DEFAULT false,
    webhook_url     VARCHAR(500),
    email_recipients VARCHAR(1000),
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE alert_history (
    id              BIGSERIAL PRIMARY KEY,
    rule_id         BIGINT REFERENCES alert_rules(id) ON DELETE CASCADE,
    metric_type     VARCHAR(50) NOT NULL,
    metric_value    DOUBLE PRECISION NOT NULL,
    threshold       DOUBLE PRECISION NOT NULL,
    message         TEXT,
    notified        BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE metric_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    metric_type     VARCHAR(50) NOT NULL,
    metric_key      VARCHAR(255),
    value           DOUBLE PRECISION NOT NULL,
    unit            VARCHAR(20),
    recorded_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_history_rule_id ON alert_history(rule_id);
CREATE INDEX idx_alert_history_created_at ON alert_history(created_at);
CREATE INDEX idx_metric_snapshots_type_time ON metric_snapshots(metric_type, recorded_at);
```

## Step 6: Health Check Endpoint

Create a simple REST controller `HealthController.java` at `/api/health` that returns:
```json
{
  "status": "UP",
  "version": "1.0.0",
  "timestamp": "2025-01-01T00:00:00Z",
  "docker": true,
  "database": true
}
```

Check Docker connectivity by pinging the Docker client. Check database by executing a simple query. Both should be try-catch wrapped — return `false` if unavailable, never throw.

## Acceptance Criteria
- [ ] `./mvnw clean compile` succeeds with zero errors
- [ ] `docker-compose -f docker-compose.dev.yml up -d` starts PostgreSQL
- [ ] `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` starts the application on port 8090
- [ ] `GET http://localhost:8090/api/health` returns 200 with the JSON above
- [ ] Flyway runs the migration and creates all 3 tables
- [ ] Application logs show Docker ping result (success or warning)
- [ ] No hardcoded secrets — all sensitive values come from environment variables or config

## Files to Create
```
src/main/java/com/serverwatch/
├── ServerWatchApplication.java
├── config/
│   ├── SchedulingConfig.java
│   ├── SecurityConfig.java
│   ├── DockerConfig.java
│   └── ServerWatchProperties.java
└── controller/
    └── HealthController.java

src/main/resources/
├── application.yml
├── application-dev.yml
├── application-prod.yml
└── db/migration/
    └── V1__initial_schema.sql

docker-compose.dev.yml
docker-compose.yml
Dockerfile
pom.xml
```
