# ServerWatch — Project Overview

## What Is This?
ServerWatch is a real-time server monitoring and management dashboard built with Java (Spring Boot). It monitors Docker containers, system metrics (CPU, RAM, disk, network), and provides container management + Git operations through a web UI — eliminating the need for direct terminal/SSH access.

## Tech Stack

### Backend (This Project)
- **Java 21** + **Spring Boot 3.3+**
- **Spring WebSocket (STOMP)** — real-time metric streaming
- **OSHI** — OS/hardware metric collection (CPU, RAM, disk, network)
- **Docker Java Client** (`com.github.docker-java`) — container lifecycle management
- **JGit** (Eclipse) — Git operations without CLI
- **Spring Data JPA** + **PostgreSQL** — alert rules, metric history, user settings
- **Spring Mail** — SMTP alert delivery
- **Spring Security** — JWT-based authentication
- **Caffeine Cache** — in-memory metric buffering

### Frontend (Separate Phase — Not Yet)
- React + TypeScript
- Recharts / Chart.js
- xterm.js (web terminal)
- Shadcn/ui

## Project Structure
```
serverwatch/
├── src/main/java/com/serverwatch/
│   ├── ServerWatchApplication.java
│   ├── config/
│   │   ├── WebSocketConfig.java
│   │   ├── SecurityConfig.java
│   │   ├── DockerConfig.java
│   │   └── SchedulingConfig.java
│   ├── collector/
│   │   ├── SystemMetricCollector.java
│   │   ├── DockerMetricCollector.java
│   │   └── NetworkMetricCollector.java
│   ├── model/
│   │   ├── dto/
│   │   │   ├── SystemMetricDTO.java
│   │   │   ├── ContainerInfoDTO.java
│   │   │   ├── ContainerStatsDTO.java
│   │   │   ├── NetworkMetricDTO.java
│   │   │   ├── AlertRuleDTO.java
│   │   │   └── GitOperationDTO.java
│   │   └── entity/
│   │       ├── AlertRule.java
│   │       ├── AlertHistory.java
│   │       └── MetricSnapshot.java
│   ├── service/
│   │   ├── MetricService.java
│   │   ├── DockerService.java
│   │   ├── GitService.java
│   │   ├── AlertService.java
│   │   └── WebSocketPublisher.java
│   ├── controller/
│   │   ├── MetricController.java
│   │   ├── DockerController.java
│   │   ├── GitController.java
│   │   └── AlertController.java
│   ├── alert/
│   │   ├── AlertEngine.java
│   │   ├── AlertEvaluator.java
│   │   ├── notifier/
│   │   │   ├── EmailNotifier.java
│   │   │   ├── WebhookNotifier.java
│   │   │   └── Notifier.java
│   │   └── rule/
│   │       ├── ThresholdRule.java
│   │       └── RuleRegistry.java
│   └── websocket/
│       ├── MetricWebSocketHandler.java
│       └── WebSocketEventListener.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── application-prod.yml
└── docker-compose.yml
```

## Build Phases
The project is divided into 6 phases. Each phase is a self-contained unit with clear inputs, outputs, and acceptance criteria. Complete them in order — each phase builds on the previous one.

| Phase | Name | Description |
|-------|------|-------------|
| 1 | Project Bootstrap | Spring Boot project setup, config, Docker Compose for dev env |
| 2 | System Metric Collection | OSHI-based CPU/RAM/disk/network collectors |
| 3 | WebSocket Real-Time Layer | STOMP WebSocket streaming of metrics |
| 4 | Docker Management | Container CRUD, logs, stats via Docker Java Client |
| 5 | Git Operations | JGit-based pull/push/branch/diff/log |
| 6 | Alert Engine | Threshold rules, email + webhook notifications |

## Running the Project
```bash
# Start dev dependencies (PostgreSQL)
docker-compose -f docker-compose.dev.yml up -d

# Run the application
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Or build and run as Docker container
./mvnw clean package -DskipTests
docker build -t serverwatch .
docker-compose up -d
```

## Key Design Decisions
1. **Agent runs ON the VPS it monitors** — accesses Docker socket and /proc directly
2. **STOMP over WebSocket** — topic-based pub/sub for different metric channels
3. **Modular collectors** — each metric source is an independent @Component
4. **Docker socket mount** — `/var/run/docker.sock` volume mount, not TCP API
5. **JGit over CLI** — no shell exec, pure Java Git implementation
6. **Alert cooldown** — prevents notification spam with configurable cooldown per rule
