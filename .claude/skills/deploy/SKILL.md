---
name: deploy
description: Read sunwell.yml, build the app JAR via Maven, and deploy it to the named target via SSH/SCP. Verifies the deployment succeeded.
allowed-tools: "Bash Read"
---

## Deploy

Builds the app JAR and deploys it to the named target.

**Usage:** `/deploy [target]`

- `target` — optional; overrides `default-target` in `sunwell.yml`

---

### Steps

**1. Read `sunwell.yml`**

Read `examples/toy-app/sunwell.yml`. Extract:
- `maven.module` — Maven module to build
- `jar` — local path to the built JAR
- `default-target` — fallback if no target arg given
- The named target block: `host`, `port`, `user`, `key`, `remote-path`

Target name: use `$ARGUMENTS` if provided, otherwise `default-target`.

If the named target does not exist in `sunwell.yml`, stop and list available
target names. Do not guess.

**2. Build the JAR**

```!
mvn package -pl {maven.module} --also-make -q
```

If Maven exits non-zero, report the failure and stop.

**3. Deploy via script**

```!
bash .claude/skills/deploy/deploy-ssh.sh \
  {host} {port} {user} {key} {jar} {remote-path}
```

If the script exits non-zero, report the failure clearly and stop. Do not
attempt to diagnose SSH errors automatically — surface them to the user.

**4. Report**

Confirm:
- Which target was deployed to
- JAR filename and size on the remote host
- Ready for profile stage

---

## Deployment Architecture

Scripts are co-located with their skill and are transport-specific, not
environment-specific. `deploy-ssh.sh` handles any SSH/SCP target — Docker,
bare metal, VM. What differs between environments is config, not code.

Future transports (k8s, ECS) get their own scripts: `deploy-k8s.sh`, etc.
Add a new script only when a genuinely different transport mechanism is needed.

### sunwell.yml target block shape

```yaml
targets:
  <name>:
    transport: ssh
    host: <hostname>
    port: <port>
    user: <ssh user>
    key: <path to private key>
    remote-path: <directory on remote host>
```
