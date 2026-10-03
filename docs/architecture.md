# Architecture Decisions

Decisions that complement `requirements.md`. Update this file when a decision changes.

## Deployment

- In production, the backend runs directly on the Debian 12 Docker host.
- Spring Boot serves the built frontend as static resources (single origin, no CORS).
- During local development, the Vite dev server proxies `/api` to the backend.

## Docker Access

- The backend uses the Docker Engine API via a Docker client library (docker-java).
- The Docker host is configurable:
  - Production: local socket `unix:///var/run/docker.sock`.
  - Development: an SSH tunnel to the remote socket opened by the developer,
    e.g. `ssh -L 2375:/var/run/docker.sock user@server`, with the backend pointed at `tcp://localhost:2375`.
- The application itself does not use SSH.
- All Docker access goes through a single service layer; controllers never talk to Docker directly.
- Docker errors are translated into API errors: container not found → 404, Docker host unreachable → 503,
  other Docker errors → 502. The original error is only shown to administrators.
- SSH access to the Docker host uses the dedicated user `serverdashboard` (member of the `docker` group, key-only login).
  Each developer machine / sandbox has its own key, so keys can be revoked individually.

## Containers API

- `GET /api/containers` (`DASHBOARD_VIEW`): statistics and container list.
  "Stopped" counts containers in state `created`, `exited` or `dead`; paused or restarting containers count as neither.
- `GET /api/containers/{id}` (`CONTAINER_VIEW`): general information, storage, configuration.
  Environment variables are only included with `CONTAINER_ENV_VIEW`.
- `GET /api/containers/{id}/logs?tail=500` (`CONTAINER_LOGS_VIEW`): the last 1–5000 log lines, read-only.
- The frontend loads data once per page view; users refresh manually (no polling).

## Container Actions

| Endpoint | Permission | Behavior |
|---|---|---|
| `POST /api/containers/{id}/start` | `CONTAINER_START` | Start |
| `POST /api/containers/{id}/stop` | `CONTAINER_STOP` | SIGTERM, then SIGKILL after `app.docker.stop-timeout` (default 60 s) |
| `POST /api/containers/{id}/restart` | `CONTAINER_RESTART` | Restart with the same stop timeout |
| `POST /api/containers/{id}/force-stop` | `CONTAINER_FORCE_STOP` | SIGKILL immediately |
| `DELETE /api/containers/{id}` | `CONTAINER_DELETE` | Only for stopped containers (otherwise 409); volumes are always kept |

- Actions are idempotent: if the container is already in the requested state, the request succeeds without changes.
- `app.docker.response-timeout` (90 s) must be longer than the stop timeout, so stopping is not cut off client-side.
- Every action is logged with user, action, and container (no separate audit log).
- The frontend asks for confirmation (modal) for every action except start. The delete confirmation lists
  the volumes and bind mounts that remain and warns about unnamed volumes and data stored inside the container.
- Destructive actions are tested against a local Docker daemon (e.g. the development sandbox), never against
  the production containers.

## Game Servers

- Profiles are Java classes implementing `GameServerProfile` (ID, name, image names). A new game = a new `@Component`.
  Current profiles: `minecraft-java` (`itzg/minecraft-server`), `minecraft-bedrock` (`itzg/minecraft-bedrock-server`),
  `satisfactory` (`wolveix/satisfactory-server`). "Generic game server" is available for games without a profile.
- Detection priority:
  1. Manual classification (collection `container_classifications`, keyed by **container name**, so it survives
     recreating the container; renaming loses it).
  2. Docker label `serverdashboard.gameserver`: a profile ID, `generic`/`true`/`yes`, or `none`/`false`/`no`.
     Unknown values count as a generic game server.
  3. Image name (registry, tag, and digest are ignored).
  4. Otherwise: not a game server. Ports are deliberately not used (too many false positives).
- Every result includes its source (manual, label, image, none).
- `PUT /api/containers/{id}/classification` (`GAMESERVER_MANAGE`): mode `AUTOMATIC` (removes the manual classification),
  `GAME_SERVER` (with optional profile), or `NOT_GAME_SERVER`. `GET /api/game-server-profiles` lists the profiles.
- Deleting a container through the application also removes its manual classification.

## Container Creation

- `GET /api/containers/creation-options`, `POST /api/containers` (202 with a job), `GET /api/container-jobs/{id}` and
  `GET /api/container-jobs/{id}/events` (Server-Sent Events); all require `CONTAINER_CREATE` (Admin only by default).
- Supported options: name, image with tag, ports (TCP/UDP), named volumes and bind mounts, environment variables,
  restart policy, network, optional memory limit, start after creation. New options are added as new request fields.
- Not supported on purpose: privileged mode, capabilities, host network, devices, PID/IPC namespaces.
- Bind mounts are only allowed strictly below `app.containers.bind-mount-root` (`BIND_MOUNT_ROOT`); without it they are
  disabled. Paths are handled with Linux semantics regardless of the backend's OS; `..` and the Docker socket are rejected.
  Symlinks on the host are not resolved (the backend cannot inspect the host file system).
- Name and published-port conflicts are checked before creating; conflicts with stopped containers or other programs
  only show up when starting (the container is then kept with status "created").
- Jobs (in memory, kept 1 hour after finishing): pull the image if missing (progress in percent), create, start.
  Only the creator and administrators can see a job; Docker's error text is only shown to administrators.
- Every created container gets the label `serverdashboard.created-by`; containers from a template additionally get
  `serverdashboard.gameserver=<profile>`.
- Templates belong to game server profiles (Minecraft Java, Minecraft Bedrock, Satisfactory); values were verified against
  the images' documentation. Templates that require a EULA (Minecraft) need explicit acceptance (`EULA=TRUE`).

## Configuration & Secrets

- Secrets are provided via environment variables.
- For local development, the backend loads the project-root `.env` (see `.env.example`).
- `.env` files and keys are never committed.

## Authentication

- Spring Security with server-side sessions (session cookie + CSRF protection).
- Sessions are kept in memory: users have to log in again after a backend restart.
  (Spring Boot 4 has no built-in MongoDB session store; this can be revisited if restarts become annoying.)
- Endpoints:
  - `POST /api/auth/login` (form parameters `username`, `password`) returns the current user.
  - `POST /api/auth/logout`
  - `GET /api/auth/me` returns the current user with role, `admin` flag, and effective permissions.
  - `PUT /api/auth/password` changes the own password.
  - `GET /api/auth/csrf` issues the CSRF cookie; the frontend calls it before logging in.
- CSRF: `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header (axios handles this automatically).
- Users are stored in MongoDB; passwords are stored as BCrypt hashes only.
- Usernames are case-insensitive and stored in lower case.
- Passwords must be 12–72 characters (BCrypt's limit is 72 bytes).
- The logged-in user is reloaded from the database on every request:
  deactivated or deleted users lose their session immediately, and role changes apply to the next request.
- Login failures always return the same message, whether the user is unknown, deactivated, or the password is wrong.
- The initial admin user is created from `INITIAL_ADMIN_USERNAME` and `INITIAL_ADMIN_PASSWORD` on startup
  if no admin exists. It is marked as "password change recommended".
- Password reset via email is postponed. Password resets are performed by administrators.

## Authorization

- Permissions are a fixed set of codes defined in code (e.g. `CONTAINER_START`, `GAMESERVER_CONFIG_EDIT`).
- Roles are stored in MongoDB and hold a configurable set of permission codes.
- Default roles: Admin, Moderator, User.
- Admin is a superuser: it bypasses permission checks and is not affected by the role's permission set.
- Safeguards:
  - The Admin role cannot be deleted.
  - The last active admin cannot be deleted, deactivated, or demoted.
- Permission checks are enforced in the backend; the frontend only hides unavailable actions.
- Additional permissions beyond the requirements:
  - `CONTAINER_CREATE` (default: Admin only)
  - `CONTAINER_FORCE_STOP`
  - `CONTAINER_ENV_VIEW` — environment variables often contain secrets and are hidden without this permission.

## User Management

- Endpoints under `/api/admin/users` (permission `USER_MANAGE`): list, create, edit (username, role, active),
  reset password, delete. `GET /api/admin/users/role-options` provides the roles for selection.
- Usernames: 3–32 characters (`A–Z a–z 0–9 . _ -`), case-insensitive.
- Passwords are either set manually (same rules as password changes) or generated:
  20 characters from an alphabet without look-alike characters, returned exactly once, stored only as a hash.
- New users and reset passwords are marked "password change recommended".
- A password reset by an administrator increments the user's session version, which ends all existing sessions.
- Safeguards: users cannot change their own role, deactivate, reset (via admin), or delete themselves;
  the last active administrator cannot be deactivated, demoted, or deleted.
- Escalation rules (relevant when `USER_MANAGE`/`ROLE_MANAGE` are granted to non-admin roles):
  only administrators can assign the Admin role or manage administrator accounts,
  and non-admins cannot grant permissions they do not have themselves.
- The frontend's admin area is at `/admin`; each tab is shown only with its permission.

## Role Management

- Endpoints under `/api/admin/roles` and `GET /api/admin/permissions` (permission `ROLE_MANAGE`).
- Each permission has a group (Dashboard, Containers, Game servers, Administration) and a description, defined in code.
- The Admin role cannot be changed or deleted. Built-in roles (Admin, Moderator, User) cannot be renamed or deleted;
  the permissions of Moderator and User can be changed.
- Role names are unique (case-insensitive), 2–32 characters.
- Custom roles can only be deleted while no user has them.
- Non-admins can only add permissions they have themselves; keeping or removing other permissions is allowed.
- Permission changes apply to logged-in users with their next request.

## Announcements

- Dashboard: `GET /api/announcements` (`DASHBOARD_VIEW`) returns the currently visible announcements, newest first.
- Management: `/api/admin/announcements` (`ANNOUNCEMENT_MANAGE`): list, create, update (also activate/deactivate), delete.
- Fields: title (max. 120), message (plain text, max. 2000, line breaks kept), active, optional start and end time,
  plus created/updated time and user.
- Visible = active, start time reached (or none), end time not reached (or none). This is decided per request,
  so announcements expire without a background job. Status in the management: visible, scheduled, expired, inactive.
- Times are stored in UTC and entered/shown in the browser's time zone.

## Live Updates

- Pages load data once and are refreshed manually (no polling).
- Live container logs: `GET /api/containers/{id}/logs/stream` (`CONTAINER_LOGS_VIEW`), Server-Sent Events.
  - Only new lines are streamed; earlier lines come from the regular logs endpoint.
  - Events: `ready`, `line` (JSON log line), `end` with a reason
    (`container-stopped`, `max-duration`, `access-revoked`, `error`, `server-shutdown`).
  - Limits (`app.log-stream.*`): ends after 30 minutes (resumable), heartbeat every 15 seconds,
    at most 20 concurrent streams (otherwise 429).
  - Each heartbeat re-checks the user (active, session valid, permission), so revoked access ends the stream
    within one heartbeat interval.
  - A closed browser connection is detected on the next send (log line or heartbeat), which frees the slot.
  - The frontend does not reconnect automatically (avoids gaps/duplicates); the user resumes manually.

## Error Handling

- API errors use RFC 9457 `ProblemDetail`, including 401/403 responses from Spring Security.
- Validation errors include an `errors` object (field name → message).
- Technical details are included only for administrators.

## Testing

- Backend tests mock the database (Mockito); no MongoDB or Docker is needed to run them.
- Controller and security behavior is tested with MockMvc against the full application context.

## Maintenance Mode

- Stored as one document in the `settings` collection (enabled, information text, changed at/by) and cached in memory
  (single backend instance); the state survives restarts.
- `GET /api/maintenance` is public (enabled + text) so the maintenance page works without login.
  `GET`/`PUT /api/admin/maintenance` require `MAINTENANCE_MANAGE` (Admin only by default).
- Enforced in the backend while enabled:
  - API requests of non-admins (including anonymous ones) get 503 with `maintenance: true` and the text.
    Exempt: maintenance status, CSRF token, login, logout.
  - Non-admin logins are rejected (503); no session is kept.
  - Open live log streams of non-admins end with reason `maintenance` at the next heartbeat.
  - Administrators (superusers) keep full access. Non-admins with `MAINTENANCE_MANAGE` can turn maintenance on,
    but cannot use the application (or turn it off) while it is active.
- Frontend: non-admins and visitors see the maintenance page (with "Administrator login");
  administrators see a banner. Any 503 maintenance response switches the app to the maintenance page.
