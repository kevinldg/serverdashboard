# Project Instructions

## Project

Private web application to manage my self-hosted Docker instance. Inspired by Portainer and Pterodactyl.

## Project Structure

- `/frontend` — React frontend application
- `/backend` — Spring Boot backend application
- `/docs` — project requirements and documentation

## Tech Stack

### Frontend

- React
- TypeScript
- Vite
- Tailwind CSS v4

### Backend

- Java 25
- Spring Boot 4.1.1
- Maven
- Lombok
- Spring Web
- Spring Data MongoDB

### Infrastructure

- MongoDB
- Self-hosted Docker on Linux Debian 12
- Remote infrastructure is accessed via SSH

## Architecture

- Keep business logic outside controllers.
- Use Controller → Service → Repository where applicable.
- Do not introduce layers or abstractions without a concrete reason.
- Frontend communicates with the backend through HTTP.
- During local development, use the Vite development proxy to avoid unnecessary CORS configuration.

## Development Principles

- Prefer simple solutions over unnecessary abstractions.
- Inspect the existing codebase before introducing new patterns or dependencies.
- Prefer existing project conventions over personal preferences.
- Preserve existing functionality unless a change explicitly requires modifying it.
- Avoid unnecessary refactoring.
- Prefer incremental changes over large rewrites.
- Keep changes focused on the requested task.
- Do not modify unrelated code.
- Do not introduce new frameworks without discussing it first.
- Do not add new dependencies without a concrete reason.

## Infrastructure

The application manages Docker resources on a remote Debian 12 server via SSH.

- Treat remote Docker operations as potentially destructive.
- Never perform destructive infrastructure operations without explicit user intent.
- Do not remove containers, images, volumes, networks, or other resources unless explicitly requested.
- Prefer read-only inspection when investigating the remote infrastructure.

## Security

- Never commit secrets, credentials, API keys, private keys, or `.env` files.
- Never print secrets or private keys in logs or command output.
- Never expose credentials in source code.
- Never modify or expose SSH private keys.
- Use environment variables or local configuration for secrets.

## Verification

After making changes:

- Run relevant frontend type checks, linting, and builds.
- Run relevant backend tests and builds.
- Verify both frontend and backend when a change affects their integration.
- Do not consider a task complete if relevant verification fails.

## Claude Code Workflow

- For non-trivial changes, first inspect the relevant code and propose an implementation plan.
- Wait for approval before implementing non-trivial changes.
- Small, obvious changes may be implemented directly when explicitly requested.
- Before implementing larger features, inspect the existing architecture and relevant requirements.
- Do not claim a task is complete without verifying the result.

## Git

- Keep changes logically focused.
- Do not modify Git history unless explicitly requested.
- Never commit changes unless explicitly requested.
- Never commit secrets.

## Development Environment

- Local development host: Windows 11.
- Claude Code runs inside a Docker sandbox.
- The application itself is deployed to and manages a remote Debian 12 server.
- Do not assume that commands available on the Windows host are available inside the Claude Code sandbox.
- Prefer commands and tooling available within the current execution environment.
- When environment-specific behavior matters, explicitly distinguish between the Windows host, the Claude Code sandbox, and the remote Debian server.