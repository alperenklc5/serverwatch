# How to Use These Phase Files with Claude Code

## Setup

1. Create your project directory:
```bash
mkdir serverwatch && cd serverwatch
git init
```

2. Copy ALL phase files into the project root:
```
serverwatch/
├── PROJECT_OVERVIEW.md
├── PHASE_1_PROJECT_BOOTSTRAP.md
├── PHASE_2_METRIC_COLLECTION.md
├── PHASE_3_WEBSOCKET_REALTIME.md
├── PHASE_4_DOCKER_MANAGEMENT.md
├── PHASE_5_GIT_OPERATIONS.md
├── PHASE_6_ALERT_ENGINE.md
└── CLAUDE_CODE_GUIDE.md
```

3. Create a `CLAUDE.md` file in the project root (Claude Code reads this automatically):
```markdown
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
Currently working on: Phase 1

## Completed Phases
(Update this list as you complete each phase)
- [ ] Phase 1 — Project Bootstrap
- [ ] Phase 2 — Metric Collection
- [ ] Phase 3 — WebSocket Real-Time
- [ ] Phase 4 — Docker Management
- [ ] Phase 5 — Git Operations
- [ ] Phase 6 — Alert Engine
```

## How to Work Through Each Phase

### Starting a Phase

Open Claude Code in VS Code and paste this prompt pattern:

```
Read PHASE_1_PROJECT_BOOTSTRAP.md and implement everything described in it.
Follow the acceptance criteria as your checklist.
Create all files listed in the "Files to Create" section.
After implementation, verify each acceptance criterion.
```

### Phase-by-Phase Prompts

**Phase 1:**
```
Read PROJECT_OVERVIEW.md and PHASE_1_PROJECT_BOOTSTRAP.md.
Initialize the Spring Boot project with all dependencies, configuration files,
Docker Compose setup, and base classes. Create every file listed in the phase document.
Start by generating the pom.xml, then the config files, then the Java classes.
```

**Phase 2:**
```
Read PHASE_2_METRIC_COLLECTION.md. Phase 1 is already complete.
Implement all metric collectors (System, Network, Process) using OSHI,
the MetricService, REST endpoints, and the database snapshot persistence.
Create all DTOs as Java records. Test each endpoint after creation.
```

**Phase 3:**
```
Read PHASE_3_WEBSOCKET_REALTIME.md. Phases 1-2 are complete.
Add WebSocket support with STOMP. Create the WebSocketConfig,
WebSocketPublisher, and event listener. Modify MetricService to publish
to WebSocket topics. Create the test HTML page for validation.
```

**Phase 4:**
```
Read PHASE_4_DOCKER_MANAGEMENT.md. Phases 1-3 are complete.
Implement DockerService with full container lifecycle management.
Include container listing, stats, start/stop/restart, log viewing,
and log streaming via WebSocket. Add all safety guards.
```

**Phase 5:**
```
Read PHASE_5_GIT_OPERATIONS.md. Phases 1-4 are complete.
Implement GitService using JGit for repository management.
Include clone, pull, push, branch management, commit history,
and diff viewing. Create the RepoRegistry for tracking repos.
Add path traversal prevention and concurrent access locks.
```

**Phase 6:**
```
Read PHASE_6_ALERT_ENGINE.md. Phases 1-5 are complete.
Implement the alert engine with rule evaluation, email notifications
(JavaMailSender), and webhook notifications (Discord/Slack format).
Include cooldown tracking, async notification sending, and history cleanup.
```

### After Each Phase

1. Update `CLAUDE.md` — mark the phase as complete, update "Current Phase"
2. Test the acceptance criteria
3. Commit your progress:
```
git add -A
git commit -m "Phase N complete: [phase name]"
```

## Troubleshooting Tips for Claude Code

If Claude Code generates something that doesn't compile:
```
The build is failing. Run ./mvnw clean compile and fix all errors.
Show me the error output first, then fix each issue.
```

If a specific feature isn't working:
```
The [feature] endpoint is returning [error]. Read the relevant phase document
section about [feature] and compare with the current implementation.
Debug and fix the issue.
```

If you want to verify everything works:
```
Run the application with ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
and test all endpoints from the current phase's acceptance criteria.
Use curl commands to verify each one. Show me the results.
```

## Recommended Workflow

1. Work on ONE phase at a time — do not skip ahead
2. Each phase should take 1-3 Claude Code sessions
3. After each session, review the generated code yourself
4. Run the app and manually test before moving to the next phase
5. Keep git commits granular — commit after each working feature
