---
name: profile
description: Read sunwell.yml, resolve focus to profiler flags, SSH into the target, run the JMH benchmark with the appropriate profiler attached, collect the recording, and write an experiments.json entry.
allowed-tools: "Bash Read Write"
---

## Profile

Profiles the app on the named target using the focus-derived profiler config.
Collect is folded into this skill — the recording lands locally before this
skill exits.

**Usage:** `/profile [target] [--focus <focus>]`

- `target` — optional; overrides `default-target` in `sunwell.yml`
- `--focus` — optional; overrides `default-focus` in `sunwell.yml`

---

### Steps

**1. Read `sunwell.yml`**

Read `examples/toy-app/sunwell.yml`. Extract:
- `jar` — path to the JAR filename (basename only needed for remote)
- `default-target`, `default-focus`
- Named target block: `host`, `port`, `user`, `key`, `remote-path`

Resolve target name: `$ARGUMENTS` target arg → `default-target`.
Resolve focus: `$ARGUMENTS` `--focus` → `default-focus` → `baseline`.

If the target does not exist in `sunwell.yml`, stop and list available targets.
If the focus is not a recognized value, stop and list valid focus values.

**2. Resolve focus to profiler config**

Use the built-in defaults table. Apply any `profile.overrides.<focus>` from
`sunwell.yml` on top.

| Focus | Profiler | Duration | JVM flags |
|---|---|---|---|
| `baseline` | JFR | 60s | `-XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=/tmp/<run-id>.jfr,settings=profile` |
| `gc` | JFR | 120s | `-XX:+FlightRecorder -XX:StartFlightRecording=duration=120s,filename=/tmp/<run-id>.jfr,settings=profile` |
| `cpu` | async-profiler | 30s | *(async-profiler not yet available — see note below)* |
| `memory` | async-profiler | 30s | *(async-profiler not yet available — see note below)* |
| `lock` | async-profiler | 30s | *(async-profiler not yet available — see note below)* |

**async-profiler focuses:** if `cpu`, `memory`, or `lock` is requested, stop
and report: "async-profiler is not yet configured for this target. Use
`--focus baseline` or `--focus gc` to proceed with JFR." Do not silently
fall back.

**3. Generate run-id**

```!
date -u +%Y%m%d-%H%M%S
```

Use the output as `<run-id>` (e.g., `20260416-143012`).

**4. Run the profile script**

```!
bash .claude/skills/profile/profile-jfr.sh \
  {host} {port} {user} {key} {remote-path} {jar-filename} {duration} {run-id}
```

If the script exits non-zero, report the error and stop. Do not retry.

**5. Collect the recording**

```!
bash .claude/skills/profile/collect-ssh.sh \
  {host} {port} {user} {key} /tmp/{run-id}.jfr results/{run-id}
```

If collect fails (recording not found on remote), report the SSH path checked
and stop.

**6. Write experiments.json entry**

Read `results/experiments.json` if it exists; create it if not.

Append this entry:

```json
{
  "run-id": "<run-id>",
  "timestamp": "<ISO-8601 UTC>",
  "target": "<target-name>",
  "focus": "<focus>",
  "profiler": "<jfr|async-profiler>",
  "artifact-path": "results/<run-id>/recording.jfr",
  "analysis-path": null,
  "hypothesis": null,
  "suggested-next-focus": null,
  "files-changed": [],
  "delta": null
}
```

Write the updated JSON back to `results/experiments.json`.

**7. Report**

Confirm:
- Run-id, target, focus, profiler used
- Recording path and size
- Ready for analyze stage
