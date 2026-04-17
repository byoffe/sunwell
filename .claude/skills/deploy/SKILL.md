---
name: deploy
description: Build the app JAR and deploy it to the target server. Currently supports SSH-based targets (Docker local, remote bare metal). Must be invoked by the user or the loop skill — Claude does not call this automatically.
disable-model-invocation: true
allowed-tools: "Bash Read"
---

## Deploy

Builds the app JAR and deploys it to the named target via the appropriate
transport script. Verifies the deployment succeeded.

**Usage:** `/deploy [target]`

- `target` — optional, defaults to `local-docker`

### Steps

Run the deploy script and report the result:

```!
bash scripts/deploy.sh $ARGUMENTS
```

If the script exits non-zero, report the failure clearly and stop. Do not
attempt to diagnose or fix build or SSH errors automatically — surface them
to the user.

If successful, confirm:
- Which target was deployed to
- JAR size and path on the remote host
- That the loop can proceed to the profile stage when ready

---

## Deployment Types and Customization

The current implementation is a prototype — all config is hardcoded in
`scripts/deploy.sh`. The intended end-state separates three concerns:

### 1. Transport scripts (`scripts/`)
One script per transport mechanism — not per environment:
- `scripts/deploy-ssh.sh` — any SSH/SCP target (Docker, bare metal, VM)
- `scripts/deploy-k8s.sh` — Kubernetes (future)
- `scripts/deploy-ecs.sh` — AWS ECS (future)

Add a new transport script only when a genuinely different mechanism is needed.
`local-docker` and `remote-bare-metal` both use SSH — same script, different config.

### 2. Target configuration (perf-target YAML)
A named target specifies: host, port, SSH key, remote user, remote path.
Targets are defined in the app's `sunwell.yml` (see below). The skill resolves
a target name to its config block and passes values as arguments to the
transport script.

### 3. App configuration (`sunwell.yml`)
Lives with the app being profiled — not in the harness. Example:

```yaml
app: my-service
maven:
  module: my-service
jar: my-service/target/my-service-benchmarks.jar
default-target: local-docker
targets:
  local-docker:
    transport: ssh
    host: localhost
    port: 2222
    user: sunwell
    key: examples/docker/sunwell_dev_key
    remote-path: /home/sunwell
  staging:
    transport: ssh
    host: staging.example.com
    port: 22
    user: deploy
    key: ~/.ssh/staging_key
    remote-path: /opt/app
```

**This YAML schema is not yet finalized** — it is pending the perf-target
configuration spec. Do not implement against it until that spec is approved.
