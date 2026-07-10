# ServerWatch

A self-hosted server monitoring and management dashboard built with Java and React. Monitor your VPS in real-time, manage Docker containers, browse files, run terminal commands, track Git repositories, and configure alerts — all from your browser.

![Dashboard](https://github.com/user-attachments/assets/2b6454ae-180c-4cc6-9a0a-cbfdb13d5be4)

---

## Features

### Real-Time Monitoring
Live system metrics streamed over WebSocket — CPU, RAM, disk, and network traffic update every 2 seconds without page refresh.

### Docker Management
View all running and stopped containers with live CPU/memory stats. Start, stop, restart, and remove containers. Stream container logs in real time directly in the browser.

![Containers](https://github.com/user-attachments/assets/90547dd8-94e8-446e-b827-28d6d72640e3)

### Web Terminal
Full interactive terminal access to your server from any browser tab. Supports vim, htop, nano, and any other interactive program. Opens with a custom welcome banner.

![Terminal](https://github.com/user-attachments/assets/632610ed-d188-43a9-84ad-186a87e9d254)

### File Manager
Browse, edit, upload, and download files on your server — no SFTP client needed. Text files open in a Monaco editor (the same editor used in VS Code) with syntax highlighting. Supports create, rename, move, delete, and `chmod`.

![Files](https://github.com/user-attachments/assets/b70fc087-c2a5-4f65-b313-97e53e3db1f9)

### Git Panel
Browse registered repositories, view commit history with file-by-file diffs, manage branches, and run pull/push/fetch operations — without touching the CLI.

![Git](https://github.com/user-attachments/assets/9a01d787-e7a5-4a85-8f42-6093c424aab1)

### Alert Engine
Define threshold-based rules (e.g. CPU > 80%) and receive notifications via email or webhook (Discord and Slack supported). Alerts appear in a live feed and are stored for historical review.

![Alerts](https://github.com/user-attachments/assets/f4f222bd-c1e4-4097-882a-64191a979956)

### User Management & Granular Permissions
Invite team members and control exactly what each user can access — per-feature, not just admin vs. non-admin. Terminal, file writes, container control, Git operations, and alert management are each individually grantable.

![Settings](https://github.com/user-attachments/assets/ea21bad1-a29c-46db-b687-9bf06e16b6b6)
![Permissions](https://github.com/user-attachments/assets/285871d4-21b4-4057-8f7b-850e6d7f3c77)

### Authentication
JWT-based login with refresh token rotation, bcrypt password hashing, and brute-force protection (rate-limits failed attempts per username and IP). Sessions are stateless; tokens are revocable.

![Login](https://github.com/user-attachments/assets/a1f38765-cbf4-4283-a04b-2c742b69b666)

---

## Tech Stack

### Backend
| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.3 |
| Real-time | WebSocket / STOMP |
| Metrics | OSHI (OS & hardware info) |
| Docker | Docker Java Client |
| Git | Eclipse JGit |
| Terminal | pty4j (pseudo-terminal) |
| Auth | JWT (jjwt), BCrypt |
| Database | PostgreSQL, Flyway, Spring Data JPA |
| Alerts | Spring Scheduler, JavaMail, HTTP webhooks |

### Frontend
| Layer | Technology |
|---|---|
| Framework | React 18, TypeScript, Vite |
| Styling | Tailwind CSS |
| Charts | Recharts |
| Terminal | xterm.js |
| Editor | Monaco Editor |
| State | Zustand |
| WebSocket | STOMP.js + SockJS |

---

## Getting Started

### Prerequisites
- Docker & Docker Compose
- Java 21 (for local development)

### Self-hosted Deployment

1. Clone the repository:
```bash
git clone https://github.com/alperenklc5/serverwatch.git
cd serverwatch
```

2. Create a `.env` file:
```env
DB_PASSWORD=your_secure_password
JWT_SECRET=your_jwt_secret_minimum_32_characters_long
MAIL_HOST=smtp.gmail.com
MAIL_USERNAME=your@email.com
MAIL_PASSWORD=your_email_password
```

3. Build the backend:
```bash
mvn clean package -DskipTests
```

4. Start the stack:
```bash
docker-compose up -d
```

The API will be available at `http://localhost:8090`.

### Frontend

```bash
cd serverwatch-ui
npm install
npm run dev
```

The dashboard will be available at `http://localhost:5173`.

For production, set the API URL in `.env.production`:
```env
VITE_API_URL=http://your-server-ip:8090
VITE_WS_URL=ws://your-server-ip:8090/ws
```

Then build:
```bash
npm run build
```

### Default Credentials
```
Username: admin
Password: changeme
```

> **Change the default password immediately after first login** via Settings → Account.

---

## Architecture

The backend runs as a Docker container **on the server it monitors**. It accesses the Docker socket directly to manage containers, mounts the host filesystem at `/hostfs` for file management, and uses `nsenter` to provide a true host shell in the terminal — giving the same access as SSH.

```
Browser
  └── React Frontend (Vite)
        ├── REST API  ──────────► Spring Boot (8090)
        └── WebSocket (STOMP) ──►    ├── OSHI (CPU/RAM/disk/net)
                                     ├── Docker Java Client
                                     ├── JGit
                                     ├── pty4j (terminal)
                                     └── PostgreSQL
```

---

## Permissions

Each user account has 12 individually toggleable permissions:

| Category | Permission |
|---|---|
| Terminal | Access web terminal |
| Files | View · Write/upload · Delete |
| Docker | View · Start/stop/restart · Remove |
| Git | View · Clone/pull/push |
| Alerts | View · Manage rules |
| Administration | Manage users & permissions |

The built-in `admin` account always retains all permissions and cannot be locked out.

---

## License

MIT
