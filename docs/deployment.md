# Deployment

The dashboard runs as a Docker container on the Debian host it manages and uses the local Docker socket.
It is reachable on the local network only: `http://<server>:8090` (plain HTTP).

## Requirements on the server

- Docker Engine with the Compose plugin (`docker compose version`)
- A user in the `docker` group to run the commands (here: `serverdashboard`)
- Internet access during the build (base images, npm and Maven dependencies)
- MongoDB Atlas must accept connections from the server's public IP ("Network Access" in Atlas)
- Port 8090 must **not** be forwarded to the internet by the router

## First installation

```bash
# 1. Copy the project to the server (no Git repository needed), e.g. from the development machine:
tar --exclude=node_modules --exclude=target --exclude=dist --exclude=.git --exclude=.idea \
    --exclude='.env' --exclude='id_*' --exclude='*_ed25519*' -czf - . \
  | ssh serverdashboard@<server> 'mkdir -p ~/serverdashboard && tar -xzf - -C ~/serverdashboard'

# 2. On the server: create the configuration (never commit it)
cd ~/serverdashboard
cp .env.production.example .env
chmod 600 .env
# edit .env: MONGODB_URI (production database), INITIAL_ADMIN_*, BIND_MOUNT_ROOT, DOCKER_GID (getent group docker)

# 3. Build and start
docker compose up -d --build

# 4. Check
docker compose ps
docker compose logs -f serverdashboard     # until "Started BackendApplication"
```

Then open `http://<server>:8090` and log in with the initial admin. Change its password on the account page.
`INITIAL_ADMIN_PASSWORD` can be removed from `.env` afterwards; it is only used while no admin exists.

## Updates

```bash
# Copy the new version (same command as step 1), then on the server:
cd ~/serverdashboard
docker compose up -d --build
docker image prune -f        # removes the previous image
```

Set `GIT_COMMIT` in `.env` to the deployed commit (`git rev-parse --short HEAD`) to see it on the system page.

## Operation

| Task | Command (in `~/serverdashboard`) |
|---|---|
| Status | `docker compose ps` |
| Logs | `docker compose logs -f serverdashboard` |
| Restart | `docker compose restart serverdashboard` |
| Stop | `docker compose down` |

The dashboard cannot stop, restart or delete its own container (label `serverdashboard.self=true`); use the commands
above for that.

## Notes

- Sessions are kept in memory: after a restart or update, everyone has to log in again.
- The container runs as a non-root user with a read-only file system; Docker access comes from the `docker` group
  (`DOCKER_GID`). Anyone with access to the dashboard as administrator effectively controls the server.
- Without HTTPS, browsers do not allow copying to the clipboard ("Copy credentials"); the password can still be
  revealed and copied manually.
