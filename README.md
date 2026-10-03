# ServerDashboard

A self-hosted web application for managing Docker containers on a single Linux server, with a focus on game servers.
Inspired by Portainer and Pterodactyl.

> Built for a private setup and meant to run on a local network only. Anyone with administrator access to the dashboard
> effectively controls the Docker host.

## Features

- **Dashboard** with all containers, their image, and state; start, stop, restart, force stop, and delete with
  confirmations (delete only for stopped containers)
- **Container details**: general information, storage, configuration (ports, networks, restart policy, environment
  variables, labels), logs and live logs
- **Container creation** with ports, volumes, bind mounts (restricted to a configured directory), environment variables,
  restart policy, network, and memory limit; image pull progress is shown live
- **Game servers**: automatic detection by image, Docker label, or manual classification; profiles and templates for
  Minecraft (Java and Bedrock) and Satisfactory
- **Configuration files** of game servers: file browser and built-in editor (Monaco), with a backup of the previous version
- **Users, roles, and permissions**: default roles Admin, Moderator, and User; custom roles with fine-grained permissions
- **Administration**: announcements on the dashboard, maintenance mode, and system information

## Tech stack

| Part | Technologies |
|---|---|
| Frontend | React, TypeScript, Vite, Tailwind CSS v4, Monaco Editor |
| Backend | Java 25, Spring Boot 4, Spring Security, Spring Data MongoDB, docker-java |
| Database | MongoDB (e.g. MongoDB Atlas) |
| Deployment | Docker Compose on the managed host (Debian 12) |

## Getting started (development)

Requirements: JDK 25, Node.js 22, a MongoDB database, and SSH access to a Docker host.

1. Copy `.env.example` to `.env` in the project root and fill in the values (never commit `.env`).
2. Open an SSH tunnel to the Docker socket of the server:

   ```bash
   ssh -i <key> -N -L 127.0.0.1:2375:/var/run/docker.sock <user>@<server>
   ```

3. Start the backend (port 8080):

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

4. Start the frontend (Vite proxies `/api` to the backend):

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

5. Log in with the initial admin from `.env` and change its password on the account page.

### Checks

```bash
cd backend && ./mvnw test                     # backend tests (the database is mocked)
cd frontend && npm run lint && npm run build  # frontend lint, type check, and build
```

## Deployment

The dashboard runs as a Docker container on the server it manages and uses the local Docker socket.
See [docs/deployment.md](docs/deployment.md).

## Documentation

- [docs/requirements.md](docs/requirements.md) – what the application should do
- [docs/architecture.md](docs/architecture.md) – architecture decisions
- [docs/deployment.md](docs/deployment.md) – installation, updates, and operation
