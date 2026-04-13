# Sunwell Docker Dev Server

Runs a container that mimics the remote profiling target — same SSH-based
workflow, same scripts. The only difference from a real server is the hostname
(`localhost`) and port (`2222`).

## How docker-compose.yml works

`docker-compose.yml` declares one service: `sunwell-server`. Compose builds the
image from `Dockerfile`, starts the container, and maps a port:

```
localhost:2222  →  container:22 (sshd)
```

Port 2222 is used on the host so it doesn't conflict with any local SSH service.
When moving to a real remote server, only the hostname and port in the deploy
config change — nothing else.

## SSH access

Authentication uses a local dev keypair. The keypair is gitignored — each
developer generates their own. The public key is baked into the image at build
time; the private key is used by scripts and for manual access.

| Field | Value                           |
|-------|---------------------------------|
| User  | sunwell                         |
| Host  | localhost                       |
| Port  | 2222                            |
| Key   | examples/docker/sunwell_dev_key |

**These are throwaway dev credentials. This container holds no real data.**

## First-time setup

Generate the local dev keypair (one-time, not committed to git):
```bash
ssh-keygen -t ed25519 -f examples/docker/sunwell_dev_key -N "" -C "sunwell-dev"
```

Then build and start the container as normal.

## Commands

**Build and start (first time or after Dockerfile changes):**
```bash
docker compose -f examples/docker/docker-compose.yml up -d --build
```

**Start (image already built):**
```bash
docker compose -f examples/docker/docker-compose.yml up -d
```

**Connect via SSH:**
```bash
ssh -i examples/docker/sunwell_dev_key -p 2222 sunwell@localhost
```

If the host key changes after a container rebuild, clear the stale entry:
```bash
ssh-keygen -R "[localhost]:2222"
```

**Stop and remove the container:**
```bash
docker compose -f examples/docker/docker-compose.yml down
```

**View logs:**
```bash
docker compose -f examples/docker/docker-compose.yml logs -f
```

## Copying files into the container

Deploy scripts will scp the benchmark JAR here:
```bash
scp -i examples/docker/sunwell_dev_key -P 2222 \
    examples/toy-app/target/toy-app-benchmarks.jar \
    sunwell@localhost:~
```
