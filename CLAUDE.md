# ServerWatch — Claude Code Instructions

## Project Context
Read PROJECT_OVERVIEW.md for the full architecture and tech stack.
This is a Java 21 + Spring Boot 3.3+ project for real-time server monitoring.

## Rules
- Use Java 21 features (records, pattern matching, sealed interfaces where appropriate)
- Follow Spring Boot conventions and best practices
- Use constructor injection, never field injection
- All DTOs should be Java records unless mutability is needed
- Use SLF4J logging (via Lombok @Slf4j or manual LoggerFactory)
- Write clear Javadoc on all public service methods
- All REST responses are wrapped in ApiResponse<T>
- Use @Valid for request body validation
- Never hardcode secrets — use environment variables
- Error handling: never let exceptions bubble to the client unhandled

## Current Phase
Currently working on: Complete (all phases done)

## Completed Phases
- [x] Phase 1 — Project Bootstrap
- [x] Phase 2 — Metric Collection
- [x] Phase 3 — WebSocket Real-Time
- [x] Phase 4 — Docker Management
- [x] Phase 5 — Git Operations
- [x] Phase 6 — Alert Engine
- [x] Phase 7 — File Manager
- [x] Phase 8 — Web Terminal (pty4j PTY backend)
- [x] Phase 9 — Authentication & Authorization (JWT)