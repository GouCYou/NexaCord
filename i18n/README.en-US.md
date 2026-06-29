# NexaCord

<p align="center">
  <a href="../README.md">🇨🇳 简体中文</a> ·
  <a href="README.zh-TW.md">🇭🇰 繁體中文</a> ·
  🇺🇸 English
</p>

NexaCord is a full-stack real-time community chat platform with a modern server, channel, friend, direct-message, and voice-space experience.
The project includes a Vue 3 web client and a Spring Boot backend, covering account registration and login, email verification codes, JWT sessions, server and channel management, text messages, image attachments, friends, direct messages, voice channels, invite links, and member roles.

## Table of Contents

- [Screenshots](#screenshots)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Development Checks](#development-checks)
- [Security Notes](#security-notes)
- [Contributing](#contributing)
- [License](#license)

## Screenshots

<p align="center">
  <span>
    <img width="19%" alt="Account login" src="../docs/screenshots/account-login.png" />
  </span>
  <span>
    <img width="19%" alt="Account registration" src="../docs/screenshots/account-register.png" />
  </span>
  <span>
    <img width="19%" alt="Friends home" src="../docs/screenshots/friends-home.png" />
  </span>
  <span>
    <img width="19%" alt="Channel chat" src="../docs/screenshots/channel-chat.png" />
  </span>
  <span>
    <img width="19%" alt="Voice channel" src="../docs/screenshots/voice-channel.png" />
  </span>
  <br />
  <sub>Account Login</sub>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <sub>Registration</sub>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <sub>Friends Home</sub>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <sub>Channel Chat</sub>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <sub>Voice Channel</sub>
</p>

## Features

- Account system: registration, login, email verification codes, password reset, password strength checks, and local session recovery.
- Session security: JWT and refresh-token based login state, plus replacement notices when the account signs in on another device.
- Server spaces: create servers, switch servers, edit server details, invite members, and manage member roles.
- Channel organization: text, voice, and category channels with server-aware channel list refreshes.
- Text messages: load, send, edit, delete, emoji input, and unread-state synchronization.
- File attachments: image selection, upload, preview, and multi-attachment messages, with Qiniu object storage support on the backend.
- Friends and direct messages: friend requests, friend filters, direct-message conversations, direct messages, and online status.
- Voice channels: join and leave voice rooms, mute microphone, deafen remote audio, and view voice member state.
- Realtime transport: STOMP over SockJS for messages, channels, servers, friend status, and session events.
- API docs: Springdoc OpenAPI integration with local Swagger UI.

## Tech Stack

| Module | Technology |
| --- | --- |
| Backend | Java 17, Spring Boot 3.5, Spring Security, Spring Data JPA |
| Data | MySQL, Redis, Hibernate |
| Realtime | Spring WebSocket, STOMP, SockJS |
| Auth | JWT, Refresh Token, Spring Security Filter |
| File Storage | Qiniu Object Storage |
| Email | SMTP email verification |
| Frontend | Vue 3, TypeScript, Vite, Vue Router, Pinia |
| UI | Element Plus, Lucide Vue, custom CSS |
| HTTP | Axios |

## Project Structure

```text
NexaCord/
├── nexacord-server/              # Spring Boot backend service
├── nexacord-web/                 # Vue 3 web client
├── docs/screenshots/             # README screenshots
├── i18n/                         # Localized README files
├── nexacord_server.sql           # Backend database schema and optional seed data
└── README.md
```

## Requirements

- JDK 17+
- Maven 3.9+, or the Maven Wrapper bundled in `nexacord-server`
- MySQL 8+
- Redis 6+
- Node.js 20+
- Optional: Qiniu object storage and an SMTP email service

## Quick Start

### 1. Create the database

```sql
CREATE DATABASE nexacord
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

To import the optional schema and seed data:

```bash
mysql -u root -p nexacord < nexacord_server.sql
```

### 2. Configure private backend settings

The backend tries to read `nexacord-server/application-local.yml` by default. Put local database, Redis, email, JWT, and object-storage secrets in that file, and do not commit it.

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/nexacord?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: your_mysql_user
    password: your_mysql_password
  data:
    redis:
      host: localhost
      port: 6379
  mail:
    username: your_mail_account
    password: your_mail_authorization_code

jwt:
  secret: replace-with-a-long-random-base64-secret

qiniu:
  access-key: your_qiniu_access_key
  secret-key: your_qiniu_secret_key
  bucket: your_bucket
  domain: your_cdn_domain

app:
  cors:
    allowed-origin-patterns: http://localhost:5173,http://127.0.0.1:5173
```

### 3. Start the backend

```bash
cd nexacord-server
./mvnw spring-boot:run
```

Windows:

```powershell
cd nexacord-server
.\mvnw.cmd spring-boot:run
```

Default backend URL: `http://localhost:8080`

API docs: `http://localhost:8080/swagger-ui/index.html`

### 4. Start the frontend

Point the web client to the local backend in `nexacord-web/.env.local`:

```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_BASE_URL=http://localhost:8080
```

Run the web client:

```bash
cd nexacord-web
npm install
npm run dev
```

The default Vite URL is usually `http://localhost:5173`.

## Configuration

- `nexacord-server/src/main/resources/application.yml` keeps default development settings and supports environment-variable overrides.
- `nexacord-server/application-local.yml` is the recommended place for local private settings.
- Local frontend development should set `VITE_API_BASE_URL`; otherwise the code uses its default remote API URL.
- `VITE_WS_BASE_URL` should point to the backend root URL without `/api`.
- In production, use environment variables or deployment-specific configuration for database passwords, JWT secrets, email authorization codes, and object-storage keys.

## Development Checks

Backend tests:

```bash
cd nexacord-server
./mvnw test
```

Backend compile:

```bash
cd nexacord-server
./mvnw -DskipTests compile
```

Frontend build:

```bash
cd nexacord-web
npm run build
```

## Security Notes

- Do not commit database passwords, email authorization codes, JWT secrets, Qiniu Access Keys, or Qiniu Secret Keys.
- The default JWT secret in `application.yml` is only a development fallback. Production must override it through `JWT_SECRET` or private configuration.
- If any secret has reached a public repository, revoke and regenerate it immediately.
- Use object storage and CDN domains for uploads in production. Avoid relying on local file serving long term.
- CORS should only allow trusted frontend domains. Avoid broad origin patterns in production.

## Contributing

Issues and pull requests are welcome. Before submitting, it is helpful to include:

- Problem background or feature goal
- Impacted area
- Main implementation idea
- Completed tests or verification steps

## License

This repository does not currently declare an open-source license. Add a `LICENSE` file before public distribution or accepting external contributions.
