# Roleplayer — GM Toolbox

A set of tools to help a pen & paper game master with mundane tasks: managing
sessions/players/characters (with PDF character sheets), tracking initiative
order in combat, and quickly generating/saving NPCs.

- **Backend**: Java 21 / Spring Boot, hexagonal architecture, SQLite (via JPA)
  for structured data, MinIO (S3-compatible) for character-sheet PDFs.
- **Frontend**: React + TypeScript + Vite + MUI, built and embedded into the
  same Spring Boot jar (single container, no separate frontend service),
  styled as a whimsical "tavern notice board".
- **Deployment**: Docker Swarm on a Raspberry Pi cluster, data persisted on a
  Synology NAS via NFS bind mounts.

## Tabs

1. **Session** — create/select the active session; manage its players and
   characters (global roster, importable across sessions); upload character
   sheets as PDFs and view them embedded in the browser.
2. **Initiative Tracker** — drag-and-drop sortable list of the active
   session's characters; uncheck a character to grey it out (knocked
   out/inactive). Order and checked-state are stored only in a browser
   cookie — they're intentionally volatile and never saved server-side.
3. **NPC Helper** — create NPCs (name, motive, status, mood) by hand or roll
   them randomly (per-field dice buttons or the big "Random NPC" button); save
   NPCs into the active session, or import ones created in another session.

## Prerequisites

- A Raspberry Pi acting as a Docker Swarm manager (see `../taster` and
  `../mealplaner` for sibling projects using the same cluster).
- A Synology NAS reachable from the Pi with an NFS export configured (the
  same share already used by `mealplaner`/`taster`, typically
  `/volume1/cloudstorage`).

## Synology NAS folders

These two folders hold all persistent data and must exist on the NAS (under
the NFS export that gets mounted at `/volume1/cloudstorage` on the Pi):

| Folder | Purpose |
| --- | --- |
| `/volume1/cloudstorage/docker-swarm-data/roleplayer` | SQLite database (`roleplayer.db`) — sessions, players, characters, NPCs |
| `/volume1/cloudstorage/docker-swarm-data/minio/roleplayer` | MinIO object storage — uploaded character-sheet PDFs |

You don't need to create these by hand — `./setup.sh` creates them
automatically (idempotent `mkdir -p` + `chmod 777`, matching the Synology
`root_squash` workaround used by the sibling projects). Create them manually
only if you prefer to prepare the NAS yourself before the first run.

## First-time setup

On the Swarm manager Pi, from the project root:

```bash
chmod +x setup.sh
./setup.sh
```

This is fully idempotent and will:

1. Install `docker.io`/`nfs-common` if missing, and initialize the Swarm if
   it isn't already active.
2. Mount the NAS NFS share (adds an `/etc/fstab` entry if needed).
3. Create the two NAS folders above with the right permissions.
4. Build the `roleplayer:latest` image for `linux/arm64`.
5. Deploy the `roleplayer` stack (`docker stack deploy`).

Override MinIO credentials if desired:

```bash
MINIO_ACCESS_KEY=myuser MINIO_SECRET_KEY=mysecret ./setup.sh
```

Reset data if needed:

```bash
./setup.sh --wipe-db      # deletes the SQLite database
./setup.sh --wipe-minio   # deletes all uploaded PDFs
```

## Ongoing deployments

After every code change, from the project root on the Pi:

```bash
./redeploy.sh
```

This rebuilds the image, re-runs `docker stack deploy` (to pick up any
compose/env changes), and force-updates the running `app` service so it
actually picks up the freshly built image (Swarm otherwise ignores a
same-tagged `:latest` image).

## Where it's running

- App: `http://pi1:3002`
- MinIO console: `http://pi1:9005` (API on `9004`)

## Useful commands

```bash
docker stack ps roleplayer              # service status
docker service logs roleplayer_app      # app logs
docker service logs roleplayer_minio    # MinIO logs
```

## Local development

### Quickest: stub mode, no external services

```bash
./run-local.sh
```

Starts the backend under the `local` Spring profile (in-memory stub
repositories instead of SQLite, in-memory PDF store instead of MinIO — see
`application-local.properties` and `adapter/memory/`) and the Vite frontend
dev server together. No Docker, MinIO, or SQLite file needed — just open
http://localhost:5173. All data is volatile and reset whenever you stop it,
which is exactly the point: a zero-setup sandbox for UI/API iteration.
Press Ctrl+C to stop both.

### Full stack (real SQLite + MinIO)

Backend (serves API on port 3002 by default):

```bash
./gradlew bootRun
```

Requires a running MinIO instance (see `docker-compose.yml`) and writes to a
local `./data/roleplayer.db` SQLite file.

Frontend (Vite dev server with API proxy to the backend):

```bash
cd frontend
npm install
npm run dev
```

Full production build (builds the frontend and embeds it into the jar):

```bash
./gradlew build
```
